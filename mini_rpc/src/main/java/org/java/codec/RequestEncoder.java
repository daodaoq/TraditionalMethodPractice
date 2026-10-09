package org.java.codec;

import com.alibaba.fastjson2.JSONObject;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import org.java.message.Message;
import org.java.message.Request;

import java.nio.charset.StandardCharsets;

public class RequestEncoder extends MessageToByteEncoder<Request> {

    @Override
    protected void encode(ChannelHandlerContext ctx, Request request, ByteBuf out) throws Exception {
        byte[] logic = Message.LOGIC;
        byte messageType = Message.MessageType.REQUEST.getCode();
        byte[] body = serializeRequest(request);
        int length = logic.length + Byte.BYTES + body.length;
        out.writeInt(length);
        out.writeBytes(logic);
        out.writeByte(messageType);
        out.writeBytes(body);
    }

    private byte[] serializeRequest(Request request) {
        return JSONObject.toJSONString(request).getBytes(StandardCharsets.UTF_8);
    }

}
