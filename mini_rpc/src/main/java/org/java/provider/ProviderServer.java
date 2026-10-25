package org.java.provider;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import lombok.extern.slf4j.Slf4j;
import org.java.message.Request;
import org.java.codec.ResponseEncoder;
import org.java.codec.SSDecoder;
import org.java.message.Response;
import org.java.register.*;

/**
 * 服务端
 */
@Slf4j
public class ProviderServer {

    private final int port;

    private final String host;

    private final ProviderRegistry registry;

    private final ServiceRegistry serviceRegistry;

    private final RegistryConfig registryConfig;

    private EventLoopGroup bossEventLoopGroup;

    private EventLoopGroup workEventLoopGroup;

    public ProviderServer(String host, int port, RegistryConfig registryConfig) {
        this.host = host;
        this.port = port;
        this.registry = new ProviderRegistry();
        this.serviceRegistry = new DefaultServiceRegistry();
        this.registryConfig = registryConfig;
    }

    public <I> void register(Class<I> interfaceClass, I serviceInstance) {
        registry.register(interfaceClass, serviceInstance);
    }

    public void start() {
        bossEventLoopGroup = new NioEventLoopGroup();
        workEventLoopGroup = new NioEventLoopGroup(4);
        try {
            this.serviceRegistry.init(registryConfig);
            ServerBootstrap serverBootstrap = new ServerBootstrap();
            serverBootstrap.group(bossEventLoopGroup, workEventLoopGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<NioSocketChannel>() {
                    @Override
                    protected void initChannel(NioSocketChannel nioSocketChannel) throws Exception {
                        nioSocketChannel.pipeline()
                            .addLast(new SSDecoder())
                            .addLast(new ResponseEncoder())
                            .addLast(new ProviderHandler());
                    }
                });
            serverBootstrap.bind(port).sync();
            // 把本地注册表中的服务都注册到注册中心里
            registry.allServiceName().stream().map(this::buildMetadata).forEach(this.serviceRegistry::registerService);
        } catch (Exception e) {
            throw new RuntimeException("服务器启动异常", e);
        }
    }

    private ServiceMetadata buildMetadata(String serviceName) {
        ServiceMetadata metadata = new ServiceMetadata();
        metadata.setServiceName(serviceName);
        metadata.setPort(port);
        metadata.setHost(host);
        return metadata;
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
                Object result = invocation.invoke(request.getMethodName(), request.getParamsClass(), request.getParams());
                log.info("{},函数被调用了{},结果是{}", request.getServiceName(), request.getMethodName(), result);
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
