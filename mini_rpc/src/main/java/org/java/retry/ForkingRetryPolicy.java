package org.java.retry;

import org.java.exception.RpcException;
import org.java.message.Response;
import org.java.register.ServiceMetadata;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class ForkingRetryPolicy implements RetryPolicy {

    @Override
    public Response retry(RetryContext context) throws Exception {
        List<ServiceMetadata> serviceMetadataList = new ArrayList<>(context.getServiceMetadataList());
        serviceMetadataList.remove(context.getFailService());
        if (serviceMetadataList.isEmpty()) {
            throw new RpcException("没有可重试的provider");
        }
        CompletableFuture[] allFuture = new CompletableFuture[serviceMetadataList.size()];
        for (int i = 0; i < serviceMetadataList.size(); i++) {
            allFuture[i] = context.doRpc(serviceMetadataList.get(i));
        }
        CompletableFuture<Object> mainFuture = CompletableFuture.anyOf(allFuture);
        return (Response) mainFuture.get(Math.min(context.getRequestTimeoutMs(), context.getMethodTimeoutMs()),
            TimeUnit.MILLISECONDS);
    }
}
