package org.java.serialize;

import org.java.spi.Extension;

public interface Serializer extends Extension {

    byte[] serialize(Object object);

    <T> T deserialize(byte[] bytes, Class<T> objectClass);

}

