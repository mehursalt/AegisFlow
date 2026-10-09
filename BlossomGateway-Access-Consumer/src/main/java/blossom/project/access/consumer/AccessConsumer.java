package blossom.project.access.consumer;

import blossom.project.access.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

/** Callback only buffers; worker owns channel writes and commits before ack. */
public final class AccessConsumer implements AutoCloseable {
    public final AtomicLong consumed=new AtomicLong(),duplicates=new AtomicLong(),retries=new AtomicLong(),failed=new AtomicLong(),errors=new AtomicLong();
    public final AtomicInteger committedUnacked=new AtomicInteger();
    private final ConnectionFactory factory;
    private final AccessStore store;
    private final ObjectMapper mapper=new ObjectMapper();
    private final AtomicBoolean running=new AtomicBoolean();
    private final AtomicBoolean returned=new AtomicBoolean();
    private final Thread worker=new Thread(this::run,"access-consumer");
    private volatile Connection connection;
    private Channel channel;
    private ArrayBlockingQueue<GetResponse> inbox;
    public AccessConsumer(ConnectionFactory factory,AccessStore store) { this.factory=factory;this.store=store;worker.setDaemon(true); }
    public void start() { if(running.compareAndSet(false,true)) { worker.start(); } }
    private void connect() throws Exception {
        if(connection!=null && connection.isOpen() && channel!=null && channel.isOpen()) { return; }
        disconnect(); connection=factory.newConnection("blossom-access-consumer"); channel=connection.createChannel();
        RabbitTopology.declare(channel); channel.addReturnListener((code,text,exchange,key,props,body)->returned.set(true));
        // Per-connection buffer: stale delivery tags must never cross reconnects.
        ArrayBlockingQueue<GetResponse> buffer=new ArrayBlockingQueue<>(64);
        inbox=buffer;
        Connection owner=connection;
        channel.basicQos(64);
        channel.basicConsume(RabbitTopology.QUEUE,false,(tag,delivery)->{
            if(!buffer.offer(new GetResponse(delivery.getEnvelope(),delivery.getProperties(),delivery.getBody(),0))) {
                // Defensive overflow: closing returns unacked messages to RabbitMQ.
                owner.abort();
            }
        },tag->owner.abort());
    }
    private void forward(GetResponse delivery,String key,int retry) throws Exception {
        Map<String,Object> headers=new HashMap<>(); headers.put("x-retry-count",retry);
        var properties=new AMQP.BasicProperties.Builder().deliveryMode(2).contentType("application/json")
                .messageId(delivery.getProps().getMessageId()).headers(headers).build();
        returned.set(false); channel.basicPublish(RabbitTopology.EXCHANGE,key,true,properties,delivery.getBody());
        channel.waitForConfirmsOrDie(1500);
        if(returned.get()) { throw new java.io.IOException("Unroutable retry/dead event"); }
        channel.basicAck(delivery.getEnvelope().getDeliveryTag(),false);
    }
    private void run() {
        while(running.get()) {
            try {
                connect(); List<AccessEvent> events=new ArrayList<>(); List<GetResponse> deliveries=new ArrayList<>();
                List<GetResponse> batch=new ArrayList<>();
                GetResponse first=inbox.poll(100,TimeUnit.MILLISECONDS);
                if(first==null) { continue; }
                batch.add(first); inbox.drainTo(batch,31);
                for(GetResponse delivery:batch) {
                    AccessEvent event;
                    try { if(delivery.getBody().length>8192){throw new IllegalArgumentException("Oversized event");} event=mapper.readValue(delivery.getBody(),AccessEvent.class); if(event==null){throw new IllegalArgumentException("Empty event");} }
                    catch(Exception invalid) { forward(delivery,"failed",0); failed.incrementAndGet(); continue; }
                    events.add(event); deliveries.add(delivery);
                }
                if(events.isEmpty()) { continue; }
                try {
                    int inserted=store.persist(events);
                    committedUnacked.set(events.size());
                    try {
                        int ackDelay=Math.max(0,Math.min(5000,Integer.getInteger("access.ackDelayMs",0)));
                        if(ackDelay>0) { Thread.sleep(ackDelay); } // Explicit fault-injection switch; default is zero.
                        for(GetResponse delivery:deliveries) { channel.basicAck(delivery.getEnvelope().getDeliveryTag(),false); }
                        consumed.addAndGet(events.size()); duplicates.addAndGet(events.size()-inserted);
                    } finally { committedUnacked.set(0); }
                } catch(java.sql.SQLException databaseFailure) {
                    errors.incrementAndGet();
                    for(GetResponse delivery:deliveries) {
                        Object prior=delivery.getProps().getHeaders()==null?null:delivery.getProps().getHeaders().get("x-retry-count");
                        int next=(prior instanceof Number?((Number)prior).intValue():0)+1;
                        forward(delivery,next<=3?"retry":"failed",next);
                        if(next<=3) { retries.incrementAndGet(); } else { failed.incrementAndGet(); }
                    }
                }
            } catch(Exception failure) {
                errors.incrementAndGet(); disconnect();
                if(running.get()) { try { Thread.sleep(500); } catch(InterruptedException ignored) { } }
            }
        }
        disconnect();
    }
    private void disconnect() { Connection current=connection; connection=null; if(current!=null) { current.abort(); } }
    @Override public void close() {
        running.set(false); worker.interrupt(); disconnect();
        try { worker.join(2500); } catch(InterruptedException error) { Thread.currentThread().interrupt(); }
    }
}
