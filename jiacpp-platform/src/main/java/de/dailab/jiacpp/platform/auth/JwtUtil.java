package de.dailab.jiacpp.platform.auth;

import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.Jwts;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Date;

@Service
public class JwtUtil {

    @Autowired
    private TokenUserDetailsService tokenUserDetailsService;

    // TODO get from environment variable or generate random secret on each start up
    private final String SECRET_KEY = "secret";


    public String generateTokenForUser(String username, String password) {
        UserDetails userDetails;
        try {
            userDetails = tokenUserDetailsService.loadUserByUsername(username);
        } catch (UsernameNotFoundException e) {
            throw e;
        }
        if (userDetails.getPassword().equals(password)) {
            return Jwts.builder().setSubject(username).setIssuedAt(new Date(System.currentTimeMillis()))
                    .setExpiration(new Date(System.currentTimeMillis() + 1000 * 60 * 60 * 10)) // token valid for 10 hours
                    .signWith(SignatureAlgorithm.HS256, SECRET_KEY).compact();
        } else {
            throw new AuthException("wrong password", null);
        }
    }

    public String generateTokenForAgentContainer(String username) {
        // TODO expiration date of 10 hours does not work for agent containers, should be able to run for weeks
        return Jwts.builder().setSubject(username).setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + 1000 * 60 * 60 * 10)) // token valid for 10 hours
                .signWith(SignatureAlgorithm.HS256, SECRET_KEY).compact();
    }

    public String getUsernameFromToken(String token) {
        return Jwts.parser().setSigningKey(SECRET_KEY).parseClaimsJws(token).getBody().getSubject();
    }

    public boolean validateToken(String token, UserDetails userDetails) {
        final String username = getUsernameFromToken(token);
        return (username.equals(userDetails.getUsername()) && !isTokenExpired(token));
    }

    private Boolean isTokenExpired(String token) {
        final Date expiration = getExpirationDateFromToken(token);
        return expiration.before(new Date());
    }

    private Date getExpirationDateFromToken(String token) {
        return Jwts.parser().setSigningKey(SECRET_KEY).parseClaimsJws(token).getBody().getExpiration();
    }

}
