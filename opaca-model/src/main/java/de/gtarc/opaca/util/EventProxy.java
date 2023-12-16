package de.gtarc.opaca.util;

import de.gtarc.opaca.model.Event;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;


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
        // read-only routes like "info" or "history" itself should not generate events
        if (method.getName().startsWith("get")) {
            // also, get-methods should return null, not "No Such Element", so no need for Ex-handling here
            return method.invoke(target, args);
        }

        // entry for API call
        Event callEvent = createCallEvent(method.getName(), args);
        addEvent(callEvent);

        try {
            Object result = method.invoke(target, args);
            addEvent(createResultEvent(callEvent, result));
            return result;
        } catch (InvocationTargetException e) { // wraps exception raised by target method
            addEvent(createErrorEvent(callEvent, e.getCause().getMessage()));
            throw e.getCause();
        } catch (ReflectiveOperationException e) { // should never happen
            addEvent(createErrorEvent(callEvent, e.getMessage()));
            throw e;
        }
    }

    private void addEvent(Event event) {
        EventHistory.getInstance().addEvent(event);
    }

    private Event createCallEvent(String method, Object[] params) {
        return new Event(Event.EventType.API_CALL, method, params, null, null);
    }

    private Event createResultEvent(Event related, Object result) {
        return new Event(Event.EventType.API_RESULT, null, null, result, related.getId());
    }

    private Event createErrorEvent(Event related, Object error) {
        return new Event(Event.EventType.API_ERROR, null, null, error, related.getId());
    }

}
