package de.dailab.jiacpp.platform.user;

import java.util.*;
import java.util.stream.Collectors;

import jakarta.annotation.PostConstruct;

import de.dailab.jiacpp.platform.PlatformConfig;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import de.dailab.jiacpp.platform.session.SessionData;


/**
 * The purpose of the TokenUserDetailsService class is to provide user details 
 * for authentication and authorization purposes in our Spring application. 
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
            Privilege privilege = createPrivilegeIfNotFound("ADMIN_PRIVILEGE");
            Role role = createRoleIfNotFound("ROLE_" + config.rolePlatform, Arrays.asList(privilege));
            createUser(config.usernamePlatform, config.passwordPlatform, Arrays.asList(role));
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
     * Adding users to the set of tokenUsers. Users can be human [username, password, roles]
     * or containers [containerID, containerID, roles]
     * If a User already exists, throw an exception
     * If there was no password given, just create a default password
     * (this should be changed in the future)
     */
    @Transactional
    public void createUser(String username, String password, Collection<Role> roles) {
        if (tokenUserRepository.findByUsername(username) != null) {
            throw new UserAlreadyExistsException(username);
        }
        else {
            // TODO make a password a requirement
            String pwd = config.enableAuth ? password : "defaultPwd";
            TokenUser user = new TokenUser(username, passwordEncoder.encode(pwd), roles);
            tokenUserRepository.save(user);
        }
    }

    @Transactional
    public String getUser(String username) {
        TokenUser user = tokenUserRepository.findByUsername(username);
        if (user == null) return "No user found with name: " + username;
        return user.toString();
    }

    @Transactional
    public List<String> getUsers() {
        return tokenUserRepository.findAll().stream().map(TokenUser::toString).collect(Collectors.toList());
    }

    @Transactional
    public Boolean removeUser(String username) {
        return tokenUserRepository.deleteByUsername(username) > 0;
    }

    @Transactional
    public String updateUser(String username, String newUsername, String password, Collection<Role> roles) {
        TokenUser user = tokenUserRepository.findByUsername(username);
        if (user == null) return "No user found with name: " + username;
        if (password != null) user.setPassword(password);
        if (roles != null) user.setRoles(roles);
        if (newUsername != null) user.setUsername(newUsername);
        return getUser(newUsername);
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

    // This temp method is just for testing to get the admin role
    @Transactional
    public Collection<Role> getDebugRole() {
        return Arrays.asList(roleRepository.findByName("ROLE_ADMIN"));
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
