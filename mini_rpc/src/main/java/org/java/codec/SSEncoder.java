package org.java.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.util.AttributeKey;
import lombok.extern.slf4j.Slf4j;
import org.java.compress.Compression;
import org.java.compress.CompressionManager;
import org.java.message.Message;
import org.java.serialize.Serializer;
import org.java.serialize.SerializerManager;
import org.java.version.Version;

@Slf4j
public class SSEncoder extends MessageToByteEncoder<Object> {

    public static final AttributeKey<String> SERIALIZE_KEY = AttributeKey.valueOf("serializeKey");
    public static final AttributeKey<SerializerManager> SERIALIZER_MANAGER_KEY = AttributeKey.valueOf(
        "serializeManagerKey");

    public static final AttributeKey<String> COMPRESS_KEY = AttributeKey.valueOf("compressKey");
    public static final AttributeKey<CompressionManager> COMPRESS_MANAGER_KEY = AttributeKey.valueOf(
        "compressManagerKey");

    private volatile Serializer defaultSerializer;
    private volatile Compression defaultCompression;
    private volatile byte defaultSerializerAndCompress;

    @Override
    protected void encode(ChannelHandlerContext ctx, Object msg, ByteBuf out) throws Exception {
        initIfNecessary(ctx);
        Message.MessageType messageType = Message.MessageType.ofClass(msg.getClass());
        if (messageType == null) {
            log.warn("{} 不支持序列化，无法发送", msg.getClass().getName());
            return;
        }
        byte[] magic = Message.MAGIC;
        byte messageCode = messageType.getCode();
        Version current = Version.V1;
        byte[] body = defaultSerializer.serialize(msg);
        byte finalSac = defaultSerializerAndCompress;
        if (body.length < 256) {
            finalSac &= (byte) 0b11110000;
        } else {
            body = defaultCompression.compress(body);
        }

        int length = magic.length + Byte.BYTES * 2 + Short.BYTES + body.length;
        out.writeInt(length);
        out.writeBytes(magic);
        out.writeByte(messageCode);
        out.writeShort(current.getVersionNum());
        out.writeByte(finalSac);
        out.writeBytes(body);
    }

    private void initIfNecessary(ChannelHandlerContext ctx) {
        if (defaultSerializer != null) {
            return;
        }
        String serializeKey = ctx.channel().attr(SERIALIZE_KEY).get();
        SerializerManager serializerManager = ctx.channel().attr(SERIALIZER_MANAGER_KEY).get();
        defaultSerializer = serializerManager.getSerializer(serializeKey);

        String compressKey = ctx.channel().attr(COMPRESS_KEY).get();
        CompressionManager compressionManager = ctx.channel().attr(COMPRESS_MANAGER_KEY).get();
        defaultCompression = compressionManager.getCompression(compressKey);

        if (defaultSerializer == null) {
            throw new IllegalArgumentException("不存在默认的序列化器");
        }
        if (defaultCompression == null) {
            throw new IllegalArgumentException("不存在默认的压缩器");
        }
        defaultSerializerAndCompress = (byte) ((defaultSerializer.code() << 4) | defaultCompression.code());
    }


}
