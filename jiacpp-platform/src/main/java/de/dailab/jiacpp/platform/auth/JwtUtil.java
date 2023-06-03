package de.dailab.jiacpp.platform.auth;

import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.Jwts;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.util.Date;

@Service
public class JwtUtil {

    @Autowired
    private TokenUserDetailsService tokenUserDetailsService;

    // TODO get from environment variable or generate random secret on each start up
    private final String SECRET_KEY = "secret";


    public String generateTokenForUser(String username, String password) {
        UserDetails userDetails = tokenUserDetailsService.loadUserByUsername(username);
        if (userDetails.getPassword().equals(password)) {
            return createToken(username, 60 * 60);  // token valid for 1 hour
        } else {
            throw new BadCredentialsException("Wrong password");
        }
    }

    public String generateTokenForAgentContainer(String containerId) {
        // TODO expiration date of 10 hours does not work for agent containers, should be able to run for weeks
        return createToken(containerId, 60 * 60 * 10);  // token valid for 10 hours
    }

    private String createToken(String username, long validSeconds) {
        return Jwts.builder().setSubject(username)
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + 1000 * validSeconds))
                .signWith(SignatureAlgorithm.HS256, SECRET_KEY).compact();
    }

    public boolean validateToken(String token, UserDetails userDetails) {
        return (getUsernameFromToken(token).equals(userDetails.getUsername()) &&
                getExpirationDateFromToken(token).after(new Date()));
    }

    public String getUsernameFromToken(String token) {
        return Jwts.parser().setSigningKey(SECRET_KEY).parseClaimsJws(token).getBody().getSubject();
    }

    private Date getExpirationDateFromToken(String token) {
        return Jwts.parser().setSigningKey(SECRET_KEY).parseClaimsJws(token).getBody().getExpiration();
    }

}
