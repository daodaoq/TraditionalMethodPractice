package org.java.retry;

import lombok.extern.slf4j.Slf4j;
import org.java.spi.Spi;

import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;

@Slf4j
public class RetryPolicyManager {
    public RetryPolicyManager() {
        init();
    }

    private final Map<String, RetryPolicy> nameMap = new HashMap<>();

    public RetryPolicy getRetryPolicy(String name) {
        return nameMap.get(name);
    }

    private void init() {
        for (RetryPolicy retryPolicy : ServiceLoader.load(RetryPolicy.class)) {
            Class<? extends RetryPolicy> aClass = retryPolicy.getClass();
            if (!aClass.isAnnotationPresent(Spi.class)) {
                log.warn("这个类{}没有SPI注解,无法被管理", aClass.getName());
                continue;
            }
            Spi spi = aClass.getAnnotation(Spi.class);
            nameMap.put(spi.value(), retryPolicy);
        }
    }
}
