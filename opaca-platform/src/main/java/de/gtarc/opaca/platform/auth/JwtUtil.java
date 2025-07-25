package de.gtarc.opaca.platform.auth;

import de.gtarc.opaca.model.Role;
import de.gtarc.opaca.platform.user.TokenUserDetailsService;
import de.gtarc.opaca.platform.PlatformConfig;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.Jwts;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Date;

/**
 * The JwtUtil class is a class responsible for generating and validating 
 * JSON Web Tokens (JWT) for authentication and authorization purposes. It provides 
 * methods for generating tokens for users and agent containers, as well as 
 * validating tokens against user details.
 * It also stores the username from the last successful authentication attempt.
 */
@Service
public class JwtUtil {
    
    @Autowired
    private PlatformConfig config;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // TODO This is a very ugly workaround to access the current user making a request
    //  and could lead to a race condition. The user should be extracted directly in the controller
    @Getter @Setter
    private String currentRequestUser;


    public String generateToken(String subject, Duration duration) {
        return Jwts.builder().setSubject(subject)
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + duration.toMillis()))
                .signWith(SignatureAlgorithm.HS256, config.secret).compact();
    }

    public boolean validateToken(String token, UserDetails userDetails) {
        return (getUsernameFromToken(token).equals(userDetails.getUsername()) &&
                getExpirationDateFromToken(token).after(new Date()));
    }

    public String getUsernameFromToken(String token) {
        return Jwts.parser().setSigningKey(config.secret).parseClaimsJws(token).getBody().getSubject();
    }

    private Date getExpirationDateFromToken(String token) {
        return Jwts.parser().setSigningKey(config.secret).parseClaimsJws(token).getBody().getExpiration();
    }

}
