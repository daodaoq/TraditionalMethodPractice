package org.java.breaker;

import org.java.metrics.RpcCallMetrics;

public interface CircuitBreaker {

    boolean allowRequest();

    void recordRpc(RpcCallMetrics metrics);


    enum State {
        OPEN, CLOSE, HALF_OPEN
    }
}
