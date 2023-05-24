package de.dailab.jiacpp.platform;

import io.jsonwebtoken.ExpiredJwtException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
import java.util.Enumeration;

@Component
public class JwtRequestFilter extends OncePerRequestFilter {

    @Autowired
    private UserDetailsService jwtUserDetailsService;

    @Autowired
    JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        System.out.println("doFilterInteral jwtUtil");
        System.out.println(jwtUtil);
        String requestURL = request.getRequestURI();
        System.out.println("______________");
        Enumeration<String> headerNames = request.getHeaderNames();
        while(headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            System.out.println("Header Name: " + headerName + ", Value: " + request.getHeader(headerName));
        }
    
        if (!isSwagger(requestURL) && !requestURL.contains("/login") ) {
        
            final String requestTokenHeader = request.getHeader("Authorization");
            System.out.println(("Request Token Header: " + requestTokenHeader));
            String username = null;
            String jwtToken = null;
            if (requestTokenHeader != null && requestTokenHeader.startsWith("Bearer ")) {
                jwtToken = requestTokenHeader.substring(7);
                System.out.println("JWT Token: " + jwtToken);
                System.out.println("Extracted Username: " + username);
                try {
                    username = jwtUtil.getUsernameFromToken(jwtToken);
                } catch (IllegalArgumentException e) {
                    System.out.println("Unable to get JWT Token");
                } catch (ExpiredJwtException e) {
                    System.out.println("JWT Token has expired");
                }
            } else {
                logger.warn("JWT Token does not begin with Bearer String");
            }
    
            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
    
                UserDetails userDetails = this.jwtUserDetailsService.loadUserByUsername(username);
    
                if (jwtUtil.validateToken(jwtToken, userDetails)) {
                    UsernamePasswordAuthenticationToken usernamePasswordAuthenticationToken = new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities());
                    usernamePasswordAuthenticationToken
                            .setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(usernamePasswordAuthenticationToken);
                    System.out.println("Token Validated and User Authentication Set in Context");
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
}

