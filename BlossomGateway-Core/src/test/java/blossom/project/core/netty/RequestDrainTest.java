package blossom.project.core.netty;
import org.junit.Test;
import static org.junit.Assert.*;
public class RequestDrainTest {
    @Test public void stopsAdmissionAndReleaseIsIdempotent() throws Exception {
        RequestDrain drain=new RequestDrain();AutoCloseable permit=drain.tryAcquire();
        drain.begin();assertNull(drain.tryAcquire());assertFalse(drain.await(1));
        permit.close();permit.close();assertEquals(0,drain.active());assertTrue(drain.await(1));
    }
    @Test public void waitsForCompletion() throws Exception {
        RequestDrain drain=new RequestDrain();AutoCloseable permit=drain.tryAcquire();drain.begin();
        Thread worker=new Thread(()->{try{Thread.sleep(30);permit.close();}catch(Exception e){throw new RuntimeException(e);}});
        worker.start();assertTrue(drain.await(1000));worker.join();
    }
}
