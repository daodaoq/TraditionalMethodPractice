package org.java.breaker;

import org.java.consumer.ConsumerProperties;
import org.java.register.ServiceMetadata;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CircuitBreakerManager {
    
    private final Map<ServiceMetadata, CircuitBreaker> circuitBreakerMap = new ConcurrentHashMap<>();
    private final ConsumerProperties consumerProperties;

    public CircuitBreakerManager(ConsumerProperties consumerProperties) {
        this.consumerProperties = consumerProperties;
    }


    public CircuitBreaker createOrGetBreaker(ServiceMetadata metadata) {
        return circuitBreakerMap.computeIfAbsent(metadata, this::createBreaker);
    }

    private CircuitBreaker createBreaker(ServiceMetadata metadata) {
        return new ResponseTimeCircuitBreaker(consumerProperties.getSlowRequestBreakRatio(),consumerProperties.getSlowRequestMs());
    }
}
