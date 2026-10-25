package org.java.consumer;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import lombok.extern.slf4j.Slf4j;

import io.netty.channel.Channel;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class ConnectionManager {

    private final Map<String, ChannelWrapper> channelTable = new ConcurrentHashMap<>();

    private final Bootstrap bootstrap;

    public  ConnectionManager(Bootstrap bootstrap) {
        this.bootstrap = bootstrap;
    }

    public Channel getChannel(String host, int port) {
        String key = host + ":" + port;
        ChannelWrapper channelWrapper = channelTable.computeIfAbsent(key, (k) -> {
            try {
                ChannelFuture channelFuture = bootstrap.connect(host, port).sync();
                Channel channel = channelFuture.channel();
                channel.closeFuture().addListener((f) -> channelTable.remove(key));
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

    private static class ChannelWrapper {
        final Channel channel;

        private ChannelWrapper(Channel channel) {
            this.channel = channel;
        }
    }
}
