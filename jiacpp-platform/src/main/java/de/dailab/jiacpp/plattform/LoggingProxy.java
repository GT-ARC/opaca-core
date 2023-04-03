package de.dailab.jiacpp.plattform;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;

public class LoggingProxy<T> implements InvocationHandler {
    private final T target;

    public LoggingProxy(T target) {
        this.target = target;
    }

    @SuppressWarnings("unchecked")
    public static <T> T create(T target) {

        return (T) Proxy.newProxyInstance(
                target.getClass().getClassLoader(),
                target.getClass().getInterfaces(),
                new LoggingProxy<>(target));
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        System.out.println("Method called: " + method.getName());
        System.out.println("Input: " + Arrays.toString(args));
        Object result = method.invoke(target, args);
        System.out.println("Output: " + result);
        return result;
    }
}
