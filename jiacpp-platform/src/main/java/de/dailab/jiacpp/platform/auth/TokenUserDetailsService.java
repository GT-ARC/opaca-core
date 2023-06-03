package de.dailab.jiacpp.platform.auth;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class TokenUserDetailsService implements UserDetailsService {

    private final Map<String, String> userCredentials = new HashMap<>();


    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        if (userCredentials.containsKey(username)) {
            return new User(username, userCredentials.get(username), List.of());
        } else {
            throw new UsernameNotFoundException("User not found: " + username);
        }
    }

    public void addUser(String username, String password) {
        userCredentials.put(username, password);
    }

    public void removeUser(String username) {
        userCredentials.remove(username);
    }
}
