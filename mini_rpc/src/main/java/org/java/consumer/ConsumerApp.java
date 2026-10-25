package org.java.consumer;

import org.java.api.Add;
import org.java.register.RegistryConfig;

public class ConsumerApp {

    public static void main(String[] args) throws Exception {
        RegistryConfig registryConfig = new RegistryConfig();
        registryConfig.setRegisterType("zookeeper");
        registryConfig.setConnectString("127.0.0.1:2181");
        ConsumerProperties consumerProperties = new ConsumerProperties();
        consumerProperties.setRegistryConfig(registryConfig);
        ConsumerProxyFactory consumerProxyFactory = new ConsumerProxyFactory(consumerProperties);
        Add addConsumer = consumerProxyFactory.createConsumerProxy(Add.class);
        System.out.println(addConsumer.add(1, 2));
        System.out.println(addConsumer.add(11, 22));
        System.out.println(addConsumer.add(1, 2));
        System.out.println(addConsumer.add(11, 22));
        System.out.println(addConsumer.add(1, 2));
        System.out.println(addConsumer.add(11, 22));
        System.out.println(addConsumer.add(1, 2));
        System.out.println(addConsumer.add(11, 22));
        System.out.println(addConsumer.add(1, 2));
        System.out.println(addConsumer.add(11, 22));

    }
}
