package org.java.consumer;

import io.netty.channel.Channel;
import lombok.extern.slf4j.Slf4j;
import org.java.breaker.CircuitBreaker;
import org.java.breaker.CircuitBreakerManager;
import org.java.exception.RpcException;
import org.java.fallback.CacheFallback;
import org.java.fallback.DefaultFallback;
import org.java.fallback.Fallback;
import org.java.fallback.MockFallback;
import org.java.loadbalance.LoadBalancer;
import org.java.loadbalance.RandomLoadBalancer;
import org.java.loadbalance.RoundRobinLoadBalancer;
import org.java.message.Request;
import org.java.message.Response;
import org.java.metrics.RpcCallMetrics;
import org.java.register.DefaultServiceRegistry;
import org.java.register.ServiceMetadata;
import org.java.register.ServiceRegistry;
import org.java.retry.RetryContext;
import org.java.retry.RetryPolicy;
import org.java.retry.RetryPolicyManager;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
public class ConsumerProxyFactory {

    private final ConnectionManager manager;

    private final ServiceRegistry registry;

    private final ConsumerProperties consumerProperties;

    private final InFlightRequestManager inFlightRequestManager;
    
    private final CircuitBreakerManager circuitBreakerManager;
    
    private final RetryPolicyManager retryPolicyManager;
    
    private final Fallback fallback;


    public ConsumerProxyFactory(ConsumerProperties consumerProperties) throws Exception {
        this.registry = new DefaultServiceRegistry();
        this.registry.init(consumerProperties.getRegistryConfig());
        this.inFlightRequestManager = new InFlightRequestManager(consumerProperties);
        this.manager = new ConnectionManager(inFlightRequestManager, consumerProperties);
        this.circuitBreakerManager = new CircuitBreakerManager(consumerProperties);
        this.consumerProperties = consumerProperties;
        this.retryPolicyManager = new RetryPolicyManager();
        this.fallback = new DefaultFallback(new CacheFallback(),new MockFallback());

    }

    @SuppressWarnings("unchecked")
    public <I> I createConsumerProxy(Class<I> interfaceClass) {
        return (I) Proxy.newProxyInstance(Thread.currentThread().getContextClassLoader(),
            new Class[]{interfaceClass},
            new ConsumerInvocationHandler(interfaceClass,
                createLoadBalancer(), 
                createRetryPolicy(consumerProperties.getRetryPolicy())));
    }

    private RetryPolicy createRetryPolicy(String name) {
        RetryPolicy retryPolicy = retryPolicyManager.getRetryPolicy(name);
        if(retryPolicy == null){
            throw new IllegalArgumentException("没有这个重试策略" + name);
        }
        return retryPolicy;
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
            boolean genericInvoke = method.getName().equals("$invoke");
            String serviceName = genericInvoke? args[0].toString() :interfaceClass.getName();
            List<ServiceMetadata> serviceMetadata = new ArrayList<>(registry.fetchServiceList(serviceName));
            ServiceMetadata provider = decideProvider(serviceMetadata);
            RpcCallMetrics metrics = RpcCallMetrics.createRpcCallMetrics(method,args,provider);
            if (provider == null) {
                return fallback.fallback(metrics);
            }
            Request request = buildRequest(method, args);
            CircuitBreaker breaker = circuitBreakerManager.createOrGetBreaker(provider);
            try {
                CompletableFuture<Response> requestFuture = callRpcAsync(request, provider);
                Response response = requestFuture.get(consumerProperties.getRequestTimeoutMs(), TimeUnit.MILLISECONDS);
                metrics.complete(response);
                breaker.recordRpc(metrics);
                fallback.recordMetrics(metrics);
                return processResponse(response);
            } catch (Exception e) {
                metrics.errorComplete(e);
                breaker.recordRpc(metrics);
            }
            try {
                return processResponse(doRetry(metrics, serviceMetadata));
            }catch (Exception e){
                return fallback.fallback(metrics);
            }
        }
        
        private ServiceMetadata decideProvider(List<ServiceMetadata> candidate){
            while (!candidate.isEmpty()){
                ServiceMetadata select = this.loadBalancer.select(candidate);
                CircuitBreaker breaker = circuitBreakerManager.createOrGetBreaker(select);
                if (breaker.allowRequest()) {
                    return select;
                }
                candidate.remove(select);
            }
            return null;
        }

        private Response doRetry(RpcCallMetrics metrics, List<ServiceMetadata> serviceMetadata) throws Exception {
            Throwable e = metrics.getThrowable();
            if (e instanceof ExecutionException ee && ee.getCause() instanceof RpcException rpcException && !rpcException.retry()) {
                throw rpcException;
            }
            Response response;
            long methodTimeoutMs = consumerProperties.getMethodTimeoutMs() - metrics.getDuration();
            if (methodTimeoutMs <= 0) {
                throw new TimeoutException();
            }
            log.warn("rpc出现了异常，进行重试", e);
            RetryContext retryContext = createRetryContextFromFailMetrics(metrics, serviceMetadata, methodTimeoutMs);
            response = this.retryPolicy.retry(retryContext);
            return response;
        }

        private RetryContext createRetryContextFromFailMetrics(RpcCallMetrics metrics, List<ServiceMetadata> serviceMetadata, long methodTimeoutMs) {
            RetryContext retryContext = new RetryContext();
            retryContext.setFailService(metrics.getProvider());
            retryContext.setServiceMetadataList(serviceMetadata);
            retryContext.setMethodTimeoutMs(methodTimeoutMs);
            retryContext.setLoadBalancer(this.loadBalancer);
            retryContext.setRequestTimeoutMs(consumerProperties.getRequestTimeoutMs());
            retryContext.setDoRpcFunction(provider -> {
                CircuitBreaker breaker = circuitBreakerManager.createOrGetBreaker(provider);
                if(!breaker.allowRequest()){
                    CompletableFuture<Response> breakFuture = new CompletableFuture<>();
                    breakFuture.completeExceptionally(new RpcException("provider熔断了"));
                    return breakFuture;
                }
                RpcCallMetrics retryMetrics = RpcCallMetrics.createRpcCallMetrics(metrics.getMethod(), metrics.getParams(),provider);
                CompletableFuture<Response> requestFuture = callRpcAsync(buildRequest(metrics.getMethod(), metrics.getParams()), provider);
                requestFuture.whenComplete((r,retryE)->{
                    if (retryE == null) {
                        retryMetrics.complete(r);
                    }else{
                        retryMetrics.errorComplete(retryE);
                    }
                    breaker.recordRpc(retryMetrics);
                });
                return requestFuture;
            });
            return retryContext;
        }

        private CompletableFuture<Response> callRpcAsync(Request request, ServiceMetadata provider) {
            CompletableFuture<Response> responseFuture = inFlightRequestManager
                .inFlightRequest(request,
                    consumerProperties.getRequestTimeoutMs(),
                    provider);
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
            }
            throw new RpcException(response.getErrorMessage());
        }

        private Request buildRequest(Method method, Object[] args) {
            boolean genericInvoke = method.getName().equals("$invoke");
            Request request = new Request();
            request.setGenericInvoke(genericInvoke);
            if(genericInvoke){
                request.setParamsClassStr((String[]) args[2]);
                request.setServiceName(args[0].toString());
                request.setMethodName(args[1].toString());
                request.setParams((Object[]) args[3]);
            }else{
                request.setParamsClass(method.getParameterTypes());
                request.setServiceName(interfaceClass.getName());
                request.setMethodName(method.getName());
                request.setParams(args);
            }
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
