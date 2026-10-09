package blossom.project.core.netty;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Admission and drain share one lock, so zero cannot race with a new admission. */
public final class RequestDrain {
    private boolean draining;
    private int active;
    public synchronized AutoCloseable tryAcquire() {
        if(draining) return null;
        active++;
        AtomicBoolean released=new AtomicBoolean();
        return ()->{if(released.compareAndSet(false,true)) release();};
    }
    private synchronized void release(){active--;notifyAll();}
    public synchronized void begin(){draining=true;notifyAll();}
    public synchronized int active(){return active;}
    public synchronized boolean isDraining(){return draining;}
    public synchronized boolean await(long milliseconds) throws InterruptedException {
        long deadline=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(Math.max(0,milliseconds));
        while(active>0){long left=deadline-System.nanoTime();if(left<=0)return false;TimeUnit.NANOSECONDS.timedWait(this,left);}
        return true;
    }
}
