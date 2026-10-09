package org.java.codec;

import com.alibaba.fastjson2.JSONObject;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import org.java.message.Message;
import org.java.message.Response;

import java.nio.charset.StandardCharsets;

public class ResponseEncoder extends MessageToByteEncoder<Response> {

    @Override
    protected void encode(ChannelHandlerContext ctx, Response response, ByteBuf out) throws Exception {
        byte[] logic = Message.LOGIC;
        byte messageType = Message.MessageType.RESPONSE.getCode();
        byte[] body = serializeResponse(response);
        int length = logic.length + Byte.BYTES + body.length;
        out.writeInt(length);
        out.writeBytes(logic);
        out.writeByte(messageType);
        out.writeBytes(body);
    }

    private byte[] serializeResponse(Response response) {
        return JSONObject.toJSONString(response).getBytes(StandardCharsets.UTF_8);
    }

}
