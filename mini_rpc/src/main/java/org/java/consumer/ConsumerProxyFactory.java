package org.java.consumer;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.HashedWheelTimer;
import io.netty.util.Timeout;
import lombok.extern.slf4j.Slf4j;
import org.java.codec.RequestEncoder;
import org.java.codec.SSDecoder;
import org.java.exception.RpcException;
import org.java.limit.RateLimiter;
import org.java.loadbalance.LoadBalancer;
import org.java.loadbalance.RandomLoadBalancer;
import org.java.loadbalance.RoundRobinLoadBalancer;
import org.java.message.Request;
import org.java.message.Response;
import org.java.register.*;
import org.java.retry.*;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

@Slf4j
public class ConsumerProxyFactory {

    // 连接管理器
    private final ConnectionManager manager;

    private final ServiceRegistry register;

    private final ConsumerProperties consumerProperties;

    private final InFlightRequestManager inFlightRequestManager;

    public ConsumerProxyFactory(ConsumerProperties consumerProperties) throws Exception {
        this.register = new DefaultServiceRegistry();
        this.register.init(consumerProperties.getRegistryConfig());
        this.inFlightRequestManager = new InFlightRequestManager(consumerProperties);
        this.manager = new ConnectionManager(inFlightRequestManager, consumerProperties);
        this.consumerProperties = consumerProperties;
    }

    private class ConsumerHandler extends SimpleChannelInboundHandler<Response> {

        @Override
        protected void channelRead0(ChannelHandlerContext channelHandlerContext,
                                    Response response) throws Exception {
            inFlightRequestManager.completeRequest(response.getRequestId(), response);
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            log.info("地址:{}连接了", ctx.channel().remoteAddress());
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            log.info("地址:{}断开了连接", ctx.channel().remoteAddress());
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            log.error("发生了异常", cause);
            ctx.channel().close();
        }
    }

    @SuppressWarnings("unchecked")
    public <I> I createConsumerProxy(Class<I> interfaceClass) {
        return (I) Proxy.newProxyInstance(Thread.currentThread().getContextClassLoader(),
            new Class[]{interfaceClass},
            new ConsumerInvocationHandler(interfaceClass, createLoadBalancer(), createRetryPolicy()));
    }

    private RetryPolicy createRetryPolicy() {
        switch (consumerProperties.getRetryPolicy()) {
            case "retrySame":
                return new RetrySame();
            case "failover":
                return new FailoverRetryPolicy();
            case "forking":
                return new ForkingRetryPolicy();
        }
        throw new IllegalArgumentException("没有这个重试策略" + consumerProperties.getRetryPolicy());
    }

    private LoadBalancer createLoadBalancer() {
        switch (this.consumerProperties.getLoadBalancePolicy()) {
            case "robin":
                return new RoundRobinLoadBalancer();
            case "random":
                return new RandomLoadBalancer();
            default:
                throw new IllegalArgumentException(this.consumerProperties.getLoadBalancePolicy() + "负载均衡不支持");
        }
    }

    public class ConsumerInvocationHandler implements InvocationHandler {

        final Class<?> interfaceClass;

        final LoadBalancer loadBalancer;

        final RetryPolicy retryPolicy;

        public ConsumerInvocationHandler(Class<?> interfaceClass, LoadBalancer loadBalancer, RetryPolicy retryPolicy) {
            this.interfaceClass = interfaceClass;
            this.loadBalancer = loadBalancer;
            this.retryPolicy = retryPolicy;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.getDeclaringClass() == Object.class) {
                return invokeObjectMethod(proxy, method, args);
            }
            long startTime = System.currentTimeMillis();
            List<ServiceMetadata> serviceMetadata = register.fetchServiceList(interfaceClass.getName());
            if (serviceMetadata.isEmpty()) {
                throw new RpcException(interfaceClass.getCanonicalName() + "没有对应的 provider");
            }
            ServiceMetadata providerMetadata = loadBalancer.select(serviceMetadata);
            Channel channel = manager.getChannel(providerMetadata);
            if (channel == null) {
                throw new RpcException("provider 连接失败");
            }
            Request request = buildRequest(method, args);
            CompletableFuture<Response> requestFuture = callRpcAsync(request, providerMetadata);
            Response response;
            try {
                response = requestFuture.get(consumerProperties.getRequestTimeoutMs(), TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                response = doRetry(method, args, e, startTime, providerMetadata, serviceMetadata);
            }
            return processResponse(response);
        }

        private Response doRetry(Method method, Object[] args, Exception e, long startTime, ServiceMetadata providerMetadata, List<ServiceMetadata> serviceMetadata) throws Exception {
            if (e instanceof ExecutionException ee && ee.getCause() instanceof RpcException rpcException && !rpcException.retry()) {
                throw rpcException;
            }
            Response response;
            // 重试
            long methodTimeoutMs = consumerProperties.getMethodTimeoutMs() - (System.currentTimeMillis() - startTime);
            if (methodTimeoutMs <= 0) {
                throw new TimeoutException();
            }
            log.error("rpc出现了异常，进行重试", e);
            RetryContext retryContext = new RetryContext();
            retryContext.setFailService(providerMetadata);
            retryContext.setServiceMetadataList(serviceMetadata);
            retryContext.setMethodTimeoutMs(methodTimeoutMs);
            retryContext.setLoadBalancer(this.loadBalancer);
            retryContext.setRequestTimeoutMs(consumerProperties.getRequestTimeoutMs());
            retryContext.setDoRpcFunction(provider -> callRpcAsync(buildRequest(method, args), provider));
            response = this.retryPolicy.retry(retryContext);
            return response;
        }

        private CompletableFuture<Response> callRpcAsync(Request request, ServiceMetadata provider) {
            CompletableFuture<Response> responseFuture = inFlightRequestManager.inFlightRequest(request,
                consumerProperties.getRequestTimeoutMs(), provider);
            Channel channel = manager.getChannel(provider);
            if (channel == null) {
                responseFuture.completeExceptionally(new RpcException("provider 连接失败"));
                return responseFuture;
            }
            channel.writeAndFlush(request).addListener(f -> {
                log.info("发送了request:{}", request.getRequestId());
                if (!f.isSuccess()) {
                    responseFuture.completeExceptionally(f.cause());
                }
            });
            return responseFuture;
        }

        private Object processResponse(Response response) {
            if (response.getCode() == 200) {
                return response.getResult();
            } else {
                throw new RpcException(response.getErrorMessage());
            }
        }

        private Request buildRequest(Method method, Object[] args) {
            Request request = new Request();
            request.setMethodName(method.getName());
            request.setParams(args);
            request.setParamsClass(method.getParameterTypes());
            request.setServiceName(interfaceClass.getName());
            return request;
        }

        private Object invokeObjectMethod(Object proxy, Method method, Object[] args) {
            if (method.getName().equals("toString")) {
                return "Sheng sheng Proxy Consumer " + interfaceClass.getName();
            }
            if (method.getName().equals("equals")) {
                return proxy == args[0];
            }
            if (method.getName().equals("hashCode")) {
                return System.identityHashCode(proxy);
            }
            throw new UnsupportedOperationException("代理对象不支持这个函数" + method.getName());
        }
    }
}
