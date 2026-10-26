package org.java.fallback;

import lombok.extern.slf4j.Slf4j;
import org.java.metrics.RpcCallMetrics;

@Slf4j
public class DefaultFallback implements Fallback {

    private final CacheFallback cacheFallback;
    private final MockFallback mockFallback;

    public DefaultFallback(CacheFallback cacheFallback, MockFallback mockFallback) {
        this.cacheFallback = cacheFallback;
        this.mockFallback = mockFallback;
    }

    @Override
    public void recordMetrics(RpcCallMetrics metrics) {
        this.cacheFallback.recordMetrics(metrics);
        this.mockFallback.recordMetrics(metrics);
    }

    @Override
    public Object fallback(RpcCallMetrics metrics) throws Exception{
        try {
            return cacheFallback.fallback(metrics);
        } catch (Exception e) {
            log.warn("缓存降级没有生效");
            return mockFallback.fallback(metrics);
        }
    }
}
