package org.java.retry;

import org.java.exception.RpcException;
import org.java.message.Response;
import org.java.register.ServiceMetadata;
import org.java.spi.Spi;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Spi("failover")
public class FailoverRetryPolicy implements RetryPolicy {

    @Override
    public Response retry(RetryContext context) throws Exception {
        List<ServiceMetadata> serviceMetadataList = new ArrayList<>(context.getServiceMetadataList());
        serviceMetadataList.remove(context.getFailService());
        if (serviceMetadataList.isEmpty()) {
            throw new RpcException("没有可重试的provider");
        }
        ServiceMetadata failoverService = context.getLoadBalancer().select(serviceMetadataList);
        CompletableFuture<Response> future = context.doRpc(failoverService);
        return future.get(Math.min(context.getRequestTimeoutMs(), context.getMethodTimeoutMs()), TimeUnit.MILLISECONDS);
    }
}
