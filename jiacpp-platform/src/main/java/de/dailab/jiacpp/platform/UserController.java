package de.dailab.jiacpp.platform;

import de.dailab.jiacpp.model.User;
import de.dailab.jiacpp.platform.auth.JwtUtil;
import de.dailab.jiacpp.platform.user.TokenUserDetailsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.extern.java.Log;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

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

    @Autowired
    private JwtUtil jwtUtil;

    /*
     * USER MANAGEMENT
     */

    /**
     * Creates a new user and adds it to the database.
     * @param user User to be added to the database. Includes username, password, roles.
     * @return The newly created user. If the addition threw an error, return the error message.
     */
    @RequestMapping(value="/users", method=RequestMethod.POST)
    @Operation(summary="Add a new user to the connected database", tags={"users"})
    public String addUser(
            @RequestBody User user) {

        try {
            userDetailsService.createUser(user.getUsername(), user.getPassword(), convertRoles(user.getRoles()));
            log.info(String.format("ADD USER: %s with roles: %s", user.getUsername(), user.getRoles()));
            return userDetailsService.getUser(user.getUsername());
        } catch (Exception e) {
            return e.getMessage();
        }
    }

    /**
     * Deletes a user from the database
     * @param token JWT to check the requesters authority
     * @param username User which will be deleted from the database
     * @return True if deletion was successful, False if not
     */
    @RequestMapping(value="/users/{username}", method=RequestMethod.DELETE)
    @Operation(summary="Delete an existing user from the connected database", tags={"users"})
    public boolean deleteUser(
            @RequestHeader("Authorization") String token,
            @PathVariable String username) {

        if (!isAdminOrSelf(token, username)) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        log.info(String.format("DELETE USER: %s", username));
        return userDetailsService.removeUser(username);
    }

    /**
     * Returns information (username, roles) for a specific user in the database
     * @param token JWT to check the requesters authority
     * @param username User from which the information is requested
     * @return User as a string representation
     */
    @RequestMapping(value="/users/{username}", method=RequestMethod.GET)
    @Operation(summary="Get an existing user from the connected database", tags={"users"})
    public String getUser(
            @RequestHeader("Authorization") String token,
            @PathVariable String username) {

        if (!isAdminOrSelf(token, username)) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        log.info(String.format("GET USER: %s", username));
        return userDetailsService.getUser(username);
    }

    /**
     * Return all users in the database
     * @return All users in the database as their string representation
     */
    @RequestMapping(value="/users", method=RequestMethod.GET)
    @Operation(summary="Get all users from the connected database", tags={"users"})
    public List<String> getUsers() {

        log.info("GET USERS");
        return List.copyOf(userDetailsService.getUsers());
    }

    /**
     * Edit a users information when giving its (former) username
     * @param username Username of the User to be edited
     * @param user New User body including either updated information or null if the field shall not be updated
     * @return The updated user with its updated information (excluding the password)
     */
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

    // Helper methods

    /**
     * Checks if the current request user is either an admin (has full control over user management)
     * or the request user is performing request on its own data
     * @param token: The token belonging to a user in the database for whom to check their authorities
     * @param username: Name of user which will get affected by request (NOT THE CURRENT REQUEST USER)
     * */
    private boolean isAdminOrSelf(String token, String username) {

        final String userToken = token.substring(7);
        UserDetails details = userDetailsService.loadUserByUsername(jwtUtil.getUsernameFromToken(userToken));
        if (details == null) return false;
        return details.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")) ||
                details.getUsername().equals(username);
    }
}
