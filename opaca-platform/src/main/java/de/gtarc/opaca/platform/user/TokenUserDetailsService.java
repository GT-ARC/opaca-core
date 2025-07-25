package de.gtarc.opaca.platform.user;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

import de.gtarc.opaca.model.Role;
import de.gtarc.opaca.model.User;
import de.gtarc.opaca.platform.auth.JwtUtil;
import jakarta.annotation.PostConstruct;

import de.gtarc.opaca.platform.PlatformConfig;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
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
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;


    @PostConstruct
	public void postConstruct() {
        // delete and re-create admin user in case password changed (alternatively, delete in pre-destroy?)
        if (userRepository.findByUsername(config.platformAdminUser) != null) {
            removeUser(config.platformAdminUser);
        }
        if (config.platformAdminPwd == null) {
            throw new RuntimeException("Platform password cannot be null even when platform authorization is not enabled!");
        }
        createUser(config.platformAdminUser, config.platformAdminPwd, Role.ADMIN, new ArrayList<>());
	}

    /** Returns the TokenUser as a standardized 'User' object */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username);
        if (user != null) {
            return new org.springframework.security.core.userdetails.User(username, user.getPassword(), getAuthorities(user));
        } else {
            throw new UsernameNotFoundException("User not found: " + username);
        }
    }

    public String generateTokenForUser(String username, String password) {
        UserDetails userDetails = loadUserByUsername(username);
        if (passwordEncoder.matches(password, userDetails.getPassword())) {
            return jwtUtil.generateToken(username, Duration.ofHours(1));
        } else {
            throw new BadCredentialsException("Wrong password");
        }
    }

    /**
     * Adding a new user to the UserRepository. Users can be human [username, password, roles, privileges]
     * or containers [containerID, password(random), roles, privileges]
     * If a User already exists, throw an exception
     */
    public void createUser(String username, String password, Role role, List<String> privileges) {
        if (userRepository.findByUsername(username) != null) {
            throw new UserAlreadyExistsException(username);
        } else {
            User user = new User(username, passwordEncoder.encode(password), role, privileges != null ? privileges : new ArrayList<>());
            userRepository.save(user);
        }
    }

    /**
     * Create a temporary sub-user, derived from teh user of the current request, to be used by
     * Containers started or other Runtime Platforms connected by that user.
     */
    public void createTempSubUser(String username) {
        var owner = getUser(jwtUtil.getCurrentRequestUser()).getUsername();
        createUser(username, generateRandomPwd(),
                config.enableAuth ? getUserRole(owner) : Role.GUEST,
                config.enableAuth ? getUserPrivileges(owner) : null
        );
    }

    /**
     * Creates a random String of length 24 containing upper and lower case characters and numbers
     */
    public String generateRandomPwd() {
        return RandomStringUtils.random(24, true, true);
    }

    /**
     * Returns a user based on its username.
     * If the user was not found, throw an exception.
     */
    public User getUser(String username) {
        User user = userRepository.findByUsername(username);
        if (user == null) throw new UsernameNotFoundException(username);
        return user;
    }

    /**
     * Return all users in the UserRepository
     */
    public List<String> getUsers() {
        return userRepository.findAll().stream().map(User::toString).collect(Collectors.toList());
    }

    /**
     * Removes a user from the UserRepository.
     * Return true if the user was deleted, false if not.
     * If the user does not exist, throw exception.
     */
    public Boolean removeUser(String username) {
        if (! userRepository.existsByUsername(username)) {
            throw new UsernameNotFoundException(username);
        }
        return userRepository.deleteByUsername(username);
    }

    /**
     * Update an existing user in the UserRepository.
     * Check for each user field if it exists and if so, update it.
     * If privileges are set, ALL privileges of the user will be replaced with the new list.
     * Return the updated user.
     */
    public String updateUser(String username, String newUsername, String password, Role role,
                             List<String> privileges) {
        User user = userRepository.findByUsername(username);
        if (user == null) throw new UsernameNotFoundException(username);
        if (userRepository.findByUsername(newUsername) != null &&
                !Objects.equals(username, newUsername)) throw new UserAlreadyExistsException(newUsername);
        if (password != null) user.setPassword(passwordEncoder.encode(password));
        if (role != null) user.setRole(role);
        if (privileges != null) user.setPrivileges(privileges);
        if (newUsername != null) {
            user.setUsername(newUsername);
            // If a new username (mongo ID) is given, the old entity should be deleted
            userRepository.deleteByUsername(username);
            // TODO what happens if the deletion or creation fails?
            //  Updating should be atomic
        }
        userRepository.save(user);
        return getUser(user.getUsername()).toString();
    }

    /**
     * Returns the role and privileges as granted authorities from the given user
     */
    private Collection<? extends GrantedAuthority> getAuthorities(User user) {
        List<String> authorities = new ArrayList<>();
        authorities.add(user.getRole().role());
        authorities.addAll(user.getPrivileges());

        return authorities.stream().map(SimpleGrantedAuthority::new).collect(Collectors.toList());
    }

    /**
     * Return the role associated to the username
     */
    public Role getUserRole(String username) {
        User user = userRepository.findByUsername(username);
        if (user == null) throw new UsernameNotFoundException(username);
        return user.getRole();
    }

    /**
     * Return the privileges associated to the username
     */
    public List<String> getUserPrivileges(String username) {
        User user = userRepository.findByUsername(username);
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
