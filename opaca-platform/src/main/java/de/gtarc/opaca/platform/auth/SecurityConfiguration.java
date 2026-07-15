package de.gtarc.opaca.platform.auth;

import de.gtarc.opaca.platform.PlatformConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;


/**
 * The SecurityConfiguration class is a configuration class for enabling and configuring authentication for the Spring
 * application. The users are managed and JWTs are created by KeyCloak. Users have access to different routes based on
 * their roles. The roles and their hierarchy have to be defined in Keycloak; see Documentation (auth.md) for details.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

    @Autowired
    private JwtConverter jwtConverter;

    @Autowired
    private PlatformConfig config;

    private final String[] noAuthRoutes = {
            "/v3/api-docs",
            "/v3/api-docs/**",
            "/swagger-resources",
            "/swagger-resources/**",
            "/swagger-ui/**",
            "/login",
            "/error",
            "/configuration/ui",
            "/configuration/security",
            "/swagger-ui.html",
            "/webjars/**"
    };

    /**
     * The security filter chain establishes the required permissions for a user to have
     * in order to access the specified routes. The swagger ui routes, along with "/login"
     * and "/error", are always permitted.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        Customizer<AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry> authPolicy = config.requireAuth
                // Role-Based Access Control if auth is required
                ? auth -> auth
                    // some routes, like those related to OpenAPI and login, should work without Authentication
                    // (except for the OpenAPI route giving insight into the agents' actions)
                    .requestMatchers(HttpMethod.GET, "/v3/api-docs/actions").hasRole(AuthUtils.ROLE_GUEST)
                    .requestMatchers(noAuthRoutes).permitAll()
                    // The next block implements the RBAC defined in the user-management docs
                    .requestMatchers(HttpMethod.GET, "/info", "/agents/**", "/containers/**").hasRole(AuthUtils.ROLE_GUEST)
                    .requestMatchers(HttpMethod.GET, "/history", "/connections", "/stream/**").hasRole(AuthUtils.ROLE_USER)
                    .requestMatchers(HttpMethod.POST, "/send/**", "/invoke/**", "/broadcast/**", "/stream/**").hasRole(AuthUtils.ROLE_USER)
                    .requestMatchers("/mcp", "/mcp/**").hasRole(AuthUtils.ROLE_USER)
                    .requestMatchers(HttpMethod.POST, "/containers/login/**", "/containers/logout/**").hasRole(AuthUtils.ROLE_USER)
                    .requestMatchers(HttpMethod.POST, "/containers/notify").hasRole(AuthUtils.ROLE_USER)
                    .requestMatchers(HttpMethod.POST, "/containers/**").hasRole(AuthUtils.ROLE_CONTRIBUTOR)
                    .requestMatchers(HttpMethod.DELETE, "/containers/**").hasRole(AuthUtils.ROLE_CONTRIBUTOR)
                    .requestMatchers("/connections/notify").hasRole(AuthUtils.ROLE_USER)
                    .requestMatchers("/connections/**").hasRole(AuthUtils.ROLE_MANAGER)
                    .anyRequest().authenticated()
                // no auth required -> permit all (but still path JWT tokens)
                : auth -> auth.anyRequest().permitAll();

        // no sessions / stateless; no CSRF necessary
        http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        http.csrf(CsrfConfigurer::disable);
        if (config.isSet(config.keycloakIssuerUri)) {
            // JWT access tokens using OAuth2/Keycloak
            http.oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtConverter)));
        }
        // authorization rules with required-auth and without
        http.authorizeHttpRequests(authPolicy);
        return http.build();
    }

    @Bean
    public RoleHierarchy roleHierarchy() {
        RoleHierarchyImpl roleHierarchy = new RoleHierarchyImpl();
        roleHierarchy.setHierarchy("""
                ROLE_MANAGER > ROLE_CONTRIBUTOR
                ROLE_CONTRIBUTOR > ROLE_USER
                ROLE_USER > ROLE_GUEST
                """);
        return roleHierarchy;
    }
}
