package de.gtarc.opaca.platform.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * This class is just exposing the password encoder, since creating
 * it in the security configuration lead to a circular dependency
 * between the tokenUserDetailService and JwtUtil
 */
@Component
public class SecurityUtil {
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
