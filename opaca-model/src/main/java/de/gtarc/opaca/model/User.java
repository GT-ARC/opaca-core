package de.gtarc.opaca.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Description for a User, later converted to a TokenUser.
 * This class stores the name, password, role, and multiple
 * privileges as strings.
 *
 * NOTE: When changing attributes in this class, make sure to adapt UserRepository accordingly!
 */
@Data
@AllArgsConstructor @NoArgsConstructor
@ToString(exclude = {"password"})
public class User {

    /** name of the user */
    String username;

    /** plain-text password for POST, then stored as password-hash in the database */
    String password;

    /** role of the user, used to determine which routes are allowed */
    Role role;

    /** list of privileges (currently not really used...) */
    List<String> privileges;

    /** map of container-logins stored for this user, mapping container-ids to access-tokens */
    Map<String, String> containerLoginTokens = new HashMap<>();

}
