package de.dailab.jiacpp.platform.auth;

import org.springframework.security.core.AuthenticationException;

public class AuthException extends AuthenticationException {

    public AuthException(String message, Exception cause){
        super(message, cause);
        // TODO bessere namen und an attribute binden
    }
    
}
