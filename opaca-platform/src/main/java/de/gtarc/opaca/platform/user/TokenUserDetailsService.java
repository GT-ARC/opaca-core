package de.gtarc.opaca.platform.user;

import java.util.*;
import java.util.stream.Collectors;

import de.gtarc.opaca.model.Role;
import jakarta.annotation.PostConstruct;

import de.gtarc.opaca.platform.PlatformConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;


/**
 * The purpose of the TokenUserDetailsService class is to provide user details 
 * for authentication and authorization purposes in our Spring application.
 * It further stores information about Users as it holds it repository and
 * offers methods to add/edit/get/remove information within it.
 */
@Service
public class TokenUserDetailsService implements UserDetailsService {

    @Autowired
    private PlatformConfig config;

    @Autowired
    private TokenUserRepository tokenUserRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;


    @PostConstruct
	public void postConstruct() {
        if (tokenUserRepository.findByUsername(config.platformAdminUser) == null) {
            if (config.platformAdminPwd == null) {
                throw new RuntimeException("Platform password cannot be null even when platform authorization is not enabled!");
            }
            createUser(config.platformAdminUser, config.platformAdminPwd, Role.ADMIN, null);
        }
	}

    /** Returns the TokenUser as a standardized 'User' object */
    @Override
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
     * or containers [containerID, password(random), roles]
     * If a User already exists, throw an exception
     */
    public void createUser(String username, String password, Role role, List<String> privileges) {
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

    public TokenUser getTokenUser(String username) {
        TokenUser user = tokenUserRepository.findByUsername(username);
        if (user == null) throw new UsernameNotFoundException(username);
        return user;
    }

    /**
     * Return all users in the UserRepository
     */
    public List<String> getUsers() {
        return tokenUserRepository.findAll().stream().map(TokenUser::toString).collect(Collectors.toList());
    }

    /**
     * Removes a user from the UserRepository.
     * Return true if the user was deleted, false if not.
     * If a the user does not exist, throw exception.
     */
    public Boolean removeUser(String username) {
        if (!tokenUserRepository.existsById(username)) {
            throw new UsernameNotFoundException(username);
        }
        return tokenUserRepository.deleteByUsername(username) > 0;
    }

    /**
     * Update an existing user in the UserRepository.
     * Check for each user field if it exists and if so, update it.
     * If privileges are set, ALL privileges of the user will be replaced with the new list.
     * Return the updated user.
     */
    public String updateUser(String username, String newUsername, String password, Role role,
                             List<String> privileges) {
        TokenUser user = tokenUserRepository.findByUsername(username);
        if (user == null) throw new UsernameNotFoundException(username);
        if (tokenUserRepository.findByUsername(newUsername) != null &&
                !Objects.equals(username, newUsername)) throw new UserAlreadyExistsException(newUsername);
        if (password != null) user.setPassword(passwordEncoder.encode(password));
        if (role != null) user.setRole(role);
        if (privileges != null) user.setPrivileges(privileges);
        if (newUsername != null){
            user.setUsername(newUsername);
            // If a new username (mongo ID) is given, the old entity should be deleted
            tokenUserRepository.deleteByUsername(username);
            // TODO what happens if the deletion or creation fails?
            //  Updating should be atomic
        }
        tokenUserRepository.save(user);
        return getUser(user.getUsername());
    }

    /**
     * Returns the role and privileges as granted authorities from the given user
     */
    private Collection<? extends GrantedAuthority> getAuthorities(TokenUser user) {
        List<String> authorities = new ArrayList<>();
        authorities.add(user.getRole().role());
        authorities.addAll(user.getPrivileges());

        return authorities.stream().map(SimpleGrantedAuthority::new).collect(Collectors.toList());
    }

    /**
     * Return the role associated to the username
     */
    public Role getUserRole(String username) {
        TokenUser user = tokenUserRepository.findByUsername(username);
        if (user == null) throw new UsernameNotFoundException(username);
        return user.getRole();
    }

    /**
     * Return the privileges associated to the username
     */
    public List<String> getUserPrivileges(String username) {
        TokenUser user = tokenUserRepository.findByUsername(username);
        if (user == null) throw new UsernameNotFoundException(username);
        return user.getPrivileges();
    }

    /**
     * Exceptions thrown during user creation if a given user already exists
     */
    public static class UserAlreadyExistsException extends RuntimeException {
        public UserAlreadyExistsException(String username) {
            super("User with username '" + username + "' already exists!");
        }
    }
}
