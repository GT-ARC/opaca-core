package de.dailab.jiacpp.platform;

import io.jsonwebtoken.MalformedJwtException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Component
public class JwtRequestFilter extends OncePerRequestFilter {

    @Autowired
    private UserDetailsService jwtUserDetailsService;

    @Autowired
    JwtUtil jwtUtil;

    @Value("${security.enableJwt}")
    private boolean enableJwt;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestURL = request.getRequestURI();
        
        if (enableJwt && !isSwagger(requestURL) && !requestURL.contains("/login")) {
        
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
                handleException(response, HttpStatus.BAD_REQUEST,"Token is not valid.");
            }
    
            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
    
                UserDetails userDetails = this.jwtUserDetailsService.loadUserByUsername(username);
    
                if (jwtUtil.validateToken(jwtToken, userDetails)) {
                    UsernamePasswordAuthenticationToken usernamePasswordAuthenticationToken = new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities());
                    usernamePasswordAuthenticationToken
                            .setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(usernamePasswordAuthenticationToken);
                } else {
                    handleException(response, HttpStatus.UNAUTHORIZED, "Token is not valid."); 
                }
            }
        }
        chain.doFilter(request, response);
    }    
    private boolean isSwagger(String url) {
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