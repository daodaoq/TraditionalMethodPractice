package org.java.sub;

import org.java.Autowired;
import org.java.Component;
import org.java.PostConstruct;

@Component
public class Cat {

    @Autowired
    private Dog dog;

    @PostConstruct
    public void init() {
        System.out.println("cat 创建了 cat 里面有一个属性" + dog);
    }
}
