package de.gtarc.opaca.platform.auth;

import de.gtarc.opaca.model.User.Role;
import de.gtarc.opaca.platform.PlatformConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;


/**
 * The SecurityConfiguration class is a configuration class for enabling and configuring authentication for the Spring
 * application. The inner class JwtRequestFilter is the filter that is applied to ensure that only authenticated and
 * authorized users are allowed for requesting the platform.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

    @Autowired
    private JwtConverter jwtConverter;

    @Autowired
    private PlatformConfig config;

    private final String[] noAuthRoutes = {
            "/v2/api-docs",
            "/swagger-resources",
            "/swagger-resources/**",
            "/swagger-ui/**",
            "/v3/api-docs/**",
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
        http
                // no sessions / stateless; no CSRF necessary
                .sessionManagement((session) -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(CsrfConfigurer::disable)
                // JWT access tokens using OAuth2/Keycloak
                .oauth2ResourceServer(oauth2 ->
                        oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtConverter))
                );
        if (config.requireAuth) {
            // role-based access control if auth is required
            http.authorizeHttpRequests((auth) -> auth
                    // some routes, like those related to OpenAPI and login, should work without Authentication
                    // (except for the OpenAPI route giving insight into the agents' actions)
                    .requestMatchers(HttpMethod.GET, "/v3/api-docs/actions").hasRole(Role.GUEST.name())
                    .requestMatchers(noAuthRoutes).permitAll()
                    // The next block implements the RBAC defined in the user-management docs
                    .requestMatchers(HttpMethod.GET, "/info", "/agents/**", "/containers/**").hasRole(Role.GUEST.name())
                    .requestMatchers(HttpMethod.GET, "/history", "/connections", "/stream/**").hasRole(Role.USER.name())
                    .requestMatchers(HttpMethod.POST, "/send/**", "/invoke/**", "/broadcast/**", "/stream/**").hasRole(Role.USER.name())
                    .requestMatchers(HttpMethod.POST, "/containers/login/**", "/containers/logout/**").hasRole(Role.USER.name())
                    .requestMatchers(HttpMethod.POST, "/containers/**").hasRole(Role.CONTRIBUTOR.name())
                    .requestMatchers(HttpMethod.DELETE, "/containers/**").hasRole(Role.CONTRIBUTOR.name())
                    .requestMatchers("/connections/**").hasRole(Role.ADMIN.name())
                    .anyRequest().authenticated()
            );
        } else {
            // permit-all if no auth required (but still enable auth)
            http.authorizeHttpRequests((auth) -> auth
                    .anyRequest().permitAll()
            );
        }
        return http.build();
    }

    /*
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration)
            throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public RoleHierarchy roleHierarchy() {
        RoleHierarchyImpl roleHierarchy = new RoleHierarchyImpl();
        String hierarchy = Role.ADMIN.role() + " > " + Role.CONTRIBUTOR.role() + " \n " +
                           Role.CONTRIBUTOR.role() + " > " + Role.USER.role() + " \n " +
                           Role.USER.role() + " > " + Role.GUEST.role();
        roleHierarchy.setHierarchy(hierarchy);
        return roleHierarchy;
    }
*/
}
