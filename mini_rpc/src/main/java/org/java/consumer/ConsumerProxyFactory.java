package org.java.consumer;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.java.codec.RequestEncoder;
import org.java.codec.SSDecoder;
import org.java.exception.RpcException;
import org.java.message.Request;
import org.java.message.Response;
import org.java.register.*;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
public class ConsumerProxyFactory {

    // 在途请求
    private final Map<Integer, CompletableFuture<Response>> inFlightRequestTable = new ConcurrentHashMap<>();

    // 连接管理器
    private final ConnectionManager manager = new ConnectionManager(createBootstrap());

    private final ServiceRegistry register;

    public ConsumerProxyFactory(RegistryConfig registryConfig) throws Exception {
        this.register = new DefaultServiceRegistry();
        this.register.init(registryConfig);
    }

    private Bootstrap createBootstrap() {
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(new NioEventLoopGroup(4))
            .channel(NioSocketChannel.class)
            .handler(new ChannelInitializer<NioSocketChannel>() {
                @Override
                protected void initChannel(NioSocketChannel nioSocketChannel) throws Exception {
                    nioSocketChannel.pipeline()
                        .addLast(new SSDecoder())
                        .addLast(new RequestEncoder())
                        .addLast(new SimpleChannelInboundHandler<Response>() {
                            @Override
                            protected void channelRead0(ChannelHandlerContext channelHandlerContext,
                                                        Response response) throws Exception {
                                CompletableFuture<Response> responseFuture = inFlightRequestTable.remove(response.getRequestId());
                                if (responseFuture == null) {
                                    log.warn("request Id{}找不到", response.getRequestId());
                                    return;
                                }
                                responseFuture.complete(response);
                            }
                        });
                }
            });

        return bootstrap;
    }

    @SuppressWarnings("unchecked")
    public <I> I createConsumerProxy(Class<I> interfaceClass) {
        return (I) Proxy.newProxyInstance(Thread.currentThread().getContextClassLoader(),
            new Class[]{interfaceClass},
            new ConsumerInvocationHandler(interfaceClass));
    }

    public class ConsumerInvocationHandler implements InvocationHandler {

        final Class<?> interfaceClass;

        public ConsumerInvocationHandler(Class<?> interfaceClass) {
            this.interfaceClass = interfaceClass;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.getDeclaringClass() == Object.class) {
                return invokeObjectMethod(proxy, method, args);
            }
            CompletableFuture<Response> responseFuture = new CompletableFuture<>();
            try {
                List<ServiceMetadata> serviceMetadata = register.fetchServiceList(interfaceClass.getName());
                if (serviceMetadata.isEmpty()) {
                    throw new RpcException(interfaceClass.getCanonicalName() + "没有对应的 provider");
                }
                ServiceMetadata providerMetadata = serviceMetadata.get(0);
                Channel channel = manager.getChannel(providerMetadata.getHost(), providerMetadata.getPort());
                if (channel == null) {
                    throw new RpcException("provider 连接失败");
                }
                Request request = buildRequest(method, args);
                inFlightRequestTable.put(request.getRequestId(), responseFuture);
                channel.writeAndFlush(request).addListener(f -> {
                    if (!f.isSuccess()) {
                        inFlightRequestTable.remove(request.getRequestId());
                        responseFuture.completeExceptionally(f.cause());
                    }
                });
                Response response = responseFuture.get(3, TimeUnit.SECONDS);
                return processResponse(response);
            } catch (RpcException rpcException) {
                throw rpcException;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        private static Object processResponse(Response response) {
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
