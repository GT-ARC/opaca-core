package de.dailab.jiacpp.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Stores login parameters in request body during login request
 */
@Data @AllArgsConstructor @NoArgsConstructor
@ToString(exclude = {"password"})
public class Login {

    /** Unique username belonging to the user initiating the request */
    String username;

    /** Password used to authenticate the user */
    String password;

}
