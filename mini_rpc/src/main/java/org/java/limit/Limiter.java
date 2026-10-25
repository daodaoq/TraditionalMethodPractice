package org.java.limit;

public interface Limiter {

    // 拿不到快速失败
    boolean tryAcquire();

    default void release(){
        release(1);
    }

    void release(int permits);
}
