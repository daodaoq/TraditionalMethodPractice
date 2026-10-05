package org.java.sub;

import org.java.Autowired;
import org.java.Component;
import org.java.PostConstruct;

@Component(name = "mydog")
public class Dog {

    @Autowired
    Cat cat;

    @Autowired
    Dog dog;

    @PostConstruct
    public void init() {
        System.out.println("dog 创建完成了，里面有一只猫" + cat);
        System.out.println("dog 创建完成了，里面有一只够" + dog);
    }
}
