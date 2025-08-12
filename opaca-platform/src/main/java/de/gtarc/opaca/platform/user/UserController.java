package de.gtarc.opaca.platform.user;

import de.gtarc.opaca.model.Role;
import de.gtarc.opaca.model.User;
import de.gtarc.opaca.platform.PlatformConfig;
import de.gtarc.opaca.platform.auth.JwtUtil;
import de.gtarc.opaca.platform.user.TokenUserDetailsService.UserAlreadyExistsException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.extern.java.Log;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Log
@RestController
@SecurityRequirement(name = "bearerAuth")
@CrossOrigin(origins = "*", methods = { RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE } )
public class UserController {

    @Autowired
    private TokenUserDetailsService userDetailsService;
    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private PlatformConfig config;

    /*
     * EXCEPTION HANDLERS
     */

    @ExceptionHandler({IllegalArgumentException.class})
    public ResponseEntity<String> handleBadRequestException(Exception e) {
        log.warning(e.toString());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
    }

    @ExceptionHandler({UserAlreadyExistsException.class})
    public ResponseEntity<String> handleAlreadyExistsException(Exception e) {
        log.warning(e.toString());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
    }

    @ExceptionHandler({UsernameNotFoundException.class})
    public ResponseEntity<String> handleResourceNotFoundException(Exception e) {
        log.warning(e.toString());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
    }

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
    public ResponseEntity<?> addUser(
            @RequestBody User user
    ) {
        log.info(String.format("POST /users %s", user));
        userDetailsService.createUser(user.getUsername(), user.getPassword(), user.getRole(), user.getPrivileges());
        return new ResponseEntity<>(userDetailsService.getUser(user.getUsername()).toString(), HttpStatus.CREATED);
    }

    /**
     * Deletes a user from the database
     * If security is disabled, do not perform secondary role check
     * @param username User which will be deleted from the database
     * @return True if deletion was successful, False if not
     */
    @RequestMapping(value="/users/{username}", method=RequestMethod.DELETE)
    @Operation(summary="Delete an existing user from the connected database", tags={"users"})
    public ResponseEntity<?> deleteUser(
            @PathVariable String username
    ) {
        log.info(String.format("DELETE /users/%s", username));
        return new ResponseEntity<>(userDetailsService.removeUser(username), HttpStatus.OK);
    }

    /**
     * Returns information (username, roles) for a specific user in the database
     * If security is disabled, do not perform secondary role check
     * @param token JWT to check the requesters authority
     * @param username User from which the information is requested
     * @return User as a string representation
     */
    @RequestMapping(value="/users/{username}", method=RequestMethod.GET)
    @Operation(summary="Get an existing user from the connected database", tags={"users"})
    public ResponseEntity<String> getUser(
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String token,
            @PathVariable String username
    ) {
        log.info(String.format("GET /users/%s", username));
        if (!config.enableAuth || isAdminOrSelf(token, username)){
            return new ResponseEntity<>(userDetailsService.getUser(username).toString(), HttpStatus.OK);
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }

    /**
     * Return all users in the database
     * @return All users in the database as their string representation
     */
    @RequestMapping(value="/users", method=RequestMethod.GET)
    @Operation(summary="Get all users from the connected database", tags={"users"})
    public ResponseEntity<List<String>> getUsers() {
        log.info("GET /users");
        return new ResponseEntity<>(List.copyOf(userDetailsService.getUsers()), HttpStatus.OK);
    }

    /**
     * Edit a users information when giving its (former) username
     * @param username Username of the User to be edited
     * @param user New User body including either updated information or null if the field shall not be updated
     * @return The updated user with its updated information (excluding the password)
     */
    @RequestMapping(value="/users/{username}", method=RequestMethod.PUT)
    @Operation(summary="Update the information of an existing user in the connected database", tags={"users"})
    public ResponseEntity<?> updateUser(
            @PathVariable String username,
            @RequestBody User user
    ) {
        log.info(String.format("PUT /users/%s %s", username, user));
        return new ResponseEntity<>(userDetailsService.updateUser(username, user.getUsername(), user.getPassword(),
                user.getRole(), user.getPrivileges()), HttpStatus.OK);
    }

    // Helper methods

    /**
     * Checks if the current request user is either an admin (has full control over user management)
     * or the request user is performing request on its own data
     * @param token: The token belonging to a user in the database for whom to check their authorities
     * @param username: Name of user which will get affected by request (NOT THE CURRENT REQUEST USER)
     */
    private boolean isAdminOrSelf(String token, String username) {
        final String userToken = token.substring(7);
        UserDetails details = userDetailsService.loadUserByUsername(jwtUtil.getUsernameFromToken(userToken));
        if (details == null) return false;
        return details.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals(Role.ADMIN.role())) ||
                details.getUsername().equals(username);
    }
}
