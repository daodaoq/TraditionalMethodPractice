package org.java.message;

import lombok.Data;

import java.nio.charset.StandardCharsets;

@Data
public class Message {

    // 魔数，标识这个消息是我们协议的消息
    public static final byte[] LOGIC = "生生".getBytes(StandardCharsets.UTF_8);

    private byte[] logic;

    private byte messageType;

    private byte[] body;

    public enum MessageType {
        REQUEST(1), RESPONSE(2);

        private final byte code;

        MessageType(int code) {
            this.code = (byte) code;
        }

        public byte getCode() {
            return code;
        }
    }
}
