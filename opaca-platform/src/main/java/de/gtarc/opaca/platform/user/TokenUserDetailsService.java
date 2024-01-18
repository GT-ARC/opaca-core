package de.gtarc.opaca.platform.user;

import java.util.*;
import java.util.stream.Collectors;

import jakarta.annotation.PostConstruct;

import de.gtarc.opaca.platform.PlatformConfig;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import de.gtarc.opaca.platform.session.SessionData;


/**
 * The purpose of the TokenUserDetailsService class is to provide user details 
 * for authentication and authorization purposes in our Spring application.
 * It further stores information about Users as it holds it repository and
 * offers methods to add/edit/get/remove information within it.
 */
@Service
@Transactional
public class TokenUserDetailsService implements UserDetailsService {

    @Autowired
    private PlatformConfig config;

    @Autowired
    private TokenUserRepository tokenUserRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;


    @PostConstruct
	public void postConstruct() {
        if (tokenUserRepository.findByUsername(config.usernamePlatform) == null) {
            // If Security is not enabled but password is null, use default password 'pass'
            // TODO Starting platform with auth enabled and no password set should log warning or even error
            String pwd = config.passwordPlatform == null && !config.enableAuth ? "pass" : config.passwordPlatform;
            createUser(config.usernamePlatform, pwd, "ROLE_ADMIN", null);
        }
	}

    /** Returns the TokenUser as a standardized 'User' object */
    @Override
    @Transactional
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        TokenUser user = tokenUserRepository.findByUsername(username);
        if (user != null) {
            return new User(username, user.getPassword(), getAuthorities(user));
        } else {
            throw new UsernameNotFoundException("User not found: " + username);
        }
    }

    /**
     * Adding a new user to the UserRepository. Users can be human [username, password, roles]
     * or containers [containerID, containerID, roles]
     * If a User already exists, throw an exception
     */
    @Transactional
    public void createUser(String username, String password, String role, List<String> privileges) {
        if (tokenUserRepository.findByUsername(username) != null) {
            throw new UserAlreadyExistsException(username);
        }
        else {
            TokenUser user = new TokenUser(username, passwordEncoder.encode(password), role, privileges);
            tokenUserRepository.save(user);
        }
    }

    /**
     * Returns a user based on its username.
     * If the user was not found, throw an exception.
     */
    public String getUser(String username) {
        return getTokenUser(username).toString();
    }

    @Transactional
    public TokenUser getTokenUser(String username) {
        TokenUser user = tokenUserRepository.findByUsername(username);
        if (user == null) throw new UsernameNotFoundException(username);
        return user;
    }

    /**
     * Return all users in the UserRepository
     */
    @Transactional
    public List<String> getUsers() {
        return tokenUserRepository.findAll().stream().map(TokenUser::toString).collect(Collectors.toList());
    }

    /**
     * Removes a user from the UserRepository.
     * Return true if the user was deleted, false if not.
     */
    @Transactional
    public Boolean removeUser(String username) {
        return tokenUserRepository.deleteByUsername(username) > 0;
    }

    /**
     * Update an existing user in the UserRepository.
     * Check for each user field if it exists and if so, update it.
     * If privileges are set, ALL privileges of the user will be replaced with the new list.
     * Return the updated user.
     */
    @Transactional
    public String updateUser(String username, String newUsername, String password, String role,
                             List<String> privileges) {
        TokenUser user = tokenUserRepository.findByUsername(username);
        if (user == null) throw new UsernameNotFoundException(username);
        if (tokenUserRepository.findByUsername(newUsername) != null &&
                !Objects.equals(username, newUsername)) throw new UserAlreadyExistsException(newUsername);
        if (password != null) user.setPassword(passwordEncoder.encode(password));
        if (role != null) user.setRole(role);
        if (privileges != null) user.setPrivileges(privileges);
        if (newUsername != null) user.setUsername(newUsername);
        tokenUserRepository.save(user);
        return getUser(user.getUsername());
    }

    /**
     * Returns the role and privileges as granted authorities from the given user
     */
    private Collection<? extends GrantedAuthority> getAuthorities(TokenUser user) {
        List<String> authorities = new ArrayList<>();
        authorities.add(user.getRole());
        authorities.addAll(user.getPrivileges());

        return authorities.stream().map(SimpleGrantedAuthority::new).collect(Collectors.toList());
    }

    /**
     * Return the role associated to the username
     */
    @Transactional
    public String getUserRole(String username) {
        TokenUser user = tokenUserRepository.findByUsername(username);
        if (user == null) throw new UsernameNotFoundException(username);
        return user.getRole();
    }

    /**
     * Return the privileges associated to the username
     */
    @Transactional
    public List<String> getUserPrivileges(String username) {
        TokenUser user = tokenUserRepository.findByUsername(username);
        if (user == null) throw new UsernameNotFoundException(username);
        return user.getPrivileges();
    }

    /**
     * Exceptions thrown during user creation if a given user already exists
     */
    static class UserAlreadyExistsException extends RuntimeException {
        public UserAlreadyExistsException(String username) {
            super("User with username '" + username + "' already exists!");
        }
    }
}
