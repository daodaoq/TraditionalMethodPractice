package org.java.provider;

import org.java.api.Add;
import org.java.register.RegistryConfig;

public class ProviderApp {
    public static void main(String[] args) {
        RegistryConfig registryConfig = new RegistryConfig();
        registryConfig.setRegisterType("zookeeper");
        registryConfig.setConnectString("127.0.0.1:2181");
        ProviderServer providerServer = new ProviderServer("127.0.0.1", 8888, registryConfig);
        providerServer.register(Add.class, new AddImpl());
        providerServer.start();
    }
}
