package org.java;

/**
 * 决定每个方法里要写什么代码
 */
public interface MyHandler {

    /**
     * 返回一段Java 代码字符串，这段代码会被塞进生成的 func1() 方法体里
     */
    String functionBody(String methodName);

    /**
     * 可选钩子，代理对象创建完后会调用它，用来做一些“事后注入”，比如把字段塞进去
     */
    default void setProxy(MyInterface proxy) {

    }
}
