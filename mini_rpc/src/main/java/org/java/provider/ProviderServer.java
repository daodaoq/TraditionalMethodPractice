package org.java.provider;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.AttributeKey;
import lombok.extern.slf4j.Slf4j;
import org.java.limit.ConcurrencyLimiter;
import org.java.limit.Limiter;
import org.java.limit.RateLimiter;
import org.java.message.Request;
import org.java.codec.ResponseEncoder;
import org.java.codec.SSDecoder;
import org.java.message.Response;
import org.java.register.*;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 服务端
 */
@Slf4j
public class ProviderServer {

    private final ProviderProperties properties;

    private final ProviderRegistry registry;

    private final ServiceRegistry serviceRegistry;

    private final Limiter globalLimiter;

    private EventLoopGroup bossEventLoopGroup;

    private EventLoopGroup workEventLoopGroup;

    public ProviderServer(ProviderProperties properties) {
        this.properties = properties;
        this.registry = new ProviderRegistry();
        this.serviceRegistry = new DefaultServiceRegistry();
        this.globalLimiter = new ConcurrencyLimiter(properties.getGlobalMaxRequest());
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
                            .addLast(new SSDecoder())
                            .addLast(new ResponseEncoder())
                            .addLast(new LimitHandler())
                            .addLast(new ProviderHandler());
                    }
                });
            serverBootstrap.bind(properties.getHost(), properties.getPort()).sync();
            // 把本地注册表中的服务都注册到注册中心里
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
                // 向后写消息
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
            // 向前写消息
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

    public class ProviderHandler extends  SimpleChannelInboundHandler<Request> {

        @Override
        protected void channelRead0(ChannelHandlerContext channelHandlerContext, Request request) throws Exception {
            ProviderRegistry.Invocation<?> invocation = registry.findService(request.getServiceName());
            if (invocation == null) {
                Response failResp = Response.fail(String.format("%s 没有对应的处理服务", request.getServiceName()), request.getRequestId());
                channelHandlerContext.writeAndFlush(failResp);
                return;
            }
            try {
                long startTime = System.currentTimeMillis();
                Object result = invocation.invoke(request.getMethodName(), request.getParamsClass(),
                    request.getParams());
                log.info("requestId:{}, {},函数被调用了{},结果是{},耗时{}ms",
                    request.getRequestId(),
                    request.getServiceName(),
                    request.getMethodName(),
                    result,
                    System.currentTimeMillis() - startTime);
                channelHandlerContext.writeAndFlush(Response.success(result, request.getRequestId()));
            } catch (Exception e) {
                Response failResp = Response.fail(e.getMessage(), request.getRequestId());
                channelHandlerContext.writeAndFlush(failResp);
            }
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

    public void stop() {
        if (bossEventLoopGroup != null) {
            bossEventLoopGroup.shutdownGracefully();
        }

        if (workEventLoopGroup != null) {
            workEventLoopGroup.shutdownGracefully();
        }
    }
}
