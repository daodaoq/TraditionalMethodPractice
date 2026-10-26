package org.java.fallback;

import org.java.exception.RpcException;
import org.java.metrics.RpcCallMetrics;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


public class MockFallback implements Fallback {

    private final Map<Class<?>, Object> mockObjectCache = new ConcurrentHashMap<>();

    @Override
    public Object fallback(RpcCallMetrics metrics) throws Exception {
        Method method = metrics.getMethod();
        RpcFallback annotation = method.getDeclaringClass().getAnnotation(RpcFallback.class);
        if (annotation == null) {
            throw new RpcException("属实是没招了！");
        }
        Class<?> methodClass = annotation.value();
        if (!method.getDeclaringClass().isAssignableFrom(methodClass)) {
            throw new RpcException(String.format("你调用了%s,但是降级策略是%s", method, methodClass));
        }
        Object mockObject = mockObjectCache.computeIfAbsent(methodClass, this::createMockObject);
        return method.invoke(mockObject, metrics.getParams());
    }

    private Object createMockObject(Class<?> methodClass) {
        try {
            return methodClass.getConstructor().newInstance();
        } catch (Exception e) {
            throw new RpcException("创建mock对象失败", e);
        }

    }

}
