package org.java.provider;

import com.alibaba.fastjson2.JSONObject;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.AttributeKey;
import lombok.extern.slf4j.Slf4j;
import org.java.codec.SSDecoder;
import org.java.codec.SSEncoder;
import org.java.compress.CompressionManager;
import org.java.handler.HeartbeatHandler;
import org.java.handler.TrafficRecordHandler;
import org.java.limit.ConcurrencyLimiter;
import org.java.limit.Limiter;
import org.java.limit.RateLimiter;
import org.java.message.Request;
import org.java.message.Response;
import org.java.register.DefaultServiceRegistry;
import org.java.register.ServiceMetadata;
import org.java.register.ServiceRegistry;
import org.java.serialize.SerializerManager;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class ProviderServer {

    private static final Set<Class<?>> NO_RESOLVE_CLASS_SET = Set.of(int.class,String.class,Integer.class);
    
    private final ProviderProperties properties;

    private final ProviderRegistry registry;

    private final ServiceRegistry serviceRegistry;

    private final Limiter globalLimiter;

    private final SerializerManager serializerManager;

    private final CompressionManager compressionManager;

    private EventLoopGroup bossEventLoopGroup;

    private EventLoopGroup workEventLoopGroup;

    private ThreadPoolExecutor invokeExecutor;

    public ProviderServer(ProviderProperties properties) {
        this.properties = properties;
        this.registry = new ProviderRegistry();
        this.serviceRegistry = new DefaultServiceRegistry();
        this.globalLimiter = new ConcurrencyLimiter(properties.getGlobalMaxRequest());
        this.serializerManager = new SerializerManager();
        this.compressionManager = new CompressionManager();
        this.invokeExecutor = new ThreadPoolExecutor(
            4,
            4,
            10,
            TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(1),
            new FastFailResponseHandler());
    }

    public <I> void register(Class<I> interfaceClass, I serviceInstance) {
        registry.register(interfaceClass, serviceInstance);
    }

    public void start() {
        bossEventLoopGroup = new NioEventLoopGroup();
        workEventLoopGroup = new NioEventLoopGroup(properties.getWorkThreadNum());
        try {
            this.serviceRegistry.init(properties.getRegistryConfig());
            ServerBootstrap serverBootstrap = new ServerBootstrap();
            serverBootstrap.group(bossEventLoopGroup, workEventLoopGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<NioSocketChannel>() {
                    @Override
                    protected void initChannel(NioSocketChannel nioSocketChannel) throws Exception {
                        nioSocketChannel.pipeline()
                            .addLast(new TrafficRecordHandler())
                            .addLast(new SSDecoder())
                            .addLast(new SSEncoder())
                            .addLast(new IdleStateHandler(30, 5, 0, TimeUnit.SECONDS))
                            .addLast(new HeartbeatHandler())
                            .addLast(new LimitHandler())
                            .addLast(new ProviderHandler());
                    }
                });
            serverBootstrap.bind(properties.getHost(), properties.getPort()).sync();
            registry.allServiceName().stream().map(this::buildMetadata).forEach(this.serviceRegistry::registerService);
        } catch (Exception e) {
            throw new RuntimeException("服务器启动异常", e);
        }
    }

    private ServiceMetadata buildMetadata(String serviceName) {
        ServiceMetadata metadata = new ServiceMetadata();
        metadata.setServiceName(serviceName);
        metadata.setPort(properties.getPort());
        metadata.setHost(properties.getHost());
        return metadata;
    }

    public class LimitHandler extends ChannelDuplexHandler {

        private static final AttributeKey<Limiter> CHANNEL_LIMITER_KEY = AttributeKey.valueOf("channel_limiter_key");
        private static final AttributeKey<AtomicInteger> GLOBAL_PERMITS = AttributeKey.valueOf("global_permits");

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            Request request = (Request) msg;
            if (!globalLimiter.tryAcquire()) {
                ctx.writeAndFlush(Response.fail("provider 限流", request.getRequestId()));
                return;
            }
            Limiter channelLimiter = ctx.channel().attr(CHANNEL_LIMITER_KEY).get();
            if (!channelLimiter.tryAcquire()) {
                globalLimiter.release();
                ctx.writeAndFlush(Response.fail("provider 限流", request.getRequestId()));
                return;
            }
            ctx.channel().attr(GLOBAL_PERMITS).get().incrementAndGet();
            ctx.fireChannelRead(request);
        }

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            promise.addListener(f -> {
                int remain = ctx.channel().attr(GLOBAL_PERMITS).get().getAndDecrement();
                if (remain > 0) {
                    ctx.channel().attr(CHANNEL_LIMITER_KEY).get().release();
                    globalLimiter.release();
                }
            });
            ctx.write(msg, promise);
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            Limiter channelLimiter = new RateLimiter(properties.getPreConsumerMaxRequest());
            ctx.channel().attr(CHANNEL_LIMITER_KEY).set(channelLimiter);
            ctx.channel().attr(GLOBAL_PERMITS).set(new AtomicInteger(0));
            ctx.fireChannelActive();
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            int remain = ctx.channel().attr(GLOBAL_PERMITS).get().getAndSet(0);
            globalLimiter.release(remain);
            ctx.fireChannelInactive();
        }
    }

    public class ProviderHandler extends SimpleChannelInboundHandler<Request> {

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Request request) throws Exception {
            ProviderRegistry.Invocation<?> invocation = registry.findService(request.getServiceName());
            if (invocation == null) {
                Response failResp = Response.fail(String.format("%s 没有对应的处理服务", request.getServiceName()),
                    request.getRequestId());
                ctx.writeAndFlush(failResp);
                return;
            }
            invokeExecutor.execute(new InvokeTask(request, ctx, invocation));
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            log.info("地址:{}连接了", ctx.channel().remoteAddress());
            ctx.channel().attr(SSEncoder.SERIALIZE_KEY).set(properties.getSerialize());
            ctx.channel().attr(SSEncoder.SERIALIZER_MANAGER_KEY).set(serializerManager);

            ctx.channel().attr(SSEncoder.COMPRESS_KEY).set(properties.getCompress());
            ctx.channel().attr(SSEncoder.COMPRESS_MANAGER_KEY).set(compressionManager);
            ctx.fireChannelActive();
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            log.info("地址:{} 断开了连接", ctx.channel().remoteAddress());
            ctx.fireChannelInactive();
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            log.error("发生了异常", cause);
            ctx.channel().close();
        }
    }

    private static class FastFailResponseHandler implements RejectedExecutionHandler {

        @Override
        public void rejectedExecution(Runnable task, ThreadPoolExecutor executor) {
            if (task instanceof InvokeTask invokeTask) {
                Response fastFail = Response.fail("服务器繁忙", invokeTask.request.getRequestId());
                invokeTask.ctx.writeAndFlush(fastFail);
                return;
            }
            throw new RuntimeException("你的task 有问题！");
        }
    }

    private static class InvokeTask implements Runnable {
        Request request;
        ChannelHandlerContext ctx;
        ProviderRegistry.Invocation<?> invocation;

        public InvokeTask(Request request, ChannelHandlerContext ctx, ProviderRegistry.Invocation<?> invocation) {
            this.request = request;
            this.ctx = ctx;
            this.invocation = invocation;
        }

        @Override
        public void run() {
            EventLoop eventLoop = ctx.channel().eventLoop();
            try {
                long startTime = System.currentTimeMillis();
                Class[] paramsType = resolveMethodParamsType(request);
                Object result = invocation.invoke(
                    request.getMethodName(),
                    paramsType,
                    resolveMethodParams(request, paramsType)); //  map  --> user
                log.info("requestId:{},{},函数被调用了{},结果是{},耗时是{}ms",
                    request.getRequestId(),
                    request.getServiceName(),
                    request.getMethodName(),
                    result,
                    System.currentTimeMillis() - startTime);
                Object finalResult = request.isGenericInvoke()? resolveResult(result) : result;
                eventLoop.execute(() -> ctx.writeAndFlush(Response.success(finalResult, request.getRequestId())));
            } catch (Exception e) {
                eventLoop.execute(() -> {
                    Response failResp = Response.fail(e.getMessage(), request.getRequestId());
                    ctx.writeAndFlush(failResp);
                });
            }
        }

        private Object resolveResult(Object result) {
            Class<?> resultClass = result.getClass();
            if(NO_RESOLVE_CLASS_SET.contains(resultClass)){
                return result;
            }
            //   list map 
            return new HashMap<>(JSONObject.from(result));
        }

        @SuppressWarnings("all")
        private Object[] resolveMethodParams(Request request, Class[] paramsType) {
            if (!request.isGenericInvoke()) {
                return request.getParams();
            }
            Object[] params = request.getParams(); 
            Object[] result = new Object[params.length];
            for (int i = 0; i < params.length; i++) {
                if(params[i] instanceof Map){
                    result[i] = new JSONObject((Map) params[i]).toJavaObject(paramsType[i]);
                }else{
                    result[i] = params[i];
                }
            }
            return result;
        }

        @SuppressWarnings("all")
        private Class[] resolveMethodParamsType(Request request) throws ClassNotFoundException {
            if (!request.isGenericInvoke()) {
                return request.getParamsClass();
            }
            String[] paramsClassStr = request.getParamsClassStr();
            Class[] result = new Class[paramsClassStr.length];
            for (int i = 0; i < paramsClassStr.length; i++) {
                String classStr = paramsClassStr[i];
                result[i] = analysisFromString(classStr);
            }
            return result;
        }

        private Class<?> analysisFromString(String classStr) throws ClassNotFoundException {
            if (classStr.equals("int")) {
                return int.class;
            }
            return Class.forName(classStr);
        }
    }

    public void stop() {
        if (bossEventLoopGroup != null) {
            bossEventLoopGroup.shutdownGracefully();
        }
        if (workEventLoopGroup != null) {
            workEventLoopGroup.shutdownGracefully();
        }
    }


}
