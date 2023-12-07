package de.dailab.jiacpp.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Stores login parameters in request body during login request
 */
@Data @AllArgsConstructor @NoArgsConstructor
public class Login {

    /**
     * Unique username belonging to the user initiating the request
     */
    String username;

    /**
     * Password used to authenticate the user
     */
    String password;

    /**
     * Edit String representation to hide password parameter in logs
     */
    @Override
    public String toString() {
        return "Login(username='" + this.username + "')";
    }

}
