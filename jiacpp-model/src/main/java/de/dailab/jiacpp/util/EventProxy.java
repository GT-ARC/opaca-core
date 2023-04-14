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
 * This class provides the Event Proxy. Whenever an API method is called, it is passed
 * through the proxy, before getting executed.
 */
public class EventProxy<T> implements InvocationHandler {

    /** the object whose method invocations to log */
    private final T target;


    private EventProxy(T target) {
        this.target = target;
    }

    @SuppressWarnings("unchecked")
    public static <T> T create(T target) {
        return (T) Proxy.newProxyInstance(
                target.getClass().getClassLoader(),
                target.getClass().getInterfaces(),
                new EventProxy<>(target));
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {

        if (method.getName().startsWith("get")) {
            // Skip logging for this method
            return method.invoke(target, args);
        }

        // entry for API call
        Event callEvent = new Event();
        callEvent.setMethodName(method.getName());
        callEvent.setInputParams(args);
        callEvent.setEventType(Event.EventType.API_CALL);

        Event resultEvent = new Event();
        resultEvent.setRelatedId(callEvent.getUniqueId());

        try {
            Object result = method.invoke(target, args);

            // create a new Event for the result
            resultEvent.setEventType(Event.EventType.API_RESULT);
            resultEvent.setResult(result);

            return result;
        } catch (InvocationTargetException e) {
            resultEvent.setResult(e.getCause().getMessage());
            resultEvent.setEventType(Event.EventType.API_ERROR);
            throw e.getCause();
        } catch (Exception e) {
            resultEvent.setResult(e.getMessage());
            resultEvent.setEventType(Event.EventType.API_ERROR);
            throw e;
        } finally {
            EventHistory.getInstance().addEvent(resultEvent);
            EventHistory.getInstance().addEvent(callEvent);
        }
    }

}
