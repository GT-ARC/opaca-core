package de.dailab.jiacpp.model;

import lombok.*;

/**
 * This class stores the username and password of a user and additionally the URL parameter
 * when connecting to another platform. Used for the /connection route
 */
@Data @AllArgsConstructor @NoArgsConstructor
@ToString(exclude = {"password"})
public class LoginConnection {

    /** Unique username belonging to the user initiating the request */
    String username;

    /** Password used to authenticate the user */
    String password;

    /** Url of another platform to connect to */
    String url;

}
