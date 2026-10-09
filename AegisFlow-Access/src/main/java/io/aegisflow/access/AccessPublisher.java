package io.aegisflow.access;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Non-blocking offer; one background owner publishes batches and waits for confirms. */
public final class AccessPublisher implements AutoCloseable {
    public interface Transport extends AutoCloseable { void publish(List<AccessEvent> events) throws Exception; }
    public final AtomicLong offered=new AtomicLong(), published=new AtomicLong(), dropped=new AtomicLong(), failures=new AtomicLong();
    private final ArrayBlockingQueue<AccessEvent> queue;
    private final Transport transport;
    private final AtomicBoolean running=new AtomicBoolean();
    private final AtomicBoolean accepting=new AtomicBoolean();
    private final AtomicInteger pending=new AtomicInteger();
    private final Thread worker;

    public AccessPublisher(int capacity,Transport transport) {
        queue=new ArrayBlockingQueue<>(capacity); this.transport=transport;
        worker=new Thread(this::run,"gateway-access-publisher"); worker.setDaemon(true);
    }
    public void start() { if(running.compareAndSet(false,true)) { accepting.set(true);worker.start(); } }
    public boolean offer(AccessEvent event) {
        offered.incrementAndGet();
        if(!accepting.get() || !queue.offer(event)) { dropped.incrementAndGet(); return false; }
        if(!accepting.get() && queue.remove(event)) { dropped.incrementAndGet(); return false; }
        return true;
    }
    public int queueSize() { return queue.size(); }
    public int pending() { return pending.get(); }
    private void run() {
        try {
            while(running.get()) {
                if(!accepting.get() && queue.isEmpty())break;
                AccessEvent first=queue.poll(200,TimeUnit.MILLISECONDS);
                if(first==null) { continue; }
                List<AccessEvent> batch=new ArrayList<>(); batch.add(first); queue.drainTo(batch,31);
                pending.set(batch.size()); boolean confirmed=false;
                for(int attempt=0;attempt<3 && running.get();attempt++) {
                    try { transport.publish(batch); published.addAndGet(batch.size()); confirmed=true; break; }
                    catch(Exception failure) { failures.incrementAndGet(); if(running.get()) { try { Thread.sleep(300); } catch(InterruptedException ignored) { } } }
                }
                if(!confirmed) { dropped.addAndGet(batch.size()); }
                pending.set(0);
            }
        } catch(InterruptedException ignored) { }
        finally { dropped.addAndGet(queue.size()); queue.clear(); try { transport.close(); } catch(Exception ignored) { } }
    }
    @Override public void close() {
        accepting.set(false);running.set(false); worker.interrupt();
        try { transport.close(); } catch(Exception ignored) { }
        try { worker.join(2500); } catch(InterruptedException error) { Thread.currentThread().interrupt(); }
    }
    public void drainAndClose(long timeoutMs) {
        accepting.set(false);
        try {worker.join(Math.max(1,timeoutMs));}catch(InterruptedException error){Thread.currentThread().interrupt();}
        close();
    }

    public static final class RabbitTransport implements Transport {
        private final ConnectionFactory factory;
        private final ObjectMapper mapper=new ObjectMapper();
        private volatile Connection connection;
        private Channel channel;
        private final AtomicBoolean returned=new AtomicBoolean();
        public RabbitTransport(String host,int port,String user,String password) { factory=RabbitTopology.factory(host,port,user,password); }
        private void connect() throws Exception {
            if(connection!=null && connection.isOpen() && channel!=null && channel.isOpen()) { return; }
            close(); connection=factory.newConnection("aegisflow-access-publisher"); channel=connection.createChannel();
            RabbitTopology.declare(channel);
            channel.addReturnListener((code,text,exchange,key,properties,body) -> returned.set(true));
        }
        @Override public void publish(List<AccessEvent> events) throws Exception {
            try {
                connect(); returned.set(false);
                for(AccessEvent event:events) {
                    var properties=new AMQP.BasicProperties.Builder().contentType("application/json").deliveryMode(2).messageId(event.eventId()).build();
                    channel.basicPublish(RabbitTopology.EXCHANGE,"event",true,properties,mapper.writeValueAsBytes(event));
                }
                channel.waitForConfirmsOrDie(1000);
                if(returned.get()) { throw new java.io.IOException("Unroutable access batch"); }
            } catch(Exception error) { close(); throw error; }
        }
        @Override public void close() { Connection current=connection; connection=null; if(current!=null) { current.abort(); } }
    }
}
