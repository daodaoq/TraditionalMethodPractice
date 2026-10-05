package org.java;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ApplicationContext {

    /**
     * new ApplicationContext("org.java")
     *     |
     *     v
     * initContext("org.java")
     *     |
     *     v
     * scanpackage("org.java")
     *     |
     *     v
     * 得到很多 Class
     *     |
     *     v
     * filter 只留下带 @Component 的 Class
     *     |
     *     v
     * wrapper：Class -> BeanDefinition
     *     |
     *     v
     * 放入 beanDefinitionMap
     *     |
     *     v
     * createBean
     *     |
     *     v
     * doCreateBean
     *     |
     *     v
     * constructor.newInstance() 创建对象
     *     |
     *     v
     * 如果有 @PostConstruct，调用它
     *     |
     *     v
     * 放入 ioc
     *     |
     *     v
     * getBean 从 ioc 里取对象
     */


    public ApplicationContext(String packageName) throws IOException, URISyntaxException {
        initContext(packageName);
    }

    /**
     * 存 beanDefinition
     */
    private Map<String, BeanDefinition> beanDefinitionMap = new HashMap<>();

    /**
     * 存 bean 对象
     */
    private Map<String, Object> ioc = new HashMap<>();

    /**
     * 存未初始化完成的 bean 对象
     */
    private Map<String, Object> loadingIoc = new HashMap<>();

    /**
     * 存 BeanPostProcessor
     */
    private List<BeanPostProcessor> postProcessors = new ArrayList<>();

    /**
     * 扫描包，拿到所有 Class
     * 过滤出带 @Component 的 Class
     * 把每个 Class 包装成 BeanDefinition，再创建 Bean
     */
    public void initContext(String packageName) throws IOException, URISyntaxException {
        scanPackage(packageName)
                .stream()
                .filter(this::canCreate)
                .forEach(this::wrapper);
        initBeanPostProcessor();
        beanDefinitionMap
                .values()
                .forEach(this::createBean);
        // ApplicationContext.class.getClassLoader().getResource("")
    }

    private void initBeanPostProcessor() {
        beanDefinitionMap.values().stream()
                .filter(bd -> BeanPostProcessor.class.isAssignableFrom(bd.getBeanType()))
                .map(this::createBean)
                .map((bean) -> (BeanPostProcessor) bean)
                .forEach(postProcessors::add);
    }

    protected boolean canCreate(Class<?> type) {
        // 只有类上直接标了 @Component，才会被创建
        return type.isAnnotationPresent(Component.class);
    }

    protected Object createBean(BeanDefinition beanDefinition) {
        String name = beanDefinition.getName();
        if (ioc.containsKey(name)) {
            return ioc.get(name);
        }
        if (loadingIoc.containsKey(name)) {
            return loadingIoc.get(name);
        }
        return doCreateBean(beanDefinition);
    }

    private Object doCreateBean(BeanDefinition beanDefinition) {
        // 从 BeanDefinition 拿出构造器
        Constructor<?> constructor = beanDefinition.getConstructor();
        Object bean = null;
        try {
            // constructor.newInstance() 反射创建对象
            bean = constructor.newInstance();
            loadingIoc.put(beanDefinition.getName(), bean);
            autowiredBean(bean, beanDefinition);
            bean = initializeBean(bean, beanDefinition);
            loadingIoc.remove(beanDefinition.getName());
            // 把创建好的对象放进 ioc
            ioc.put(beanDefinition.getName(), bean);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return bean;
    }

    private Object initializeBean(Object bean, BeanDefinition beanDefinition) throws InvocationTargetException, IllegalAccessException {
        for (BeanPostProcessor postProcessor : postProcessors) {
            bean = postProcessor.beforeInitializeBean(bean, beanDefinition.getName());
        }

        // 如果有 @PostConstruct 方法，就调用它
        Method postConstructMethod = beanDefinition.getPostConstructMethod();
        if (postConstructMethod != null) {
            postConstructMethod.invoke(bean);
        }

        for (BeanPostProcessor postProcessor : postProcessors) {
            bean = postProcessor.afterInitializeBean(bean, beanDefinition.getName());
        }

        return bean;
    }

    private void autowiredBean(Object bean, BeanDefinition beanDefinition) throws IllegalAccessException {
        for (Field autowiredField : beanDefinition.getAutowiredFields()) {
            autowiredField.setAccessible(true);
            autowiredField.set(bean, getBean(autowiredField.getType()));
        }
    }

    /**
     * 包装成 BeanDefinition
     * 这里还没有创建对象，只是把 Class 转成 BeanDefinition，然后放进 beanDefinitionMap
     */
    protected BeanDefinition wrapper(Class<?> type) {
        BeanDefinition beanDefinition = new BeanDefinition(type);
        if (beanDefinitionMap.containsKey(beanDefinition.getName())) {
            throw new RuntimeException("bean 名字重复");
        }
        beanDefinitionMap.put(beanDefinition.getName(), beanDefinition);
        return beanDefinition;
    }

    private List<Class<?>> scanPackage(String packageName) throws IOException, URISyntaxException {
        List<Class<?>> classList = new ArrayList<>();
        // 把包名 org.java 转成路径 org/java
        URL resource = this.getClass().getClassLoader().getResource(packageName.replace(".", File.separator));
        // 找到这个目录
        Path path = Path.of(resource.toURI());
        // 递归遍历目录下所有 .class 文件
        Files.walkFileTree(path, new SimpleFileVisitor<>(){
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path absolutePath = file.toAbsolutePath();
                // 把 .class 文件路径转成全限定类名
                if (absolutePath.toString().endsWith(".class")) {
                    String replaceStr = absolutePath.toString().replace(File.separator, ".");
                    int packageIndex = replaceStr.lastIndexOf(packageName);
                    String className = replaceStr.substring(packageIndex, replaceStr.length() - ".class".length());
                    try {
                        // 用 Class.forName(className) 加载类
                        classList.add(Class.forName(className));
                    } catch (ClassNotFoundException e) {
                        throw new RuntimeException(e);
                    }
                }
                return FileVisitResult.CONTINUE;
            }
        });
        // 返回 List<Class<?>>
        return classList;
    }

    public Object getBean(String name){
        if (name == null) {
            return null;
        }
        Object bean =  this.ioc.get(name);
        if (bean != null) {
            return bean;
        }
        if (beanDefinitionMap.containsKey(name)) {
            return createBean(beanDefinitionMap.get(name));
        }
        return null;
    }

    public <T> T getBean(Class<T> beanType) {
        String beanName = this.beanDefinitionMap.values().stream()
                .filter(bd -> beanType.isAssignableFrom(bd.getBeanType()))
                .map(BeanDefinition::getName)
                .findFirst()
                .orElse(null);
        return (T) getBean(beanName);
    }

    public <T> List<T> getBeans(Class<T> beanType) {
        return this.beanDefinitionMap.values().stream()
                .filter(bd -> beanType.isAssignableFrom(bd.getBeanType()))
                .map(BeanDefinition::getName)
                .map(this::getBean)
                .map((bean) -> (T) bean)
                .toList();
    }
}
