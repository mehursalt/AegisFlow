package io.aegisflow.core.monitor;

import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Counts aggregated inbound HTTP requests through response write completion. */
public final class GatewayMetrics implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger("accessLog");
    private static final GatewayMetrics INSTANCE = new GatewayMetrics();
    private static final Set<String> METHODS = Set.of("GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS", "TRACE", "CONNECT");
    private final PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
    private final AtomicInteger inFlight = new AtomicInteger();
    private final JvmGcMetrics gcMetrics = new JvmGcMetrics();
    private final java.util.concurrent.ThreadPoolExecutor accessLogs = new java.util.concurrent.ThreadPoolExecutor(
            1, 1, 0, TimeUnit.MILLISECONDS, new java.util.concurrent.ArrayBlockingQueue<>(1024),
            task -> { Thread thread = new Thread(task, "gateway-access-log"); thread.setDaemon(true); return thread; },
            (task, pool) -> registry.counter("gateway.log.dropped").increment());
    private HttpServer server;
    private ExecutorService executor;
    private volatile java.util.function.Consumer<io.aegisflow.access.AccessEvent> eventSink;
    private String gatewayId = "unknown";
    public void setEventSink(String gateway, java.util.function.Consumer<io.aegisflow.access.AccessEvent> sink) { gatewayId=gateway; eventSink=sink; }
    public void bindPublisher(io.aegisflow.access.AccessPublisher publisher) {
        Gauge.builder("gateway.access.queue",publisher,io.aegisflow.access.AccessPublisher::queueSize).register(registry);
        Gauge.builder("gateway.access.pending",publisher,io.aegisflow.access.AccessPublisher::pending).register(registry);
        for (var entry:java.util.Map.of("offered",publisher.offered,"published",publisher.published,"dropped",publisher.dropped,"failures",publisher.failures).entrySet()) {
            io.micrometer.core.instrument.FunctionCounter.builder("gateway.access."+entry.getKey(),entry.getValue(),java.util.concurrent.atomic.AtomicLong::doubleValue).register(registry);
        }
    }

    public GatewayMetrics() {
        Gauge.builder("gateway.inflight", inFlight, AtomicInteger::get).register(registry);
        Gauge.builder("gateway.log.queue", accessLogs.getQueue(), java.util.Queue::size).register(registry);
        new JvmMemoryMetrics().bindTo(registry);
        new JvmThreadMetrics().bindTo(registry);
        gcMetrics.bindTo(registry);
        new ProcessorMetrics().bindTo(registry);
    }

    public static GatewayMetrics getInstance() { return INSTANCE; }
    public PrometheusMeterRegistry registry() { return registry; }

    public synchronized void start(String host, int port) throws IOException {
        if (server != null) { return; }
        HttpServer created = HttpServer.create(new InetSocketAddress(host, port), 0);
        ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "gateway-metrics");
            thread.setDaemon(true);
            return thread;
        });
        created.setExecutor(worker);
        created.createContext("/prometheus", exchange -> {
            byte[] bytes = registry.scrape().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain; version=0.0.4; charset=utf-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (var output = exchange.getResponseBody()) { output.write(bytes); }
        });
        created.start();
        server = created;
        executor = worker;
    }

    public Observation begin(String id, String method) {
        inFlight.incrementAndGet();
        registry.counter("gateway.accepted").increment();
        return new Observation(id, METHODS.contains(method) ? method : "OTHER");
    }

    @Override
    public synchronized void close() {
        if (server != null) { server.stop(0); server = null; }
        if (executor != null) { executor.shutdownNow(); executor = null; }
        accessLogs.shutdown();
        try { if (!accessLogs.awaitTermination(1, TimeUnit.SECONDS)) { accessLogs.shutdownNow(); } }
        catch (InterruptedException error) { accessLogs.shutdownNow(); Thread.currentThread().interrupt(); }
        gcMetrics.close();
        registry.close();
    }

    public final class Observation {
        private final String id;
        private final String method;
        private final long started = System.nanoTime();
        private final AtomicBoolean finished = new AtomicBoolean();
        private volatile String route = "unmatched";
        private volatile String reason = "http";
        private volatile String service = "unknown", instance = "unknown";
        public void service(String value) { service=value==null?"unknown":value; }
        public void instance(String value) { instance=value==null?"unknown":value; }
        private ChannelFuture closeFuture;
        private ChannelFutureListener closeListener;
        private volatile Runnable completionCallback;

        public synchronized void onComplete(Runnable callback) {
            if (finished.get()) { callback.run(); return; }
            Runnable previous = completionCallback;
            completionCallback = previous == null ? callback : () -> { previous.run(); callback.run(); };
            if (finished.get()) { runCompletionCallbacks(); }
        }

        private synchronized void runCompletionCallbacks() {
            Runnable callback = completionCallback;
            completionCallback = null;
            if (callback != null) { callback.run(); }
        }

        private Observation(String id, String method) { this.id = id; this.method = method; }
        public String requestId() { return id; }
        public long elapsedNanos() { return System.nanoTime() - started; }
        public void route(String ruleId) { route = ruleId == null ? "unmatched" : ruleId; }
        public void reason(String value) { reason = value; }

        public void watchDisconnect(ChannelFuture future) {
            closeFuture = future;
            closeListener = ignored -> complete(0, false);
            future.addListener(closeListener);
            if (finished.get()) { future.removeListener(closeListener); }
        }

        public void complete(int status, boolean written) {
            if (!finished.compareAndSet(false, true)) { return; }
            try {
            if (closeFuture != null && closeListener != null) { closeFuture.removeListener(closeListener); }
            long elapsed = System.nanoTime() - started;
            String outcome = !written ? "write_error" : status >= 500 ? "server_error" : status >= 400 ? "client_error" : "success";
            Timer.builder("gateway.request").tags("route", route, "method", method,
                    "status", Integer.toString(status), "outcome", outcome, "reason", reason)
                    .publishPercentileHistogram()
                    .minimumExpectedValue(Duration.ofMillis(1)).maximumExpectedValue(Duration.ofSeconds(30))
                    .serviceLevelObjectives(Duration.ofMillis(100), Duration.ofMillis(500), Duration.ofSeconds(1))
                    .register(registry).record(elapsed, TimeUnit.NANOSECONDS);
            String completedRoute = route, completedReason = reason;
            var sink=eventSink;
            if(sink!=null) {
                try { sink.accept(new io.aegisflow.access.AccessEvent(1,java.util.UUID.randomUUID().toString(),id,gatewayId,
                        route,service,instance,method,status,outcome,reason,TimeUnit.NANOSECONDS.toMillis(elapsed),System.currentTimeMillis())); }
                catch(RuntimeException invalid) { registry.counter("gateway.access.invalid").increment(); }
            }
            accessLogs.execute(() -> LOG.info("gateway_complete requestId={} route={} method={} status={} outcome={} reason={} durationMs={}",
                    id, completedRoute, method, status, outcome, completedReason, TimeUnit.NANOSECONDS.toMillis(elapsed)));
            } finally {
                inFlight.decrementAndGet();
                if(completionCallback!=null){runCompletionCallbacks();}
            }
        }
    }
}
