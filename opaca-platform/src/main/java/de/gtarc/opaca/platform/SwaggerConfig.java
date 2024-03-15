package de.gtarc.opaca.platform;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.security.SecuritySchemes;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(info=@Info(
        title = "OPACA Runtime Platform",
        version = "0.2",
        description = """
                Use this Web API to interact with the OPACA Runtime Platform and its Agent Containers. Here's a short
                description of the different groups of API routes:
                * **users**: view, add or delete user accounts for this platform
                * **agents**: interact with agents inside the containers, e.g. by sending them messages, invoking actions
                * **authentication**: login to request an access token
                * **containers**: view, deploy or remove Agent Containers running on this platform
                * **connections**: view, add or remove other Runtime Platforms connected to this platform
                * **info**: get basic information on this Runtime Platform and its properties
                """
))
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
    public GroupedOpenApi platformApi() {
        return GroupedOpenApi.builder()
                .group("Runtime Platform")
                .pathsToMatch("/containers/**", "/connections/**")
                .build();
    }

    @Bean
    public GroupedOpenApi agentsApi() {
        return GroupedOpenApi.builder()
                .group("Agents")
                .pathsToMatch("/send/**", "/invoke/**", "/broadcast/**", "/stream/**", "/agents/**")
                .build();
    }

    @Bean
    public GroupedOpenApi otherApi() {
        return GroupedOpenApi.builder()
                .group("Other")
                .pathsToMatch("/users/**", "/authentication/**", "/info", "/history", "/config")
                .build();
    }

    // last one here is shown when loading the page
    @Bean
    public GroupedOpenApi allApi() {
        return GroupedOpenApi.builder()
                .group("All")
                .pathsToMatch("/**")
                .build();
    }
            
}
