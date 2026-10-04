package org.java;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * BeanDefinition 可以理解成 Bean 的说明书 / 图纸。
 * 它不存对象本身，而是存：
 * Bean 叫什么名字
 * 用哪个构造器创建
 * 有没有 @PostConstruct 初始化方法
 */
public class BeanDefinition {

    private String name;
    private Constructor<?> constructor;
    private Method postConstructMethod;

    public BeanDefinition(Class<?> type) {
        Component component = type.getAnnotation(Component.class);
        this.name = component.name().isEmpty() ? type.getSimpleName() : component.name();
        try {
            this.constructor = type.getConstructor();
            this.postConstructMethod =
                    Arrays.stream(type.getDeclaredMethods())
                            .filter(m -> m.isAnnotationPresent(PostConstruct.class))
                            .findFirst()
                            .orElse(null);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    public String getName() {
        return name;
    }

    public Constructor<?> getConstructor() {
        return constructor;
    }

    public Method getPostConstructMethod() {
        return postConstructMethod;
    }
}
