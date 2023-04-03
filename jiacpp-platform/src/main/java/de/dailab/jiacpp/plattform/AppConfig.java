package de.dailab.jiacpp.plattform;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class AppConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new RequestResponseLoggingInterceptor());
    }

    @Bean
    public InterceptorRegistry interceptorRegistry() {
        return new InterceptorRegistry();
    }



}
