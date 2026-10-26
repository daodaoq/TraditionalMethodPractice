package org.java.breaker;

import org.java.metrics.RpcCallMetrics;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class ResponseTimeCircuitBreaker implements CircuitBreaker {

    private final long breakMs = 10000;

    private final long windowDurationMs = 10000;

    private final long slotMs = 1000;

    private final long slowRequestMs ;
    
    private final double slowRatio;
    
    private final int minRequest = 5;

    private final Slot[] slots = new Slot[(int) (windowDurationMs / slotMs)];

    private volatile int currentIndex = 0;

    private volatile long currentTime = System.currentTimeMillis() / slotMs * slotMs;

    private volatile long breakStartTime = 0;

    private final AtomicReference<State> stateReference = new AtomicReference<>(State.CLOSE);

    private final Lock slideLock = new ReentrantLock();


    public ResponseTimeCircuitBreaker(double slowRatio,long slowRequestMs) {
        this.slowRequestMs = slowRequestMs;
        this.slowRatio = slowRatio;
        for (int i = 0; i < slots.length; i++) {
            slots[i] = new Slot();
        }
    }

    @Override
    public boolean allowRequest() {
        if (stateReference.get() == State.CLOSE) {
            return true;
        }
        if (stateReference.get() == State.HALF_OPEN) {
            return false;
        }
        if (System.currentTimeMillis() - breakStartTime < breakMs) {
            return false;
        }
        return stateReference.compareAndSet(State.OPEN, State.HALF_OPEN);
    }

    @Override
    public void recordRpc(RpcCallMetrics metrics) {
        long now = System.currentTimeMillis();
        slideWindowIfNecessary(now);
        boolean slowRequest = !metrics.isComplete() || metrics.getDuration() > slowRequestMs;
        switch (stateReference.get()) {
            case OPEN -> processOpen(slowRequest);
            case CLOSE -> processClose(slowRequest);
            case HALF_OPEN -> processHalfOpen(slowRequest);
        }
    }

    private void processOpen(boolean slowRequest) {

    }

    private void processClose(boolean slowRequest) {
        if(!slowRequest){
            slots[currentIndex].requestCount.incrementAndGet();
            return;
        }
        slots[currentIndex].requestCount.incrementAndGet();
        slots[currentIndex].errorRequestCount.incrementAndGet();
        int totalRequest = 0;
        int totalErrorRequest = 0;
        for (Slot slot : slots) {
            totalRequest += slot.requestCount.get();
            totalErrorRequest += slot.errorRequestCount.get();
        }
        if (totalRequest < minRequest) {
            return;
        }
        double errorRatio = ((double) totalErrorRequest )/ totalRequest;
        if(errorRatio > slowRatio && this.stateReference.compareAndSet(State.CLOSE,State.OPEN)){
            this.breakStartTime = System.currentTimeMillis();
        }
        
    }

    private void processHalfOpen(boolean slowRequest) {
        if (!slowRequest) {
            this.stateReference.compareAndSet(State.HALF_OPEN, State.CLOSE);
            return;
        }
        if (this.stateReference.compareAndSet(State.HALF_OPEN, State.OPEN)) {
            this.breakStartTime = System.currentTimeMillis();
        }
    }

    private void slideWindowIfNecessary(long now) {
        if (now - currentTime < slotMs) {
            return;
        }
        try {
            slideLock.lock();
            int diff = (int) ((now - currentTime) / slotMs);
            if (diff <= 0) {
                return;
            }
            int step = Math.min(slots.length, diff);
            for (int i = 0; i < step; i++) {
                int updateIndex = (currentIndex + i + 1) % slots.length;
                slots[updateIndex].requestCount.set(0);
                slots[updateIndex].errorRequestCount.set(0);
            }
            currentIndex = (currentIndex + diff) % slots.length;
            currentTime = now / slotMs * slotMs;
        } finally {
            slideLock.unlock();
        }
    }

    public class Slot {
        AtomicInteger requestCount = new AtomicInteger(0);
        AtomicInteger errorRequestCount = new AtomicInteger(0);
    }

}
