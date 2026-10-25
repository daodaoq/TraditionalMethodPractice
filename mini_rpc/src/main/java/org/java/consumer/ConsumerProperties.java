package org.java.consumer;

import lombok.Data;
import org.java.register.RegistryConfig;

@Data
public class ConsumerProperties {
    private Integer workThreadNum = 4;
    private Integer connectTimeoutMs = 3000;
    private Integer requestTimeoutMs = 3000;
    private Integer methodTimeoutMs = 30000;
    private String loadBalancePolicy = "robin";
    private String retryPolicy = "forking";
    private String serialize = "json";
    private String compress = "none";
    private int rpcPreSecond =  100;
    private int rpcPreChannel = 50;
    private double slowRequestBreakRatio = 0.5;
    private long slowRequestMs = 1000;
    private RegistryConfig registryConfig = new RegistryConfig();
}
