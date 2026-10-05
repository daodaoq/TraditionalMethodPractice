package org.java;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

/**
 * BeanDefinition 可以理解成 Bean 的说明书 / 图纸。
 * 它不存对象本身，而是存：
 * Bean 叫什么名字
 * 用哪个构造器创建
 * 有没有 @PostConstruct 初始化方法
 */
public class BeanDefinition {

    private final String name;
    private final Constructor<?> constructor;
    private final Method postConstructMethod;
    private final List<Field> autowiredFields;
    private final Class<?> beanType;

    public BeanDefinition(Class<?> type) {
        this.beanType = type;
        Component component = type.getAnnotation(Component.class);
        this.name = component.name().isEmpty() ? type.getSimpleName() : component.name();
        try {
            this.constructor = type.getConstructor();
            this.postConstructMethod =
                    Arrays.stream(type.getDeclaredMethods())
                            .filter(m -> m.isAnnotationPresent(PostConstruct.class))
                            .findFirst()
                            .orElse(null);
            this.autowiredFields =
                    Arrays.stream(type.getDeclaredFields())
                            .filter(f -> f.isAnnotationPresent(Autowired.class))
                            .toList();
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

    public List<Field> getAutowiredFields() {
        return autowiredFields;
    }

    public Class<?> getBeanType() {
        return beanType;
    }
}
