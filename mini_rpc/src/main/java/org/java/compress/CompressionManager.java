package org.java.compress;

import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.ServiceLoader;

@Slf4j
public class CompressionManager {

    private final Map<Integer, Compression> codeMap = new HashMap<>();
    private final Map<String, Compression> nameMap = new HashMap<>();

    public CompressionManager() {
        init();
    }

    public Compression getCompression(int typeCode) {
        return codeMap.get(typeCode);
    }

    public Compression getCompression(String name) {
        return nameMap.get(name.toUpperCase(Locale.ROOT));
    }

    private void init() {
        for (Compression compression : ServiceLoader.load(Compression.class)) {
            if (codeMap.put(compression.code(),compression) != null) {
                throw new IllegalArgumentException("压缩器的code重复");
            }
            if (compression.code() >= 16) {
                throw new IllegalArgumentException("压缩器code不能超过15");
            }
            if (nameMap.put(compression.getName().toUpperCase(Locale.ROOT),compression) != null) {
                throw new IllegalArgumentException("压缩器的名字重复");
            }
        }
    }
}
