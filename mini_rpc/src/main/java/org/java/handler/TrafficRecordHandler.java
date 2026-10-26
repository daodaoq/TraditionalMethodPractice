package org.java.handler;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.util.AttributeKey;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class TrafficRecordHandler extends ChannelDuplexHandler {

    public static final AttributeKey<TrafficRecord> RECORD_ATTRIBUTE_KEY = AttributeKey.valueOf("traffic_record");

    private TrafficRecord trafficRecord;

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof ByteBuf byteBuf) {
            trafficRecord.download.getAndAdd(byteBuf.readableBytes());
        }
        ctx.fireChannelRead(msg);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (msg instanceof ByteBuf byteBuf) {
            trafficRecord.upload.getAndAdd(byteBuf.readableBytes());
        }
        ctx.write(msg, promise);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        trafficRecord = new TrafficRecord();
        ctx.channel().eventLoop().scheduleAtFixedRate(() -> {
            System.out.printf("当前上行流量：%d 下行流量 %d %n", trafficRecord.upload.get(), trafficRecord.download.get());
        }, 5, 5, TimeUnit.SECONDS);
        ctx.channel().attr(RECORD_ATTRIBUTE_KEY).set(trafficRecord);
        ctx.fireChannelActive();
    }

    public static class TrafficRecord {
        AtomicLong upload = new AtomicLong();
        AtomicLong download = new AtomicLong();
    }
}
