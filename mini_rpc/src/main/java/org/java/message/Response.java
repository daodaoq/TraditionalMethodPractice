package org.java.message;

import lombok.Data;

import java.io.Serializable;

@Data
public class Response implements Serializable {
    private Object result;
    private int code;
    private String errorMessage;
    private int requestId;

    public static Response fail(String errorMessage, int requestId) {
        Response response = new Response();
        response.errorMessage = errorMessage;
        response.code = 400;
        response.requestId = requestId;
        return response;
    }

    public static Response success(Object result, int requestId) {
        Response response = new Response();
        response.result = result;
        response.code = 200;
        response.requestId = requestId;
        return response;
    }
}
