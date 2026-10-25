package org.java.register;

public class RegisterFactory {

    public static ServiceRegistry createServiceRegistry(RegistryConfig config) {
        if (config.getRegisterType().equals("zookeeper")) {
            return new ZookeeperServiceRegistry();
        }
        if (config.getRegisterType().equals("redis")) {
            return new RedisServiceRegistry();
        }
        throw new IllegalArgumentException(config.getRegisterType() + "没有实现");
    }
}
