package org.java.metrics;

import lombok.Data;
import org.java.message.Response;
import org.java.register.ServiceMetadata;

import java.lang.reflect.Method;

@Data
public class RpcCallMetrics {
    private boolean complete;
    private Throwable throwable;
    private long duration;
    private long startTime;
    private Method method;
    private ServiceMetadata provider;
    private Object[] params;
    private Object result;

    private RpcCallMetrics() {

    }

    public static RpcCallMetrics createRpcCallMetrics(Method method,Object[] params,ServiceMetadata provider) {
        RpcCallMetrics metrics = new RpcCallMetrics();
        metrics.startTime = System.currentTimeMillis();
        metrics.method = method;
        metrics.params = params;
        metrics.provider = provider;
        return metrics;
    }

    public void complete(Response response) {
        this.complete = true;
        this.duration = System.currentTimeMillis() - startTime;
        this.result = response.getResult();
    }

    public void errorComplete(Throwable throwable) {
        this.duration = System.currentTimeMillis() - startTime;
        this.throwable = throwable;
    }
}
