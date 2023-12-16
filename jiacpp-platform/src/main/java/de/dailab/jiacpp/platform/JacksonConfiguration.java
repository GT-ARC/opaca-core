package de.dailab.jiacpp.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.gtarc.opaca.util.RestHelper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

/**
 * Jackson Configuration for e.g. resolving Dates and Times, or variants on nested objects.
 */
@Configuration
public class JacksonConfiguration {

    @Bean
    @Primary
    public ObjectMapper objectMapper2(Jackson2ObjectMapperBuilder builder) {
        return RestHelper.mapper;
    }
}
