package org.java.provider;

import org.java.api.Add;
import org.java.register.RegistryConfig;

public class ProviderApp {
    public static void main(String[] args) {
        RegistryConfig registryConfig = new RegistryConfig();
        registryConfig.setRegisterType("zookeeper");
        registryConfig.setConnectString("127.0.0.1:2181");
        ProviderProperties providerProperties = new ProviderProperties();
        providerProperties.setHost("127.0.0.1");
        providerProperties.setPort(8888);
        providerProperties.setRegistryConfig(registryConfig);
        ProviderServer providerServer = new ProviderServer(providerProperties);
        providerServer.register(Add.class, new AddImpl());
        providerServer.start();
    }
}
