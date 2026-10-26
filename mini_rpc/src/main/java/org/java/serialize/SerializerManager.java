package org.java.serialize;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.ServiceLoader;

public class SerializerManager {

    private final Map<Integer, Serializer> codeMap = new HashMap<>();
    private final Map<String, Serializer> nameMap = new HashMap<>();

    public SerializerManager() {
        init();
    }

    public Serializer getSerializer(int typeCode) {
        return codeMap.get(typeCode);
    }

    public Serializer getSerializer(String name) {
        return nameMap.get(name.toUpperCase(Locale.ROOT));
    }

    private void init() {
        ServiceLoader<Serializer> loader = ServiceLoader.load(Serializer.class);
        for (Serializer serializer : loader) {
            if (codeMap.put(serializer.code(),serializer) != null) {
                throw new IllegalArgumentException("序列化器的code重复");
            }
            if (serializer.code() >= 16) {
                throw new IllegalArgumentException("序列化器code不能超过15");
            }
            if (nameMap.put(serializer.getName().toUpperCase(Locale.ROOT),serializer) != null) {
                throw new IllegalArgumentException("序列化器的名字重复");
            }
        }
    }
}
