package de.dailab.jiacpp.platform;

import de.dailab.jiacpp.model.User;
import de.dailab.jiacpp.platform.user.TokenUserDetailsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.extern.java.Log;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Log
@RestController
@SecurityRequirement(name = "bearerAuth")
@CrossOrigin(origins = "*", methods = { RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE } )
public class UserController {

    @Autowired
    TokenUserDetailsService userDetailsService;

    /*
     * USER MANAGEMENT
     */

    @RequestMapping(value="/users", method=RequestMethod.POST)
    @Operation(summary="Add a new user to the connected database", tags={"users"})
    public String addUser(
            @RequestBody User user) {
        try {
            userDetailsService.createUser(user.getUsername(), user.getPassword(), convertRoles(user.getRoles()));
            log.info(String.format("ADD USER: %s with roles: %s", user.getUsername(), user.getRoles()));
            return userDetailsService.getUser(user.getUsername());
        } catch (UsernameNotFoundException e) {
            return e.getMessage();
        }
    }

    @RequestMapping(value="/users/{username}", method=RequestMethod.DELETE)
    @Operation(summary="Delete an existing user from the connected database", tags={"users"})
    public boolean deleteUser(@PathVariable String username) {
        log.info(String.format("DELETE USER: %s", username));
        return userDetailsService.removeUser(username);
    }

    @RequestMapping(value="/users/{username}", method=RequestMethod.GET)
    @Operation(summary="Get an existing user from the connected database", tags={"users"})
    public String getUser(@PathVariable String username) {
        log.info(String.format("GET USER: %s", username));
        return userDetailsService.getUser(username);
    }

    @RequestMapping(value="/users", method=RequestMethod.GET)
    @Operation(summary="Get all users from the connected database", tags={"users"})
    public List<String> getUsers() {
        log.info("GET USERS");
        return List.copyOf(userDetailsService.getUsers());
    }

    @RequestMapping(value="/users/{username}", method=RequestMethod.PUT)
    @Operation(summary="Update the information of an existing user in the connected database", tags={"users"})
    public String updateUser(
            @PathVariable String username,
            @RequestBody User user) {
        String logOut = String.format("UPDATE USER: %s (", username);
        if (user.getUsername() != null) logOut += String.format("NEW USERNAME: %s ", user.getUsername());
        if (user.getPassword() != null) logOut += "NEW PASSWORD ";
        if (!user.getRoles().isEmpty()) logOut += String.format("NEW ROLES: %s", user.getRoles());
        log.info(logOut + ")");
        return userDetailsService.updateUser(username, user.getUsername(), user.getPassword(),
                convertRoles(user.getRoles()));
    }

    private Map<String, List<String>> convertRoles(List<User.Role> roles) {
        Map<String, List<String>> userRoles = new HashMap<>();
        for (de.dailab.jiacpp.model.User.Role role : roles) {
            userRoles.put(role.getName(), role.getPrivileges());
        }
        return userRoles;
    }
}
