package de.dailab.jiacpp.platform;

import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.Jwts;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Date;

@Service
public class JwtUtil {

    private final TokenUserDetailsService tokenUserDetailsService;

    private final String SECRET_KEY = "secret";

    @Autowired
    public JwtUtil(TokenUserDetailsService tokenUserDetailsService) {
        this.tokenUserDetailsService = tokenUserDetailsService;
    }

    public String generateTokenForPlatform(String username, String password) {
        System.out.println("ÜBERHAUPT IN GENERATE TOKEN?");
        UserDetails userDetails;
        try {
            System.out.println(tokenUserDetailsService);
            userDetails = tokenUserDetailsService.loadUserByUsername(username);
            System.out.println("USERRETAILS");
            System.out.println(userDetails);
        } catch (UsernameNotFoundException e) {
            System.out.println("TOKEN CREATION FAILED");
            return null;
        }
        System.out.println(password);
        if (userDetails.getPassword().equals(password)) {
            System.out.println("Yes it is equal password");
            String token = Jwts.builder().setSubject(username).setIssuedAt(new Date(System.currentTimeMillis()))
                    .setExpiration(new Date(System.currentTimeMillis() + 1000 * 60 * 60 * 10)) // token valid for 10 hours
                    .signWith(SignatureAlgorithm.HS256, SECRET_KEY).compact();
            return token;
        } else {
            System.out.println("TOKEN CREATION FAILED");
            return null;
        }
    }

    public String generateTokenForAgentContainer(String username) {
        String token = Jwts.builder().setSubject(username).setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + 1000 * 60 * 60 * 10)) // token valid for 10 hours
                .signWith(SignatureAlgorithm.HS256, SECRET_KEY).compact();
        System.out.println(token);
        return token;
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
