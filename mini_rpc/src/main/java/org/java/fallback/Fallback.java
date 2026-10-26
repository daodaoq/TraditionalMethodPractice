package org.java.fallback;


import org.java.metrics.RpcCallMetrics;

public interface Fallback {

    Object fallback(RpcCallMetrics metrics) throws Exception;

    default void recordMetrics(RpcCallMetrics metrics) {

    }
}
