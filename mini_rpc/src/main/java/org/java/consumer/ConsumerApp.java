package org.java.consumer;

import org.java.api.Add;

public class ConsumerApp {

    public static void main(String[] args) throws Exception {
        Add consumer = new Consumer();
        System.out.println(consumer.add(1, 2));
        System.out.println(consumer.add(11, 22));
    }
}
