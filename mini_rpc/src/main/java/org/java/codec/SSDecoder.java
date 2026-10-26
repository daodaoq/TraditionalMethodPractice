package org.java.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.util.AttributeKey;
import org.java.compress.Compression;
import org.java.compress.CompressionManager;
import org.java.message.Message;
import org.java.serialize.Serializer;
import org.java.serialize.SerializerManager;

import java.util.Arrays;

public class SSDecoder extends LengthFieldBasedFrameDecoder {

    public static final AttributeKey<SerializerManager> SERIALIZER_MANAGER_KEY = AttributeKey.valueOf(
        "serializeManagerKey");

    public static final AttributeKey<CompressionManager> COMPRESS_MANAGER_KEY = AttributeKey.valueOf(
        "compressManagerKey");

    private volatile SerializerManager serializerManager;
    private volatile CompressionManager compressionManager;

    public SSDecoder() {
        super(1024 * 1024, 0, Integer.BYTES, 0, Integer.BYTES);
    }

    @Override
    protected Object decode(ChannelHandlerContext ctx, ByteBuf in) throws Exception {
        initIfNecessary(ctx);
        ByteBuf frame = (ByteBuf) super.decode(ctx, in);
        if (frame == null) {
            return null;
        }
        try {
            byte[] magic = new byte[Message.MAGIC.length];
            frame.readBytes(magic);
            if (!Arrays.equals(magic, Message.MAGIC)) {
                throw new IllegalArgumentException("魔数不对！协议有问题");
            }
            byte messageType = frame.readByte();
            short version = frame.readShort();
            byte serializerAndCompress = frame.readByte();
            Compression compression = this.compressionManager.getCompression(serializerAndCompress & 0b00001111);
            if (compression == null) {
                throw new IllegalArgumentException("没有支持的压缩器");
            }
            Serializer serializer = this.serializerManager.getSerializer((serializerAndCompress & 0b11110000) >>> 4);
            if (serializer == null) {
                throw new IllegalArgumentException("没有支持的序列化器");
            }
            byte[] body = new byte[frame.readableBytes()];
            frame.readBytes(body);

            body = compression.decompress(body);
            Message.MessageType type = Message.MessageType.ofCode(messageType);
            if (type == null) {
                throw new IllegalArgumentException("不支持的消息类型" + messageType);
            }
            return serializer.deserialize(body, type.getMessageClass());
        } finally {
            frame.release();
        }
    }

    private void initIfNecessary(ChannelHandlerContext ctx) {
        if(serializerManager != null){
            return;
        }
        serializerManager = ctx.channel().attr(SERIALIZER_MANAGER_KEY).get();
        compressionManager = ctx.channel().attr(COMPRESS_MANAGER_KEY).get();    
    }
}
