package org.java;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 生成源码 → 编译 → 加载 → 创建对象
 */
public class MyInterfaceFactory {

    /**
     * createProxyObject(handler)
     *     |
     *     v
     * getClassName()  得到 "MyInterface$proxy1"
     *     |
     *     v
     * createJavaFile()  根据 handler.functionBody() 拼出源码，写 .java 文件
     *     |
     *     v
     * Compiler.compile()  编译成 .class
     *     |
     *     v
     * loadClass()  加载类
     *     |
     *     v
     * newInstance()  反射创建对象
     *     |
     *     v
     * handler.setProxy(proxy)  给 handler 一个钩子，可以反射操作代理对象
     *     |
     *     v
     * 返回代理对象
     */

    private static final AtomicInteger count = new AtomicInteger();

    /**
     * 生成 Java 源码
     */
    private static File createJavaFile(String className, MyHandler handler) throws IOException {
        String func1Body = handler.functionBody("func1");
        String func2Body = handler.functionBody("func2");
        String func3Body = handler.functionBody("func3");
        String context = "package org.java;\n" +
                "\n" +
                "public class " + className + " implements MyInterface {\n" +
                "MyInterface myInterface;\n" +
                "    @Override\n" +
                "    public void func1() {\n" +
                "       " + func1Body + "\n" +
                "    }\n" +
                "\n" +
                "    @Override\n" +
                "    public void func2() {\n" +
                "       " + func2Body + "\n" +
                "    }\n" +
                "\n" +
                "    @Override\n" +
                "    public void func3() {\n" +
                "       " + func3Body + "\n" +
                "    }\n" +
                "}\n";

        File javaFile = new File(className + ".java");
        Files.writeString(javaFile.toPath(), context);
        return javaFile;
    }

    private static String getClassName() {
        return "MyInterface$proxy" + count.incrementAndGet();
    }

    /**
     * 加载并创建对象
     */
    private static MyInterface newInstance(String className, MyHandler handler) throws Exception {
        // 把刚编译出来的类加载进 JVM
        Class<?> aClass = MyInterfaceFactory.class.getClassLoader().loadClass(className);
        // 拿无参构造器
        Constructor<?> constructor = aClass.getConstructor();
        // 创建对象，强转成 MyInterface
        MyInterface proxy = (MyInterface)  constructor.newInstance();
        // 调用 handler.setProxy(proxy)，给 handler 一个机会去操作这个代理对象
        handler.setProxy(proxy);
        return proxy;
    }

    public static MyInterface createProxyObject(MyHandler myHandler) throws Exception {
        String className = getClassName();
        File javaFile = createJavaFile(className, myHandler);
        Compiler.compile(javaFile);
        return newInstance("org.java." + className, myHandler);
    }
}
