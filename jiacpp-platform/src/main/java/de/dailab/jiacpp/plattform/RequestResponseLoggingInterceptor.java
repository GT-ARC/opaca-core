package de.dailab.jiacpp.plattform;

import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;


class RequestResponseLoggingInterceptor implements HandlerInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(RequestResponseLoggingInterceptor.class);


    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String requestSenderAddress = request.getRemoteAddr();
        String targetAddress = request.getRequestURL().toString();
        String endpoint = request.getRequestURI();
        String parameters = request.getParameterMap().toString();


        long timestamp = System.currentTimeMillis();
        logger.info(String.format("Request received from %s to %s:%s with method %s, parameters %s at %d",
                requestSenderAddress, targetAddress, endpoint, request.getMethod(), parameters, timestamp));
        return true;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {
        String requestSenderAddress = request.getRemoteAddr();
        String targetAddress = request.getRequestURL().toString();
        String endpoint = request.getRequestURI();
        String parameters = request.getParameterMap().toString();
        long timestamp = System.currentTimeMillis();
        logger.info(String.format("Response sent to %s from %s:%s with method %s, parameters %s, status %d at %d",
                requestSenderAddress, targetAddress, endpoint, request.getMethod(), parameters, response.getStatus(), timestamp));
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // No action needed
    }

}