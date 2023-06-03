package de.dailab.jiacpp.platform.auth;

import io.jsonwebtoken.MalformedJwtException;

import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNullApi;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.constraints.NotNull;
import java.io.IOException;

import lombok.Setter;

public class JwtRequestFilter extends OncePerRequestFilter {
    
    @Setter
    private UserDetailsService jwtUserDetailsService;

    @Setter
    private JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestURL = request.getRequestURI();

        // TODO check exact match of path, not contained in URL
        if (! isSwagger(requestURL) && ! requestURL.contains("/login")) {

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
        // TODO (almost) same list in SecurityConfiguration -> extract to some constant?
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