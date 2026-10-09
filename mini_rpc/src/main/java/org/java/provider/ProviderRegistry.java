package org.java.provider;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 注册表
 */
public class ProviderRegistry {

    private Map<String, Invocation<?>> serviceInstanceMap = new ConcurrentHashMap<>();

    public <I> void register(Class<I> interfaceClass, I serviceInstance) {
        // 判断是否为接口
        if (!interfaceClass.isInterface()) {
            throw new IllegalArgumentException("注册的类型必须是一个接口");
        }
        // 判断是否重复注册
        if (serviceInstanceMap.putIfAbsent(interfaceClass.getName(), new Invocation<>(interfaceClass, serviceInstance)) != null) {
            throw new IllegalArgumentException(interfaceClass.getName() + "重复注册了");
        }
    }

    public Invocation<?> findService(String serviceName) {
        return serviceInstanceMap.get(serviceName);
    }

    /**
     * 把注册进来的服务实现对象包一层
     * 并在 RPC 服务端收到调用请求时，通过反射根据“方法名 + 参数类型”调用真正的服务实现方法，返回结果
     */
    public static class Invocation<I> {

        // 服务接口的实现类实例
        final I serviceInstance;

        // 服务接口
        final Class<I> interfaceClass;

        public Invocation(Class<I> interfaceClass, I serviceInstance) {
            this.serviceInstance = serviceInstance;
            this.interfaceClass = interfaceClass;
        }

        public Object invoke(String methodName, Class<?>[] paramsClass, Object[] params)
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
            Method invokeMethod = interfaceClass.getDeclaredMethod(methodName, paramsClass);
            return invokeMethod.invoke(serviceInstance, params);
        }
    }
}
