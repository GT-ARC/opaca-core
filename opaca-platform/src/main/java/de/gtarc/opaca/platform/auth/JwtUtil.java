package de.gtarc.opaca.platform.auth;

import de.gtarc.opaca.model.Role;
import de.gtarc.opaca.platform.user.TokenUserDetailsService;
import de.gtarc.opaca.platform.PlatformConfig;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.Jwts;

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
    private TokenUserDetailsService tokenUserDetailsService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    public String generateTokenForUser(String username, String password) {
        UserDetails userDetails = tokenUserDetailsService.loadUserByUsername(username);
        if (passwordEncoder.matches(password, userDetails.getPassword())) {
            return createToken(username, Duration.ofHours(1));
        } else {
            throw new BadCredentialsException("Wrong password");
        }
    }

    public String generateTokenForAgentContainer(String containerId) {
        // TODO expiration date of 10 hours does not work for agent containers, should be able to run for weeks
        return createToken(containerId, Duration.ofHours(10));
    }

    private String createToken(String username, Duration duration) {
        return Jwts.builder().setSubject(username)
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

    /**
     * Checks if the current request user is either an admin (has full control over user management)
     * or the request user is performing request on its own data
     * @param token: The token belonging to a user in the database for whom to check their authorities
     * @param username: Name of user which will get affected by request (NOT THE CURRENT REQUEST USER)
     */
    public boolean isAdminOrSelf(String token, String username) {
        UserDetails details = tokenUserDetailsService.loadUserByUsername(getUsernameFromToken(token));
        if (details == null) return false;
        return details.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals(Role.ADMIN.role())) ||
                details.getUsername().equals(username);
    }

}
