package org.java.retry;

import lombok.extern.slf4j.Slf4j;
import org.java.exception.RpcException;
import org.java.message.Response;
import org.java.spi.Spi;

import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Spi("retrySame")
public class RetrySame implements RetryPolicy {

    final int retryMax = 3;

    private final Random random = new Random();

    @Override
    public Response retry(RetryContext context) throws Exception {
        long startTime = System.currentTimeMillis();
        int retryCount = 0;
        while (retryCount < retryMax) {
            long nextDelay = nextDelay(retryCount);
            if (nextDelay >= 1000) {
                nextDelay = 1000;
            }
            long methodTimeoutMs = context.getMethodTimeoutMs() - (System.currentTimeMillis() - startTime);
            if (methodTimeoutMs <= 0 || nextDelay >= methodTimeoutMs) {
                throw new TimeoutException();
            }
            Thread.sleep(nextDelay);
            try {
                log.info("开始重试");
                CompletableFuture<Response> future = context.doRpc(context.getFailService());
                return future.get(Math.min(methodTimeoutMs, context.getRequestTimeoutMs()), TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                log.error("重试发生错误", e);
            }
            retryCount++;

        }
        throw new RpcException("重试失败");
    }

    private long nextDelay(int retryCount) {
        return 100L * (1L << retryCount) + random.nextInt(0, 50);
    }

}
