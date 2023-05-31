package de.dailab.jiacpp.platform;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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

@EnableWebSecurity
@Configuration
public class SecurityConfiguration extends WebSecurityConfigurerAdapter {

    private JwtRequestFilter jwtRequestFilter;

    @Autowired
    private UserDetailsService myUserDetailsService;


    @Autowired
    private JwtUtil jwtUtil;

    @Override
    protected void configure(AuthenticationManagerBuilder auth) throws Exception {
        auth.userDetailsService(myUserDetailsService);
    }

    @PostConstruct
    public void init() {
        if(enableJwt) {
            jwtRequestFilter = new JwtRequestFilter();
            jwtRequestFilter.setJwtUserDetailsService(myUserDetailsService);
            jwtRequestFilter.setJwtUtil(jwtUtil);
        }
    }
    
    @Value("${security.enableJwt}")
    private boolean enableJwt;

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        if (enableJwt) {
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
    
}
