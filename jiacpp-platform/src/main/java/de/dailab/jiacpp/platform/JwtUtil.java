package de.dailab.jiacpp.platform;

import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.Jwts;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Date;


public class JwtUtil {
    
    final String username;
    final String password;

    public JwtUtil (String username, String password) {
        System.out.println(username);
        System.out.println(password);
        this.username = username;
        this.password = password;
    }
    
    private final String SECRET_KEY = "secret";

    public String generateToken(String username, String password) {
        System.out.println("generate token");
        System.out.println(username);
        System.out.println(password);
        System.out.println(this.username);
        System.out.println(this.password);
        if (this.username.equals(username) && this.password.equals(password)) {
            return Jwts.builder().setSubject(username).setIssuedAt(new Date(System.currentTimeMillis()))
                    .setExpiration(new Date(System.currentTimeMillis() + 1000 * 60 * 60 * 10)) // token valid for 10 hours
                    .signWith(SignatureAlgorithm.HS256, SECRET_KEY).compact();
        } else {
            System.out.println("TOKEN CREATION FAILED");
            return null;
        }
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
