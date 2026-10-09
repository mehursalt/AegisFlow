package blossom.project.core.monitor;

import io.micrometer.core.instrument.Gauge;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Local, non-blocking admission; shares counters across versions/routes of a service. */
public final class AdmissionLimiter {
    private static final AdmissionLimiter INSTANCE = new AdmissionLimiter();
    public static AdmissionLimiter getInstance() { return INSTANCE; }
    private final ConcurrentHashMap<String, AtomicInteger> active = new ConcurrentHashMap<>();

    public Permit tryAcquire(String service, int limit) {
        if (limit <= 0) { throw new IllegalArgumentException("Positive concurrency limit required"); }
        AtomicInteger count = active.computeIfAbsent(service, key -> {
            AtomicInteger created = new AtomicInteger();
            Gauge.builder("gateway.service.inflight", created, AtomicInteger::get)
                    .tag("service", key).register(GatewayMetrics.getInstance().registry());
            return created;
        });
        while (true) {
            int current = count.get();
            if (current >= limit) {
                GatewayMetrics.getInstance().registry().counter("gateway.rejected", "scope", "service", "service", service).increment();
                return null;
            }
            if (count.compareAndSet(current, current + 1)) { return new Permit(count); }
        }
    }
    public int active(String service) { AtomicInteger value = active.get(service); return value == null ? 0 : value.get(); }

    public static final class Permit implements AutoCloseable {
        private final AtomicInteger count;
        private final AtomicBoolean released = new AtomicBoolean();
        private Permit(AtomicInteger count) { this.count = count; }
        @Override public void close() { if (released.compareAndSet(false, true)) { count.decrementAndGet(); } }
    }
}
