package de.gtarc.opaca.platform;

import tools.jackson.databind.json.JsonMapper;
import de.gtarc.opaca.util.RestHelper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Jackson Configuration for e.g. resolving Dates and Times, or variants on nested objects.
 */
@Configuration
public class JacksonConfig {

    @Bean
    @Primary
    public JsonMapper jsonMapper() {
        return RestHelper.mapper;
    }
}
