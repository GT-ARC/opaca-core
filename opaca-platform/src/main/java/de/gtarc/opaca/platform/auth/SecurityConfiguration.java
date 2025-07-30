package de.gtarc.opaca.platform.auth;

import de.gtarc.opaca.model.Role;
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
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;


import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.SignatureException;

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
import java.util.stream.Stream;

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
        if (config.enableAuth) {
            http
                    .csrf(CsrfConfigurer::disable)
                    .authorizeHttpRequests((auth) -> auth
                            // some routes, like those related to OpenAPI and login, should work without Authentication
                            .requestMatchers(noAuthRoutes).permitAll()
                            // The next block implements the RBAC defined in the user-management docs
                            // A rule consists of a specific or generic (/**) route, the lowest role level
                            // to access the route (see role hierarchy), and the optional REST method
                            // the route is requested with (if none given, all methods are concerned)
                            .requestMatchers(HttpMethod.GET, "/users").hasRole(Role.ADMIN.name())
                            .requestMatchers(HttpMethod.GET, "/info", "/agents/**", "/containers/**", "/users/**").hasRole(Role.GUEST.name())
                            .requestMatchers(HttpMethod.GET, "/history", "/connections", "/stream/**").hasRole(Role.USER.name())
                            .requestMatchers(HttpMethod.POST, "/send/**", "/invoke/**", "/broadcast/**", "/stream/**").hasRole(Role.USER.name())
                            .requestMatchers(HttpMethod.POST, "/containers/**").hasRole(Role.CONTRIBUTOR.name())
                            .requestMatchers(HttpMethod.DELETE, "/containers/**").hasRole(Role.CONTRIBUTOR.name())
                            .requestMatchers("/connections/**", "/users/**").hasRole(Role.ADMIN.name())
                            .anyRequest().authenticated()
                    )
                    .sessionManagement((session) -> session
                            .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                    )
                    .addFilterBefore(new JwtRequestFilter(), UsernamePasswordAuthenticationFilter.class);
        } else {
            http
                    .csrf(CsrfConfigurer::disable)
                    .authorizeHttpRequests((auth) -> auth
                            .anyRequest().permitAll()
                    );
        }
        return http.build();
    }

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

    public class JwtRequestFilter extends OncePerRequestFilter {

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            String requestURI = request.getRequestURI();
            String requestTokenHeader = request.getHeader("Authorization");

            if (Stream.of(noAuthRoutes).noneMatch(s -> requestURI.startsWith(s.replace("**", "")))) {
                String username = null;
                String jwtToken = null;
                if (requestTokenHeader != null && requestTokenHeader.startsWith("Bearer ")) {
                    jwtToken = requestTokenHeader.substring(7);
                    try {
                        username = jwtUtil.getUsernameFromToken(jwtToken);
                    } catch (SignatureException | IllegalArgumentException e) {
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
                        jwtUtil.setCurrentRequestUser(username);
                    } else {
                        handleException(response, HttpStatus.UNAUTHORIZED, "Invalid Token.");
                    }
                }
            }
            chain.doFilter(request, response);
        }

        private void handleException(HttpServletResponse response, HttpStatus status, String message)
                throws IOException {
            response.setStatus(status.value());
            response.getWriter().write(message);
        }
    }
}
