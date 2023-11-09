package de.dailab.jiacpp.platform.auth;

import java.util.*;

import javax.annotation.PostConstruct;

import de.dailab.jiacpp.platform.PlatformConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import de.dailab.jiacpp.platform.session.SessionData;


/**
 * The purpose of the TokenUserDetailsService class is to provide user details 
 * for authentication and authorization purposes in our Spring application. 
 */
@Service
public class TokenUserDetailsService implements UserDetailsService {

    @Autowired
    private SessionData sessionData;

    @Autowired
    private PlatformConfig config;


    private Map<String, String> userCredentials;

    private Map<String, Collection<Role>> userRoles;

    @PostConstruct
	public void postConstruct() {
		userCredentials = sessionData.userCredentials;
        userRoles = sessionData.userRoles;
        if (userCredentials.isEmpty() || userRoles.isEmpty()) {
            addUser(config.usernamePlatform, config.passwordPlatform, getDebugRole());
        }
	}

    /** Returns the user as a standardized 'User' object */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        if (userCredentials.containsKey(username)) {
            return new User(username, userCredentials.get(username), getAuthorities(userRoles.get(username)));
        } else {
            throw new UsernameNotFoundException("User not found: " + username);
        }
    }

    /**
     * Adding user to the credentials map. Those user credentials can be a human's credentials
     * as [username:password] or agent container credentials as [containerID:containerID].
     */
    public void addUser(String username, String password, Collection<Role> roles) {
        userCredentials.put(username, password);
        userRoles.put(username, roles);
    }

    public void removeUser(String username) {
        userCredentials.remove(username);
    }

    private Collection<? extends GrantedAuthority> getAuthorities(Collection<Role> roles) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        for (Role role : roles) {
            authorities.add(new SimpleGrantedAuthority(role.getName()));
            role.getPrivileges().stream().map(SimpleGrantedAuthority::new)
                    .forEach(authorities::add);
        }

        return authorities;
    }

    // This temp method is just for testing to get the admin role
    public Collection<Role> getDebugRole() {
        return Arrays.asList(new Role("ROLE_ADMIN", Arrays.asList("ADMIN_PRIVILEGE")));
    }
}
