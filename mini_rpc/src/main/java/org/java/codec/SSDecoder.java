package org.java.codec;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONReader;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import org.java.message.Message;
import org.java.message.Request;
import org.java.message.Response;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

import static java.nio.charset.StandardCharsets.UTF_8;

public class SSDecoder extends LengthFieldBasedFrameDecoder {

    public SSDecoder() {
        /**
         * maxFrameLength	    1MB	最大帧长
         * lengthFieldOffset	0	长度字段从第 0 字节开始
         * lengthFieldLength	4	长度字段占 4 字节（int）
         * lengthAdjustment	    0	长度字段的值就是整个帧剩余部分的长度
         * initialBytesToStrip	4	解码后把前 4 字节长度字段剥掉
         */
        super(1024 * 1024, 0, Integer.BYTES, 0, Integer.BYTES);
    }

    @Override
    protected Object decode(ChannelHandlerContext ctx, ByteBuf in) throws Exception {
        // super.decode() 返回的 frame 已经是一个“只包含当前这一条完整消息”的 ByteBuf 了
        // frame = logic + type + body
        ByteBuf frame = (ByteBuf) super.decode(ctx, in);
        byte[] logic = new byte[Message.LOGIC.length];
        // 读走 logic，readerIndex += logic.length
        frame.readBytes(logic);
        if (!Arrays.equals(logic, Message.LOGIC)) {
            throw new IllegalArgumentException("魔数不对！协议有问题");
        }
        // 读走 type，readerIndex += 1
        byte messageType = frame.readByte();
        // 剩下的长度为 body 的长度
        byte[] body = new byte[frame.readableBytes()];
        frame.readBytes(body);
        if (Objects.equals(Message.MessageType.REQUEST.getCode(), messageType)) {
            // 反序列化成 Request 对象
            return deserializeRequest(body);
        }
        if (Objects.equals(Message.MessageType.RESPONSE.getCode(), messageType)) {
            // 反序列化成 Response 对象
            return deserializeResponse(body);
        }
        throw new IllegalArgumentException("消息类型不支持"+messageType);
    }

    private Request deserializeRequest(byte[] body) {
        return JSONObject.parseObject(new String(body, StandardCharsets.UTF_8), Request.class, JSONReader.Feature.SupportClassForName);
    }

    private Response deserializeResponse(byte[] body) {
        return JSONObject.parseObject(new String(body, StandardCharsets.UTF_8), Response.class);
    }
}
