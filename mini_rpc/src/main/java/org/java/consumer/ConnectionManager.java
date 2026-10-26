package org.java.consumer;


import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.extern.slf4j.Slf4j;
import org.java.codec.SSDecoder;
import org.java.codec.SSEncoder;
import org.java.compress.CompressionManager;
import org.java.handler.HeartbeatHandler;
import org.java.handler.TrafficRecordHandler;
import org.java.message.Response;
import org.java.register.ServiceMetadata;
import org.java.serialize.SerializerManager;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
public class ConnectionManager {

    private final Map<String, ChannelWrapper> channelTable = new ConcurrentHashMap<>();

    private final Bootstrap bootstrap;

    private final InFlightRequestManager inFlightRequestManager;

    private final ConsumerProperties properties;
    
    private final SerializerManager serializerManager;
    
    private final CompressionManager compressionManager;

    public ConnectionManager(InFlightRequestManager inFlightRequestManager, ConsumerProperties properties) {
        this.inFlightRequestManager = inFlightRequestManager;
        this.bootstrap = createBootstrap(properties);
        this.properties = properties;
        this.serializerManager = new SerializerManager();
        this.compressionManager = new CompressionManager();
    }


    public Channel getChannel(ServiceMetadata metadata) {
        String host = metadata.getHost();
        int port = metadata.getPort();
        String key = host + ":" + port;
        ChannelWrapper channelWrapper = channelTable.computeIfAbsent(key, (k) -> {
            try {
                ChannelFuture channelFuture = bootstrap.connect(host, port).sync();
                Channel channel = channelFuture.channel();
                channel.closeFuture().addListener((f) -> {
                    channelTable.remove(key);
                    inFlightRequestManager.clearChannel(metadata);
                });
                return new ChannelWrapper(channel);
            } catch (InterruptedException e) {
                log.error("连接超时{},{}", host, port, e);
                return new ChannelWrapper(null);
            }
        });
        Channel channel = channelWrapper.channel;
        if (channel == null || !channel.isActive()) {
            channelTable.remove(key);
            return null;
        }
        return channel;
    }

    private Bootstrap createBootstrap(ConsumerProperties consumerProperties) {
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(new NioEventLoopGroup(consumerProperties.getWorkThreadNum()))
            .channel(NioSocketChannel.class)
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, consumerProperties.getConnectTimeoutMs())
            .handler(new ChannelInitializer<NioSocketChannel>() {
                @Override
                protected void initChannel(NioSocketChannel nioSocketChannel) throws Exception {
                    nioSocketChannel.pipeline()
                        .addLast(new TrafficRecordHandler())
                        .addLast(new SSDecoder())
                        .addLast(new SSEncoder())
                        .addLast(new IdleStateHandler(30,5,0, TimeUnit.SECONDS))
                        .addLast(new HeartbeatHandler())
                        .addLast(new ConsumerHandler());
                }
            });
        return bootstrap;
    }

    private class ConsumerHandler extends SimpleChannelInboundHandler<Response> {

        @Override
        protected void channelRead0(ChannelHandlerContext channelHandlerContext, Response response) throws Exception {
            inFlightRequestManager.completeRequest(response.getRequestId(), response);
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

    private static class ChannelWrapper {
        final Channel channel;

        private ChannelWrapper(Channel channel) {
            this.channel = channel;
        }
    }

}
