package org.java.limit;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class RateLimiter implements Limiter {

    private static final int MAX_TRY_ACQUIRE = 512;
    private static final long MAX_QUEUE_NS = TimeUnit.MILLISECONDS.toNanos(500);

    private final AtomicLong nextTokenNs;
    private final long intervalNs;

    public RateLimiter(int permitsPreSecond) {
        this.intervalNs = TimeUnit.SECONDS.toNanos(1) / permitsPreSecond;
        this.nextTokenNs = new AtomicLong(0L);
    }


    @Override
    public boolean tryAcquire() {
        long now = System.nanoTime();
        for (int count = 0; count < MAX_TRY_ACQUIRE; count++) {
            long pre = nextTokenNs.get();
            if (now + MAX_QUEUE_NS < pre) {
                return false;
            }
            if (this.nextTokenNs.compareAndSet(pre, Math.max(now, pre) + intervalNs)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void release(int permits) {

    }
}
