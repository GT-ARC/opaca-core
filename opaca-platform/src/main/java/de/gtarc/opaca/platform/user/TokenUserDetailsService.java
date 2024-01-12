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
 * It further stores information about Users/Roles/Privileges as it holds
 * their repositories and offers methods to add/edit/get/remove information
 * within them.
 */
@Service
@Transactional
public class TokenUserDetailsService implements UserDetailsService {

    @Autowired
    private SessionData sessionData;

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
		tokenUserRepository = sessionData.tokenUserRepository;
        roleRepository = sessionData.roleRepository;
        privilegeRepository = sessionData.privilegeRepository;
        if (tokenUserRepository.findByUsername(config.usernamePlatform) == null) {
            Map<String, List<String>> userRoles = new HashMap<>();
            userRoles.put("ROLE_" + config.rolePlatform, List.of("ADMIN_PRIVILEGE"));
            createUser(config.usernamePlatform, config.passwordPlatform, userRoles);
        }
	}

    /** Returns the user as a standardized 'User' object */
    @Override
    @Transactional
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        TokenUser user = tokenUserRepository.findByUsername(username);
        if (user != null) {
            return new User(username, user.getPassword(),
                    getAuthorities(user.getRoles()));
        } else {
            throw new UsernameNotFoundException("User not found: " + username);
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
    @Transactional
    public void createUser(String username, String password, Map<String, List<String>> roles) {
        if (tokenUserRepository.findByUsername(username) != null) {
            throw new UserAlreadyExistsException(username);
        }
        else {
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
     * Return the updated user.
     */
    @Transactional
    public String updateUser(String username, String newUsername, String password, Map<String, List<String>> roles) {
        TokenUser user = tokenUserRepository.findByUsername(username);
        if (user == null) throw new UsernameNotFoundException(username);
        if (tokenUserRepository.findByUsername(newUsername) != null &&
                !Objects.equals(username, newUsername)) throw new UserAlreadyExistsException(newUsername);
        if (password != null) user.setPassword(passwordEncoder.encode(password));
        if (roles != null) user.setRoles(createRolesIfNotFound(roles));
        if (newUsername != null) user.setUsername(newUsername);
        tokenUserRepository.save(user);
        return getUser(user.getUsername());
    }

    @Transactional
    public Privilege createPrivilegeIfNotFound(String name) {
        Privilege privilege = privilegeRepository.findByName(name);
        if (privilege == null) {
            privilege = new Privilege(name);
            privilegeRepository.save(privilege);
        }
        return privilege;
    }

    @Transactional
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
            List<Privilege> privileges = roles.get(role).stream()
                    .map(this::createPrivilegeIfNotFound)
                    .collect(Collectors.toList());
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
        for (Role role : roles) {
            privileges.add(role.getName());
            for (Privilege item : role.getPrivileges()) {
                privileges.add(item.getName());
            }
        }
        return privileges;
    }

    private List<GrantedAuthority> getGrantedAuthorities(List<String> privileges) {
        return privileges.stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
    }

    @Transactional
    public Map<String, List<String>> getUserRoles(String username) {
        TokenUser user = tokenUserRepository.findByUsername(username);
        if (user == null) throw new UsernameNotFoundException(username);
        Map<String, List<String>> userRoles = new HashMap<>();
        for (Role role : user.getRoles()) {
            List<String> privileges = role.getPrivileges().stream()
                    .map(Privilege::getName)
                    .collect(Collectors.toList());
            userRoles.put(role.getName(), privileges);
        }
        return userRoles;
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
