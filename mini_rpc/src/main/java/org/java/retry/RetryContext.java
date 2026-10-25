package org.java.retry;

import lombok.Data;
import org.java.loadbalance.LoadBalancer;
import org.java.message.Response;
import org.java.register.ServiceMetadata;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

@Data
public class RetryContext {

    private ServiceMetadata failService;

    private List<ServiceMetadata> serviceMetadataList;

    private long methodTimeoutMs;

    private long requestTimeoutMs;

    private LoadBalancer loadBalancer;

    private Function<ServiceMetadata, CompletableFuture<Response>> doRpcFunction;

    public CompletableFuture<Response> doRpc(ServiceMetadata serviceMetadata) {
        return doRpcFunction.apply(serviceMetadata);
    }

}
