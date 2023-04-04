package de.dailab.jiacpp.plattform;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.List;
import de.dailab.jiacpp.plattform.LogEntry;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.dailab.jiacpp.plattform.LoggingContext;


public class LoggingProxy<T> implements InvocationHandler {
    private final T target;

    public LoggingProxy(T target) {
        this.target = target;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        LogEntry logEntry = new LogEntry();
        logEntry.setMethodName(method.getName());
        logEntry.setInputParams(args);

        Object result = null;
        try {
            result = method.invoke(target, args);
            logEntry.setResult(result);
            logEntry.setSuccess(true);
        } catch (InvocationTargetException e) {
            logEntry.setSuccess(false);
            logEntry.setErrorMessage(e.getCause().getMessage());
            throw e.getCause();
        } catch (Exception e) {
            logEntry.setSuccess(false);
            logEntry.setErrorMessage(e.getMessage());
            throw e;
        } finally {
            LoggingContext.getInstance().addLogEntry(logEntry);
        }

        // Trigger the function to print the log object as JSON
        printLogEntryJson();

        return result;
    }


    private void printLogEntryJson() {
        ObjectMapper objectMapper = new ObjectMapper();
        List<LogEntry> logEntries = LoggingContext.getInstance().getLogEntries();
        try {
            String json = objectMapper.writeValueAsString(logEntries);
            System.out.println(json);
        } catch (JsonProcessingException e) {
            System.out.println("Error converting log entries to JSON: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> T create(T target) {
        return (T) Proxy.newProxyInstance(
                target.getClass().getClassLoader(),
                target.getClass().getInterfaces(),
                new LoggingProxy<>(target));
    }
}
