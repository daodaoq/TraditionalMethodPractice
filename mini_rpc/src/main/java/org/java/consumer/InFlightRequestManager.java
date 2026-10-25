package org.java.consumer;

import io.netty.util.HashedWheelTimer;
import io.netty.util.Timeout;
import lombok.extern.slf4j.Slf4j;
import org.java.exception.LimitException;
import org.java.limit.ConcurrencyLimiter;
import org.java.limit.Limiter;
import org.java.limit.RateLimiter;
import org.java.message.Request;
import org.java.message.Response;
import org.java.register.ServiceMetadata;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
public class InFlightRequestManager {

    // 在途请求
    private final Map<Integer, CompletableFuture<Response>> inFlightRequestTable;

    private final HashedWheelTimer timeoutTimer;

    private final Map<ServiceMetadata, Limiter> channelLimiterMap;

    private final Limiter globalLimiter;

    private final ConsumerProperties consumerProperties;

    public InFlightRequestManager(ConsumerProperties consumerProperties) {
        this.consumerProperties = consumerProperties;
        this.globalLimiter = new ConcurrencyLimiter(consumerProperties.getRpcPreSecond());
        this.inFlightRequestTable = new ConcurrentHashMap<>();
        this.timeoutTimer = new HashedWheelTimer(100, TimeUnit.MILLISECONDS, 256);
        this.channelLimiterMap = new ConcurrentHashMap<>();
    }

    public CompletableFuture<Response> inFlightRequest(Request request, long timeoutMs, ServiceMetadata metadata) {
        CompletableFuture<Response> responseFuture = new CompletableFuture<>();
        if (!globalLimiter.tryAcquire()) {
            responseFuture.completeExceptionally(new LimitException("全局限流，当前在途请求超过阈值"));
            return responseFuture;
        }
        Limiter channelLimiter = channelLimiterMap.computeIfAbsent(metadata,
            k -> new RateLimiter(consumerProperties.getRpcPreChannel()));
        if (!channelLimiter.tryAcquire()) {
            responseFuture.completeExceptionally(new LimitException("channel限流，当前在途请求超过阈值"));
            return responseFuture;
        }
        inFlightRequestTable.put(request.getRequestId(), responseFuture);
        Timeout timeout = timeoutTimer.newTimeout(
            (t) -> responseFuture.completeExceptionally(new TimeoutException()),
            timeoutMs,
            TimeUnit.MILLISECONDS);
        responseFuture.whenComplete((r, e) -> {
            inFlightRequestTable.remove(request.getRequestId());
            globalLimiter.release();
            channelLimiter.release();
            timeout.cancel();
        });
        return responseFuture;
    }

    public void clearChannel(ServiceMetadata metadata) {
        channelLimiterMap.remove(metadata);
    }

    public boolean completeRequest(int requestId, Response response) {
        CompletableFuture<Response> future = inFlightRequestTable.remove(requestId);
        if (future == null) {
            log.warn("request Id:{}, 空闲返回", requestId);
            return false;
        }
        return future.complete(response);
    }

    public boolean completeExceptionallyRequest(int requestId, Exception e) {
        CompletableFuture<Response> future = inFlightRequestTable.remove(requestId);
        if (future == null) {
            log.warn("request Id:{}, 空闲异常", requestId, e);
            return false;
        }
        return future.completeExceptionally(e);
    }
}
