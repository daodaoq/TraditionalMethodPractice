package org.java.consumer;

import org.java.api.Add;
import org.java.register.RegistryConfig;

import java.util.concurrent.CyclicBarrier;

public class ConsumerApp {

    public static void main(String[] args) throws Exception {
        RegistryConfig registryConfig = new RegistryConfig();
        registryConfig.setRegisterType("zookeeper");
        registryConfig.setConnectString("127.0.0.1:2181");
        ConsumerProperties consumerProperties = new ConsumerProperties();
        consumerProperties.setRegistryConfig(registryConfig);
        ConsumerProxyFactory consumerProxyFactory = new ConsumerProxyFactory(consumerProperties);
        Add addConsumer = consumerProxyFactory.createConsumerProxy(Add.class);
        CyclicBarrier cyclicBarrier = new CyclicBarrier(10);
        for (int i = 0; i < 100; i ++) {
            new Thread(() -> {
                try {
                    cyclicBarrier.await();
                    System.out.println(addConsumer.add(1, 2));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();
        }

    }
}
