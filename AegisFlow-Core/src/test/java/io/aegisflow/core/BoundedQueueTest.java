package io.aegisflow.core;
import io.aegisflow.core.disruptor.*;
import org.junit.Test;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

public class BoundedQueueTest {
    @Test public void realRingBufferReportsFullWithoutWaiting() throws Exception {
        CountDownLatch entered=new CountDownLatch(1), release=new CountDownLatch(1);
        var queue=new ParallelQueueHandler.Builder<Integer>().setBufferSize(2).setThreads(1)
                .setListener(new EventListener<Integer>() {
                    public void onEvent(Integer event) { if(event==1) { entered.countDown(); try { release.await(); } catch(InterruptedException e){Thread.currentThread().interrupt();} } }
                    public void onException(Throwable error,long sequence,Integer event) { throw new AssertionError(error); }
                }).build();
        queue.start();
        try {
            assertTrue(queue.tryAdd(1)); assertTrue(entered.await(3,TimeUnit.SECONDS));
            assertTrue(queue.tryAdd(2));
            long start=System.nanoTime(); assertFalse(queue.tryAdd(3));
            assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start)<100);
        } finally { release.countDown(); queue.shutDown(); }
    }
}
