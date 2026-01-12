package de.gtarc.opaca.platform.auth;

import de.gtarc.opaca.model.User;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class KeycloakUtil {

    // TODO
    // login to keycloak
    // renew token
    // create temp sub user

    // FORMER METHODS OF JWT UTIL

    public String generateToken(String owner, Duration duration) {
        return null;
    }

    public String getUsernameFromToken(String token) {
        return null;
    }

    // FORMER METHODS OF TOKEN USER DETAILS SERVICE

    public String generateTokenForUser(String username, String password) {
        return null;
    }

    /**
     * Create a temporary sub-user, derived from the user of the current request, to be used by
     * Containers started or other Runtime Platforms connected by that user.
     *
     * @param username the name of the new user to be created
     * @param owner the name of the user creating the new user (ignored if no auth)
     */
    public User createTempSubUser(String username, String owner) {
        return null;
    }

    /**
     * Removes a user from the UserRepository.
     * Return true if the user was deleted, false if not.
     * If the user does not exist, throw exception.
     */
    public Boolean removeUser(String username) {
        return null;
    }

    /**
     * Checks if the current request user is either an admin (has full control over user management)
     * or the request user is performing request on its own data
     * @param username: Name of user which will get affected by request (NOT THE CURRENT REQUEST USER)
     */
    public boolean isAdminOrSelf(String username) {
        return false;
    }

    /**
     * Returns Access Token associated with container, or null
     */
    public String getContainerToken(String username, String containerId) {
        return null;
    }

    /**
     * Associate Access Token with container, overwrite existing if any
     */
    public void addContainerToken(String username, String containerId, String token) {

    }

    /**
     * Deleted Access Token associated with container, return old token if existed, otherwise null
     */
    public String removeContainerToken(String username, String containerId) {
        return null;
    }

}
