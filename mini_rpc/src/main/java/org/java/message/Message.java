package org.java.message;

import lombok.Data;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Data
public class Message {
    public static final byte[] MAGIC = "生生".getBytes(StandardCharsets.UTF_8);

    private byte[] magic;

    private byte messageType;

    private short version;

    private byte serializeAndCompress;

    private byte[] body;

    public enum MessageType {
        REQUEST(1, Request.class), 
        RESPONSE(2, Response.class),
        HEARTBEAT_REQUEST(3,HeartbeatRequest.class),
        HEARTBEAT_RESPONSE(4,HeartbeatResponse.class);

        private static final Map<Class<?>, MessageType> CLASS_CACHE = new HashMap<>();
        private static final Map<Byte, MessageType> CODE_CACHE = new HashMap<>();
        private final byte code;
        private final Class<?> messageClass;

        static {
            for (MessageType value : values()) {
                if (CLASS_CACHE.put(value.messageClass, value) != null) {
                    throw new IllegalArgumentException(value + "没有唯一对应消息类");
                }
                if (CODE_CACHE.put(value.code, value) != null) {
                    throw new IllegalArgumentException(value + "没有唯一数字码");
                }
            }
        }

        MessageType(int code, Class<?> messageClass) {
            this.code = (byte) code;
            this.messageClass = messageClass;
        }

        public byte getCode() {
            return code;
        }

        public Class<?> getMessageClass() {
            return messageClass;
        }

        public static MessageType ofClass(Class<?> messageClass) {
            return CLASS_CACHE.get(messageClass);
        }
        public static MessageType ofCode(Byte messageCode) {
            return CODE_CACHE.get(messageCode);
        }


    }
}
