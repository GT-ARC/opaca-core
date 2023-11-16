package de.dailab.jiacpp.platform.auth;

import java.util.*;

import jakarta.annotation.PostConstruct;

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


    private Map<String, TokenUser> tokenUsers;
    private String currentUser;

    @PostConstruct
	public void postConstruct() {
		tokenUsers = sessionData.tokenUsers;
        if (tokenUsers.isEmpty()) {
            addUser(config.usernamePlatform, config.passwordPlatform,
                    Arrays.asList(new Role("ROLE_" + config.rolePlatform)));
            currentUser = config.usernamePlatform;
        }
	}

    /** Returns the user as a standardized 'User' object */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        if (tokenUsers.containsKey(username)) {
            return new User(username, tokenUsers.get(username).getPassword(),
                    getAuthorities(tokenUsers.get(username).getRoles()));
        } else {
            throw new UsernameNotFoundException("User not found: " + username);
        }
    }

    /**
     * Adding users to the set of tokenUsers. Users can be human [username, password, roles]
     * or containers [containerID, containerID, roles]
     * If a User already exists, throw an exception
     */
    public void addUser(String username, String password, Collection<Role> roles) {
        if (tokenUsers.containsKey(username)) {
            throw new UserAlreadyExistsException(username);
        }
        else {
            TokenUser user = new TokenUser(username, password, roles);
            tokenUsers.put(username, user);
        }
    }

    public void removeUser(String username) {
        tokenUsers.remove(username);
    }

    public Collection<Role> getCurrentUserRoles() {
        return tokenUsers.get(currentUser).getRoles();
    }

    /**
     * Return the granted authorities of the current user.
     * @return
     */
    public Collection<? extends GrantedAuthority> getCurrentUserAuthorities() {
        return getAuthorities(tokenUsers.get(currentUser).getRoles());
    }

    /**
     * Returns the roles and privileges as granted authorities based on the given
     * collection of roles.
     */
    private Collection<? extends GrantedAuthority> getAuthorities(Collection<Role> roles) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        for (Role role : roles) {
            authorities.add(new SimpleGrantedAuthority(role.getName()));
            if (role.getPrivileges() != null) {
                role.getPrivileges().stream().map(SimpleGrantedAuthority::new)
                        .forEach(authorities::add);
            }
        }

        return authorities;
    }

    // This temp method is just for testing to get the admin role
    public Collection<Role> getDebugRole() {
        return Arrays.asList(new Role("ROLE_ADMIN", Arrays.asList("ADMIN_PRIVILEGE")));
    }

    /**
     * Exception thrown during user creation if a given user already exists
     */
    static class UserAlreadyExistsException extends RuntimeException {
        public UserAlreadyExistsException(String username) {
            super("User with username '" + username + "' already exists!");
        }
    }
}
