package org.java.limit;

import io.netty.channel.DefaultEventLoop;
import io.netty.channel.EventLoop;
import io.netty.util.concurrent.ScheduledFuture;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Deprecated
public class BucketLimiter implements Limiter {


    private static final EventLoop REFILL_EVENT_LOOP = new DefaultEventLoop(r -> {
        Thread thread = new Thread(r, "refill_event_loop");
        thread.setDaemon(true);
        return thread;
    });

    private final AtomicInteger tokens;

    private final ScheduledFuture<?> refillSchedule;

    public BucketLimiter(int permitsPreSecond) {
        this.tokens = new AtomicInteger(permitsPreSecond);
        refillSchedule = REFILL_EVENT_LOOP.scheduleAtFixedRate(() -> tokens.set(permitsPreSecond), 1, 1,
            TimeUnit.SECONDS);
        
    }

    public void destroy() {
        refillSchedule.cancel(false);
    }

    @Override
    public boolean tryAcquire() {
        while (true) {
            int currentTokens = tokens.get();
            if (currentTokens <= 0) {
                return false;
            }
            if (tokens.compareAndSet(currentTokens, currentTokens - 1)) {
                return true;
            }
        }
    }

    @Override
    public void release(int permits) {

    }
}
