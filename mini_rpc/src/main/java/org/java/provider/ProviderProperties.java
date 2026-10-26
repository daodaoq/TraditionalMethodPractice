package org.java.provider;

import lombok.Data;
import org.java.register.RegistryConfig;

@Data
public class ProviderProperties {

    private String host;
    private int port;
    private int workThreadNum = 4;
    private int globalMaxRequest = 10;
    private int preConsumerMaxRequest = 5;
    private String serialize = "json";
    private String compress = "none";
    private RegistryConfig registryConfig;
}
