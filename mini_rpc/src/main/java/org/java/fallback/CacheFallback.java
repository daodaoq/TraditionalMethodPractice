package org.java.fallback;

import lombok.Data;
import org.java.exception.RpcException;
import org.java.metrics.RpcCallMetrics;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class CacheFallback implements Fallback {

    private static final Object NULL_OBJECT = new Object();

    private final Map<InvokeKey, Object> rpcResultCache = new ConcurrentHashMap<>();

    @Override
    public Object fallback(RpcCallMetrics metrics) {
        InvokeKey invokeKey = new InvokeKey(metrics.getMethod(), metrics.getParams());
        Object cacheResult = rpcResultCache.get(invokeKey);
        if (cacheResult == NULL_OBJECT) {
            return null;
        }
        if (cacheResult == null) {
            throw new RpcException("缓存降级没招啦！");
        }
        return cacheResult;
    }

    @Override
    public void recordMetrics(RpcCallMetrics metrics) {
        InvokeKey invokeKey = new InvokeKey(metrics.getMethod(), metrics.getParams());
        Object result = metrics.getResult();
        if (result == null) {
            result = NULL_OBJECT;
        }
        rpcResultCache.put(invokeKey, result);
    }

    @Data
    private class InvokeKey {
        final Method method;
        final Object[] args;

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            InvokeKey invokeKey = (InvokeKey) o;
            return Objects.equals(method, invokeKey.method) && Objects.deepEquals(args, invokeKey.args);
        }

        @Override
        public int hashCode() {
            return Objects.hash(method, Arrays.hashCode(args));
        }
    }
}
