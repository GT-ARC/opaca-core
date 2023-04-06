package de.dailab.jiacpp.plattform;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.UUID;
import java.util.List;
import de.dailab.jiacpp.plattform.LogEntry;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.dailab.jiacpp.plattform.LoggingHistory;


/**
 * This class provides the Logging Proxy. Whenever a API method is called, it is passed.
 * through the proxy, before getting executed.
 */

public class LoggingProxy<T> implements InvocationHandler {
    private final T target;

    public LoggingProxy(T target) {
        this.target = target;
    }

    // List for methods that should be skipped for log history, such as getHistory()
    public List<String> skipMethods = Arrays.asList("getHistory");

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {

        if (skipMethods.contains(method.getName())) {
            // Skip logging for this method
            return method.invoke(target, args);
        }

        LogEntry callEntry = new LogEntry();
        LogEntry resultEntry = new LogEntry();

        // entry for API call
        callEntry.setMethodName(method.getName());
        callEntry.setInputParams(args);
        callEntry.setEventType("APICall");

        // ID for mapping the related events
        String relatedId = UUID.randomUUID().toString();
        resultEntry.setRelatedId(relatedId);
        callEntry.setRelatedId(relatedId);


        Object result = null;
        try {
            result = method.invoke(target, args);

            // create a new LogEntry for the result
            resultEntry.setEventType("APIResult");
            resultEntry.setResult(result);

        } catch (InvocationTargetException e) {
            resultEntry.setResult(e.getCause().getMessage());
            resultEntry.setEventType("APIError");
            throw e.getCause();
        } catch (Exception e) {
            resultEntry.setResult(e.getMessage());
            resultEntry.setEventType("APIError");
            throw e;
        } finally {
            LoggingHistory.getInstance().addLogEntry(resultEntry);
            LoggingHistory.getInstance().addLogEntry(callEntry);
        }

        return result;
    }

    @SuppressWarnings("unchecked")
    public static <T> T create(T target) {
        return (T) Proxy.newProxyInstance(
                target.getClass().getClassLoader(),
                target.getClass().getInterfaces(),
                new LoggingProxy<>(target));
    }
}
