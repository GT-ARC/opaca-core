package de.gtarc.opaca.platform;

import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.security.SecuritySchemes;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@SecuritySchemes({
        @SecurityScheme(
                name = "bearerAuth",
                type = SecuritySchemeType.HTTP,
                scheme = "bearer", 
                bearerFormat = "JWT"
        )
})
public class SwaggerConfig {

        @Bean
        public GroupedOpenApi allApi() {
            return GroupedOpenApi.builder()
                    .group("All")
                    .pathsToMatch("/**")
                    .build();
        }
            
}
