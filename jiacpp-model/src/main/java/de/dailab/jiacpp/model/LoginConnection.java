package de.dailab.jiacpp.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * This class stores the username and password of a user and additionally an url parameter
 * when connecting to another platform. Used for the /connection route
 */
@Data @AllArgsConstructor @NoArgsConstructor
public class LoginConnection {

    /**
     * Unique username belonging to the user initiating the request
     */
    String username;

    /**
     * Password used to authenticate the user
     */
    String password;

    /**
     * Url of another platform to connect to
     */
    String url;

    /**
     * Edit String representation to hide password parameter in logs
     */
    @Override
    public String toString() {
        return "LoginConnection(username='" + this.username + "', url='" + this.url + "')";
    }
}
