package org.java.provider;

import org.java.api.Add;
import org.java.register.RegistryConfig;

public class ProviderApp {
    public static void main(String[] args) {
        RegistryConfig registryConfig = new RegistryConfig();
        registryConfig.setRegisterType("zookeeper");
        registryConfig.setConnectString("127.0.0.1:2181");
        ProviderProperties properties = new ProviderProperties();
        properties.setHost("127.0.0.1");
        properties.setPort(8894);
        properties.setRegistryConfig(registryConfig);
        ProviderServer providerServer = new ProviderServer(properties);
        providerServer.register(Add.class, new AddImpl());
        providerServer.start();
    }
}
