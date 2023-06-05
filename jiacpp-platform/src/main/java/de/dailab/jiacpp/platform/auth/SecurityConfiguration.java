package de.dailab.jiacpp.platform.auth;

import de.dailab.jiacpp.platform.PlatformConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;


import io.jsonwebtoken.MalformedJwtException;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/*
 * The SecurityConfiguration class is a configuration class for enabling and configuring authentification for our Spring application. 
 * The inner class JwtRequestFilter is the filter that is applied to ensure that only authentificated/authorized users are 
 * allowed for requesting the platform.
 */
@EnableWebSecurity
public class SecurityConfiguration extends WebSecurityConfigurerAdapter {

    @Autowired
    private UserDetailsService myUserDetailsService;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private PlatformConfig config;

    @Override
    protected void configure(AuthenticationManagerBuilder auth) throws Exception {
        auth.userDetailsService(myUserDetailsService);
    }

    /* Configure the filter applied to the REST routes */
    @Override
    protected void configure(HttpSecurity http) throws Exception {
        if (config.enableAuth) {
            JwtRequestFilter jwtRequestFilter = new JwtRequestFilter();
            http.csrf().disable()
                    .authorizeRequests()
                    .antMatchers("/v2/api-docs",
                            "/swagger-resources",
                            "/swagger-resources/**",
                            "/swagger-ui/**",
                            "/v3/api-docs/**",
                            "/login",
                            "/configuration/ui",
                            "/configuration/security",
                            "/swagger-ui.html",
                            "/webjars/**").permitAll()
                    .anyRequest().authenticated().and()
                    .exceptionHandling().and().sessionManagement()
                    .sessionCreationPolicy(SessionCreationPolicy.STATELESS);
            http.addFilterBefore(jwtRequestFilter, UsernamePasswordAuthenticationFilter.class);
        } else {
            http.csrf().disable() 
                .authorizeRequests()
                .antMatchers("/**").permitAll();
        }
    }

    @Override
    @Bean
    public AuthenticationManager authenticationManagerBean() throws Exception {
        return super.authenticationManagerBean();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
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
