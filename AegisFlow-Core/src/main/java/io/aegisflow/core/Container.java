package io.aegisflow.core;

import io.aegisflow.core.netty.NettyHttpClient;
import io.aegisflow.core.netty.NettyHttpServer;
import io.aegisflow.core.netty.processor.DisruptorNettyCoreProcessor;
import io.aegisflow.core.netty.processor.NettyCoreProcessor;
import io.aegisflow.core.netty.processor.NettyProcessor;
import lombok.extern.slf4j.Slf4j;

import static io.aegisflow.common.constant.GatewayConst.BUFFER_TYPE_PARALLEL;

/**
 */
@Slf4j
public class Container implements LifeCycle {
    private final Config config;

    private NettyHttpServer nettyHttpServer;

    private NettyHttpClient nettyHttpClient;

    private NettyProcessor nettyProcessor;
    private io.aegisflow.access.AccessPublisher accessPublisher;
    private final java.util.concurrent.atomic.AtomicBoolean stopping=new java.util.concurrent.atomic.AtomicBoolean();

    public Container(Config config) {
        this.config = config;
        init();
    }

    @Override
    public void init() {

        NettyCoreProcessor nettyCoreProcessor = new NettyCoreProcessor();
        //如果启动要使用多生产者多消费组 那么我们读取配置
        if (BUFFER_TYPE_PARALLEL.equals(config.getParallelBufferType())) {
            //开启配置的情况下使用Disruptor
            this.nettyProcessor = new DisruptorNettyCoreProcessor(config, nettyCoreProcessor);
        } else {
            this.nettyProcessor = nettyCoreProcessor;
        }

        this.nettyHttpServer = new NettyHttpServer(config, nettyProcessor);

        this.nettyHttpClient = new NettyHttpClient(config,
                nettyHttpServer.getEventLoopGroupWoker());
    }

    @Override
    public void start() {
        if(config.isAccessEventsEnabled() && (config.getRabbitUser().isBlank() || config.getRabbitPassword().isBlank())) {
            throw new IllegalStateException("Set AEGIS_RABBIT_USER and AEGIS_RABBIT_PASSWORD before enabling access events");
        }
        try {
            io.aegisflow.core.monitor.GatewayMetrics.getInstance().start(config.getPrometheusHost(), config.getPrometheusPort());
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Cannot bind metrics endpoint", e);
        }
        nettyProcessor.start();
        io.micrometer.core.instrument.Gauge.builder("gateway.draining",nettyHttpServer.drain(),drain->drain.isDraining()?1:0)
            .register(io.aegisflow.core.monitor.GatewayMetrics.getInstance().registry());
        if (config.isAccessEventsEnabled()) {
            accessPublisher = new io.aegisflow.access.AccessPublisher(config.getAccessEventBufferSize(),
                    new io.aegisflow.access.AccessPublisher.RabbitTransport(config.getRabbitHost(),config.getRabbitPort(),config.getRabbitUser(),config.getRabbitPassword()));
            accessPublisher.start();
            var metrics = io.aegisflow.core.monitor.GatewayMetrics.getInstance();
            metrics.setEventSink(config.getApplicationName()+":"+config.getPort(), accessPublisher::offer);
            metrics.bindPublisher(accessPublisher);
        }
        nettyHttpServer.start();
        nettyHttpClient.start();
        log.info("api gateway started!");
    }

    @Override
    public void shutdown() {
        shutdown(()->{});
    }
    public void shutdown(Runnable deregister) {
        if(!stopping.compareAndSet(false,true))return;
        cleanup("stop-admission",nettyHttpServer::stopAccepting);
        boolean drained=false;
        try {drained=nettyHttpServer.drain().await(Math.max(0,Math.min(60000,config.getShutdownGracePeriodMs())));}
        catch(InterruptedException interrupted){Thread.currentThread().interrupt();}
        log.info("gateway_shutdown_drain drained={} remaining={}",drained,nettyHttpServer.drain().active());
        cleanup("deregister",deregister);
        cleanup("close-connections",nettyHttpServer::closeConnections);
        cleanup("processor",nettyProcessor::shutDown);
        cleanup("http-client",nettyHttpClient::shutdown);
        cleanup("http-server",nettyHttpServer::shutdown);
        if(accessPublisher!=null)cleanup("events",()->accessPublisher.drainAndClose(Math.max(0,Math.min(10000,config.getShutdownEventFlushMs()))));
        cleanup("metrics",()->io.aegisflow.core.monitor.GatewayMetrics.getInstance().close());
        log.info("gateway_shutdown_complete");
    }
    private void cleanup(String phase,Runnable action){
        try{action.run();}catch(Exception failure){log.warn("gateway_shutdown_failure phase={}",phase,failure);}
    }
}
