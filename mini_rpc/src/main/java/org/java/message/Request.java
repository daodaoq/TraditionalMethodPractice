package org.java.message;

import lombok.Data;

import java.io.Serializable;
import java.util.concurrent.atomic.AtomicInteger;

@Data
public class Request implements Serializable {

    private static final AtomicInteger REQUEST_COUNTER = new AtomicInteger();

    private int requestId = REQUEST_COUNTER.getAndIncrement();
    
    private String serviceName;
    private String methodName;
    private boolean genericInvoke;
    private Class<?>[] paramsClass;
    private String[] paramsClassStr;
    private Object[] params;
}
