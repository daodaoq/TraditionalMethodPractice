package org.java.limit;

import java.util.concurrent.Semaphore;

public class ConcurrencyLimiter implements Limiter {
    private final Semaphore semaphore;

    public ConcurrencyLimiter(int limitNum) {
        this.semaphore = new Semaphore(limitNum);
    }

    @Override
    public boolean tryAcquire() {
        return semaphore.tryAcquire();
    }

    @Override
    public void release(int permits) {
        semaphore.release(permits);
    }
}
