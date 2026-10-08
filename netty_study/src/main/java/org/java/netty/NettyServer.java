package org.java.netty;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NettyServer {

    public static void main(String[] args) {
        Map<Channel, List<String>> db = new HashMap<>();
        ServerBootstrap serverBootstrap = new ServerBootstrap()
            .group(new NioEventLoopGroup(), new NioEventLoopGroup()) // 设置线程组，第一个是 bossGroup，第二个是 workerGroup
            .channel(NioServerSocketChannel.class) // 指定服务端 Channel 类型
            .childHandler(new ChannelInitializer<SocketChannel>() { // 指定新连接的处理器
                /**
                 * handler(...)：给 bossGroup 的监听 Channel 用，处理 accept 相关
                 * childHandler(...)：给 workerGroup 里每条新连接 用，处理读写
                 * ChannelInitializer 是一个特殊的 handler
                 * 它只在每条新连接建立时执行一次，用来给这个连接的 Pipeline 装 handler
                 */
                @Override
                protected void initChannel(SocketChannel ch) throws Exception {
                    /**
                     * 每个 Channel 有一个 Pipeline（责任链），里面是一串 ChannelHandler
                     * 数据从 socket 进来后，会沿着 pipeline 依次经过每个 handler，像流水线一样
                     */
                    ch.pipeline().addLast(new LineBasedFrameDecoder(1024))
                        .addLast(new StringDecoder())
                        .addLast(new StringEncoder())
                        .addLast(new ResponseHandler())
                        .addLast(new DbHandler(db));
                }
            });
        ChannelFuture bindFuture = serverBootstrap.bind(8080);
        bindFuture.addListener(f -> {
            if (f.isSuccess()) {
                System.out.println("我们的服务器成功监听端口" + 8080);
            } else {
                System.out.println("服务器监听端口失败");
            }
        });
    }

    static class ResponseHandler extends SimpleChannelInboundHandler<String> {
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, String msg) throws Exception {
            System.out.println(msg);
            String message = msg + " world\n";
            ctx.channel().writeAndFlush(message);
            // 为了让消息继续往下一个节点传
            ctx.fireChannelRead(msg);
        }

        @Override
        public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
            System.out.println(ctx.channel() + "注册了");
            // 传递注册事件
            ctx.fireChannelRegistered();
        }
    }

    static class DbHandler extends SimpleChannelInboundHandler<String> {
        private Map<Channel, List<String>> db;

        public DbHandler(Map<Channel, List<String>> db) {
            this.db = db;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, String msg) throws Exception {
            List<String> messageList = db.computeIfAbsent(ctx.channel(), k -> new ArrayList<>());
            messageList.add(msg);
        }

        @Override
        public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
            System.out.println(ctx.channel() + "注册了");
        }

        @Override
        public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
            System.out.println(ctx.channel() + "解除注册了");
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            System.out.println(ctx.channel() + "可以使用了");
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            List<String> strings = db.get(ctx.channel());
            System.out.println(strings);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            super.exceptionCaught(ctx, cause);
        }
    }
}
