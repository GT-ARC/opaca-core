package de.dailab.jiacpp.platform.auth;

import java.time.Duration;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;

import de.dailab.jiacpp.platform.Persistent;
import de.dailab.jiacpp.platform.PlatformConfig;

/**
 * The purpose of the TokenUserDetailsService class is to provide user details 
 * for authentication and authorization purposes in our Spring application. 
 */
@Service
public class TokenUserDetailsService implements UserDetailsService {

    private Persistent persistent;

    /** Returns the user as a standardized 'User' object */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        if (persistent.data.userCredentials.containsKey(username)) {
            return new User(username, persistent.data.userCredentials.get(username), List.of());
        } else {
            throw new UsernameNotFoundException("User not found: " + username);
        }
    }

    public void initialize(Persistent persistent) {
        this.persistent = persistent;
    }
    /**
     * Adding user to the credentials map. Those user credentials can be a human's credentials
     * as [username:password] or agent container credentials as [containerID:containerID].
     */
    public void addUser(String username, String password) {
        persistent.data.userCredentials.put(username, password);
    }

    public void removeUser(String username) {
        persistent.data.userCredentials.remove(username);
    }
}
