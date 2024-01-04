package de.dailab.jiacpp.platform.user;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import jakarta.annotation.PostConstruct;

import de.dailab.jiacpp.platform.PlatformConfig;
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
 * It further stores information about Users/Roles/Privileges as it holds
 * their repositories and offers methods to add/edit/get/remove information
 * within them.
 */
@Service
public class TokenUserDetailsService implements UserDetailsService {

    @Autowired
    private PlatformConfig config;

    @Autowired
    private TokenUserRepository tokenUserRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PrivilegeRepository privilegeRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;


    @PostConstruct
	public void postConstruct() {
        if (tokenUserRepository.findByUsername(config.usernamePlatform) == null) {
            Map<String, List<String>> userRoles = new HashMap<>();
            userRoles.put("ROLE_" + config.rolePlatform, List.of("ADMIN_PRIVILEGE"));
            createUser(config.usernamePlatform, config.passwordPlatform, userRoles);
        }
	}

    /** Returns the user as a standardized 'User' object */
    @Override
    public UserDetails loadUserByUsername(String username) throws UserNotFoundException {
        TokenUser user = tokenUserRepository.findByUsername(username);
        if (user != null) {
            return new User(username, user.getPassword(),
                    getAuthorities(user.getRoles()));
        } else {
            throw new UserNotFoundException(username);
        }
    }

    /**
     * Adding a new user to the UserRepository. Users can be human [username, password, roles]
     * or containers [containerID, containerID, roles]
     * If a User already exists, throw an exception
     * If there was no password given, just create a default password
     * (this should be changed in the future)
     * Creates Roles/Privileges if they have never been used before
     */
    public void createUser(String username, String password, Map<String, List<String>> roles) {
        if (tokenUserRepository.findByUsername(username) != null) {
            throw new UserAlreadyExistsException(username);
        }
        else {
            validateString(username);
            validateString(password);
            Collection<Role> userRoles = createRolesIfNotFound(roles);
            // TODO make a password a requirement
            String pwd = config.enableAuth ? password : "defaultPwd";
            TokenUser user = new TokenUser(username, passwordEncoder.encode(pwd), userRoles);
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

    public TokenUser getTokenUser(String username) throws UserNotFoundException {
        TokenUser user = tokenUserRepository.findByUsername(username);
        if (user == null) throw new UserNotFoundException(username);
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
     */
    public Boolean removeUser(String username) throws UserNotFoundException {
        if (tokenUserRepository.findByUsername(username) == null){
            throw new UserNotFoundException(username);
        }
        return tokenUserRepository.deleteByUsername(username) != 0;
    }

    /**
     * Update an existing user in the UserRepository.
     * Check for each user field if it exists and if so, update it.
     * Return the updated user.
     */
    public String updateUser(String username, String newUsername, String password, Map<String, List<String>> roles)
        throws UserNotFoundException {

        // Check if there is an existing user
        TokenUser user = tokenUserRepository.findByUsername(username);
        if (user == null) throw new UserNotFoundException(username);

        // Check if username not taken and does not include invalid characters
        if (newUsername != null) {
            if (tokenUserRepository.findByUsername(newUsername) != null &&
                    !username.equals(newUsername)) throw new UserAlreadyExistsException(newUsername);
            validateString(newUsername);
        }

        // Validate and set password
        if (password != null){
            validateString(password);
            user.setPassword(passwordEncoder.encode(password));
        }
        if (roles != null) user.setRoles(createRolesIfNotFound(roles));
        if (newUsername != null) user.setUsername(newUsername);
        tokenUserRepository.save(user);
        return getUser(user.getUsername());
    }

    public Privilege createPrivilegeIfNotFound(String name) {
        Privilege privilege = privilegeRepository.findByName(name);
        if (privilege == null) {
            privilege = new Privilege(name);
            privilegeRepository.save(privilege);
        }
        return privilege;
    }

    public Role createRoleIfNotFound(String name, Collection<Privilege> privileges) {
        Role role = roleRepository.findByName(name);
        if (role == null) {
            role = new Role(name);
            role.setPrivileges(privileges);
            roleRepository.save(role);
        }
        return role;
    }

    /**
     * Create new Roles/Privileges based on a map containing their
     * info as strings {RoleName: [PrivilegeName, ...], ...}
     */
    public Collection<Role> createRolesIfNotFound(Map<String, List<String>> roles) {
        List<Role> userRoles = new ArrayList<>();
        for (String role : roles.keySet()) {
            List<Privilege> privileges = new ArrayList<>();
            for (String privilege : roles.get(role)) {
                privileges.add(createPrivilegeIfNotFound(privilege));
            }
            userRoles.add(createRoleIfNotFound(role, privileges));
        }
        return userRoles;
    }

    /**
     * Returns the roles and privileges as granted authorities based on the given
     * collection of roles.
     */
    private Collection<? extends GrantedAuthority> getAuthorities(Collection<Role> roles) {
        return getGrantedAuthorities(getPrivileges(roles));
    }

    private List<String> getPrivileges(Collection<Role> roles) {
        List<String> privileges = new ArrayList<>();
        List<Privilege> collection = new ArrayList<>();
        for (Role role : roles) {
            privileges.add(role.getName());
            collection.addAll(role.getPrivileges());
        }
        for (Privilege item : collection) {
            privileges.add(item.getName());
        }
        return privileges;
    }

    private List<GrantedAuthority> getGrantedAuthorities(List<String> privileges) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        for (String privilege : privileges) {
            authorities.add(new SimpleGrantedAuthority(privilege));
        }
        return authorities;
    }

    public Map<String, List<String>> getUserRoles(String username) throws UserNotFoundException {
        TokenUser user = tokenUserRepository.findByUsername(username);
        if (user == null) throw new UserNotFoundException(username);
        Map<String, List<String>> userRoles = new HashMap<>();
        for (Role role : user.getRoles()) {
            List<String> privileges = new ArrayList<>();
            for (Privilege privilege : role.getPrivileges()) {
                privileges.add(privilege.getName());
            }
            userRoles.put(role.getName(), privileges);
        }
        return userRoles;
    }

    private void validateString(String password) {
        String valid = "^[a-zA-Z0-9$&+,:;=?#|'<>.^*()%!/-]+$";
        if (!Pattern.compile(valid).matcher(password).matches())
            throw new IllegalArgumentException("Invalid Character provided.");
    }

    /**
     * Exceptions thrown during user creation if a given user already exists
     */
    public static class UserAlreadyExistsException extends RuntimeException {
        public UserAlreadyExistsException(String username) {
            super("User with username '" + username + "' already exists!");
        }
    }

    public static class UserNotFoundException extends UsernameNotFoundException {
        public UserNotFoundException(String username) {
            super("User with username '" + username + "' was not found!");
        }
    }
}
