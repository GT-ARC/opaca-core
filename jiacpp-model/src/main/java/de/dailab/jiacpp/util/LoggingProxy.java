package de.dailab.jiacpp.util;

import de.dailab.jiacpp.model.Event;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.UUID;
import java.util.List;
import com.fasterxml.jackson.core.JsonProcessingException;


/**
 * This class provides the Logging Proxy. Whenever an API method is called, it is passed
 * through the proxy, before getting executed.
 */
public class LoggingProxy<T> implements InvocationHandler {

    /** the object whose method invocations to log */
    private final T target;

    /** List for methods that should be skipped for log history, such as getHistory() */
    public List<String> skipMethods = List.of("getHistory");


    private LoggingProxy(T target) {
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

        if (skipMethods.contains(method.getName())) {
            // Skip logging for this method
            return method.invoke(target, args);
        }

        Event callEvent = new Event();
        Event resultEvent = new Event();

        // entry for API call
        callEvent.setMethodName(method.getName());
        callEvent.setInputParams(args);
        callEvent.setEventType("APICall");

        // ID for mapping the related events
        String relatedId = UUID.randomUUID().toString();
        resultEvent.setRelatedId(relatedId);
        callEvent.setRelatedId(relatedId);

        Object result = null;
        try {
            result = method.invoke(target, args);

            // create a new Event for the result
            resultEvent.setEventType("APIResult");
            resultEvent.setResult(result);

        } catch (InvocationTargetException e) {
            resultEvent.setResult(e.getCause().getMessage());
            resultEvent.setEventType("APIError");
            throw e.getCause();
        } catch (Exception e) {
            resultEvent.setResult(e.getMessage());
            resultEvent.setEventType("APIError");
            throw e;
        } finally {
            LoggingHistory.getInstance().addEvent(resultEvent);
            LoggingHistory.getInstance().addEvent(callEvent);
        }

        return result;
    }

}
