package de.dailab.jiacpp.platform;

import java.util.ArrayList;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class MyUserDetailsService implements UserDetailsService {

    @Value("${username_platform}")
    private String usernamePlatform;

    @Value("${password_platform}")
    private String passwordPlatform;
    
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        if (this.usernamePlatform.equals(username)) {
            return new User(username, passwordPlatform, new ArrayList<>());
        }
        throw new UsernameNotFoundException("User not found");
    }
}
