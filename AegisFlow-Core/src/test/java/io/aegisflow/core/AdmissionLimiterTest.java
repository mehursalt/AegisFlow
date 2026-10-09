package io.aegisflow.core;
import io.aegisflow.core.monitor.AdmissionLimiter;
import io.aegisflow.core.monitor.GatewayMetrics;
import org.junit.Test;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class AdmissionLimiterTest {
    @Test public void limitAndDoubleReleaseAreSafe() {
        AdmissionLimiter limiter=new AdmissionLimiter();
        var first=limiter.tryAcquire("test-limited",1);
        assertNotNull(first); assertNull(limiter.tryAcquire("test-limited",1));
        first.close(); first.close(); assertEquals(0,limiter.active("test-limited"));
        var next=limiter.tryAcquire("test-limited",1); assertNotNull(next); next.close();
    }
    @Test public void servicesHaveSeparatePermits() {
        AdmissionLimiter limiter=new AdmissionLimiter();
        var slow=limiter.tryAcquire("test-report",1);
        var fast=limiter.tryAcquire("test-catalog",1);
        assertNotNull(slow); assertNotNull(fast); slow.close(); fast.close();
    }
    @Test public void completionAndLateRegistrationReleasePermit() {
        try(GatewayMetrics metrics=new GatewayMetrics()) {
            AdmissionLimiter limiter=new AdmissionLimiter();
            var permit=limiter.tryAcquire("test-completion",1);
            var observation=metrics.begin("id","GET");
            observation.onComplete(permit::close); observation.complete(503,true); observation.complete(200,true);
            assertEquals(0,limiter.active("test-completion"));
            var late=limiter.tryAcquire("test-completion",1);
            observation.onComplete(late::close); assertEquals(0,limiter.active("test-completion"));
        }
    }
    @Test public void concurrentAcquisitionDoesNotExceedLimit() throws Exception {
        AdmissionLimiter limiter=new AdmissionLimiter();
        ExecutorService pool=Executors.newFixedThreadPool(12);
        CountDownLatch start=new CountDownLatch(1), attempted=new CountDownLatch(12), release=new CountDownLatch(1);
        AtomicInteger accepted=new AtomicInteger();
        try {
            for(int i=0;i<12;i++) { pool.submit(() -> {
                try { start.await(); var permit=limiter.tryAcquire("test-parallel",3);
                    if(permit!=null) { accepted.incrementAndGet(); }
                    attempted.countDown();
                    if(permit!=null) { release.await(); permit.close(); }
                } catch(InterruptedException e) { Thread.currentThread().interrupt(); }
            }); }
            start.countDown(); assertTrue(attempted.await(3,TimeUnit.SECONDS));
            assertEquals(3,accepted.get()); assertEquals(3,limiter.active("test-parallel"));
        } finally { release.countDown(); pool.shutdown(); assertTrue(pool.awaitTermination(3,TimeUnit.SECONDS)); }
        assertEquals(0,limiter.active("test-parallel"));
    }
}
