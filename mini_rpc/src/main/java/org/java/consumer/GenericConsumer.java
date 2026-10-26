package org.java.consumer;

public interface GenericConsumer {
    
    Object $invoke(String serviceName, String methodName, String[] paramsType, Object[] args);
}
