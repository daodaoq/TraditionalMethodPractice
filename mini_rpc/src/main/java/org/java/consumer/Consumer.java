package org.java.consumer;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.java.api.Add;
import org.java.codec.RequestEncoder;
import org.java.message.Request;
import org.java.message.Response;
import org.java.codec.ResponseEncoder;
import org.java.codec.SSDecoder;

import java.util.concurrent.CompletableFuture;

/**
 * 客户端
 */
public class Consumer implements Add {

    public int add(int a, int b) {
        try {
            CompletableFuture<Integer> addResultFuture = new CompletableFuture<>();
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
                                protected void channelRead0(ChannelHandlerContext channelHandlerContext, Response response) throws Exception {
                                    addResultFuture.complete(Integer.valueOf(response.getResult().toString()));
                                }
                            });
                    }
                });

            ChannelFuture channelFuture = bootstrap.connect("localhost", 8888).sync();
            Request request = new Request();
            request.setMethodName("add");
            request.setParams(new Object[]{a, b});
            request.setParamsClass(new Class[]{int.class, int.class});
            request.setServiceName(Add.class.getName());
            channelFuture.channel().writeAndFlush(request);
            return addResultFuture.get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
