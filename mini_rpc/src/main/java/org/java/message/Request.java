package org.java.message;

import lombok.Data;

import java.util.concurrent.atomic.AtomicInteger;

@Data
public class Request {

    private static final AtomicInteger REQUEST_COUNTER = new AtomicInteger();

    private int requestId = REQUEST_COUNTER.getAndIncrement();

    private String serviceName;

    private String methodName;

    private Class<?>[] paramsClass;

    private Object[] params;
}
