package de.dailab.jiacpp.platform.auth;

import de.dailab.jiacpp.platform.PlatformConfig;
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
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;


import io.jsonwebtoken.MalformedJwtException;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * The SecurityConfiguration class is a configuration class for enabling and configuring authentication for the Spring
 * application. The inner class JwtRequestFilter is the filter that is applied to ensure that only authenticated and
 * authorized users are allowed for requesting the platform.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

    @Autowired
    private UserDetailsService myUserDetailsService;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private PlatformConfig config;

    /*
    The security filter chain establishes the required permissions for a user to have
    in order to access the specified routes. The swagger ui routes, along with "/login"
    and "/error", are always permitted.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        if (config.enableAuth) {
            JwtRequestFilter jwtRequestFilter = new JwtRequestFilter();
            http
                    .csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests((auth) -> auth
                            .requestMatchers(
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
                            ).permitAll()
                            .requestMatchers(HttpMethod.GET, "/info", "/agents/**", "/containers/**").hasRole("GUEST")
                            .requestMatchers(HttpMethod.GET, "/history", "/connections", "/stream/**").hasRole("USER")
                            .requestMatchers(HttpMethod.POST, "/send/**", "/invoke/**", "/broadcast/**").hasRole("USER")
                            .requestMatchers(HttpMethod.POST, "/containers/**").hasRole("CONTRIBUTOR")
                            .requestMatchers(HttpMethod.DELETE, "/containers/**").hasRole("CONTRIBUTOR")
                            .requestMatchers("/connections/**").hasRole("ADMIN")
                            .anyRequest().authenticated()
                    )
                    .sessionManagement((session) -> session
                            .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                    )
                    .addFilterBefore(jwtRequestFilter, UsernamePasswordAuthenticationFilter.class);
        }
        else {
            http
                    .csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests((auth) -> auth
                            .anyRequest().permitAll()
                    );
        }
        return http.build();
    }

    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration)
            throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public RoleHierarchy roleHierarchy() {
        RoleHierarchyImpl roleHierarchy = new RoleHierarchyImpl();
        String hierarchy = "ROLE_ADMIN > ROLE_CONTRIBUTOR \n ROLE_CONTRIBUTOR > ROLE_USER \n ROLE_USER > ROLE_GUEST";
        roleHierarchy.setHierarchy(hierarchy);
        return roleHierarchy;
    }

    public class JwtRequestFilter extends OncePerRequestFilter {

        /* Here is the filter defined that is applied to all REST routes */
        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            String requestURI = request.getRequestURI();

            // TODO check exact match of path, not contained in URL

            /* Some routes, such as /login and those related to the Swagger UI,
             * are not included in the token authorization process.
             * /login is used to generate a token and Swagger UI should
             * always be accessible, to perform actions such as /login.
             */
            if (! isSwagger(requestURI) && ! requestURI.equals("/login")) {

                final String requestTokenHeader = request.getHeader("Authorization");
                String username = null;
                String jwtToken = null;
                if (requestTokenHeader != null && requestTokenHeader.startsWith("Bearer ")) {
                    jwtToken = requestTokenHeader.substring(7);
                    try {
                        username = jwtUtil.getUsernameFromToken(jwtToken);
                    } catch (IllegalArgumentException e) {
                        handleException(response, HttpStatus.UNAUTHORIZED, e.getMessage());
                    } catch (MalformedJwtException e) {
                        handleException(response, HttpStatus.BAD_REQUEST, e.getMessage());
                    }
                } else {
                    handleException(response, HttpStatus.BAD_REQUEST, "Missing Token.");
                }

                if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                    UserDetails userDetails = myUserDetailsService.loadUserByUsername(username);
                    if (jwtUtil.validateToken(jwtToken, userDetails)) {
                        var authToken = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authToken);
                    } else {
                        handleException(response, HttpStatus.UNAUTHORIZED, "Invalid Token.");
                    }
                }
            }
            chain.doFilter(request, response);
        }

        /* Check whether endpoint is related to the Swagger UI */
        private boolean isSwagger(String url) {
            // TODO (almost) same list in SecurityConfiguration -> extract to some constant?
            return url.contains("/swagger-resources")
                    || url.contains("/v2/api-docs")
                    || url.contains("/swagger-resources/")
                    || url.contains("/configuration/ui")
                    || url.contains("/configuration/security")
                    || url.contains("/swagger-ui.html")
                    || url.contains("/webjars/")
                    || url.contains("/v3/api-docs/")
                    || url.contains("/swagger-ui/");
        }

        private void handleException(HttpServletResponse response, HttpStatus status, String message)
                throws IOException {
            response.setStatus(status.value());
            response.getWriter().write(message);
        }
    }
}
