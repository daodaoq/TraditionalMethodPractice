package org.java.message;

import lombok.Data;

@Data
public class Request {

    private String serviceName;

    private String methodName;

    private Class<?>[] paramsClass;

    private Object[] params;
}
