package org.java.provider;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.java.message.Request;
import org.java.codec.ResponseEncoder;
import org.java.codec.SSDecoder;
import org.java.message.Response;

/**
 * 服务端
 */
public class ProviderServer {

    private final int port;

    private final ProviderRegistry registry;

    private EventLoopGroup bossEventLoopGroup;

    private EventLoopGroup workEventLoopGroup;

    public  ProviderServer(int port) {
        this.port = port;
        this.registry = new ProviderRegistry();
    }

    public <I> void register(Class<I> interfaceClass, I serviceInstance) {
        registry.register(interfaceClass, serviceInstance);
    }

    public void start() {
        bossEventLoopGroup = new NioEventLoopGroup();
        workEventLoopGroup = new NioEventLoopGroup(4);
        try {
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
        } catch (Exception e) {
            throw new RuntimeException("服务器启动异常", e);
        }
    }

    public  class ProviderHandler extends  SimpleChannelInboundHandler<Request> {
        @Override
        protected void channelRead0(ChannelHandlerContext channelHandlerContext, Request request) throws Exception {
            ProviderRegistry.Invocation<?> service = registry.findService(request.getServiceName());
            Object result = service.invoke(request.getMethodName(), request.getParamsClass() ,request.getParams());
            Response response = new Response();
            response.setResult(result);
            channelHandlerContext.writeAndFlush(response);
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
