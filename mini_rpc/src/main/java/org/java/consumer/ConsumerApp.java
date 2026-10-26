package org.java.consumer;

import org.java.api.Add;
import org.java.api.User;
import org.java.register.RegistryConfig;

import java.util.HashMap;
import java.util.Map;

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

        GenericConsumer genericConsumer = consumerProxyFactory.createConsumerProxy(GenericConsumer.class);
        System.out.println(genericConsumer.$invoke(Add.class.getName(), "add", new String[]{"int", "int"},
            new Object[]{4, 5}));
        //  aaa rpc + auth --->  gateway/proxy --> provider
        Map<String,Object> user1 = new HashMap<>();
        user1.put("age",1);
        user1.put("name","张三");
        Map<String,Object> user2 = new HashMap<>();
        user2.put("age",3);
        user2.put("name","李四");

        System.out.println(genericConsumer.$invoke(Add.class.getName(), "mergeAge", new String[]{User.class.getName(), User.class.getName()},
            new Object[]{user1, user2}));
    }
}
