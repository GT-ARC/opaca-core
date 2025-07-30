package de.gtarc.opaca.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.List;

/**
 * Description for a User, later converted to a TokenUser.
 * This class stores the name, password, role, and multiple
 * privileges as strings.
 */
@Data
@AllArgsConstructor @NoArgsConstructor
@ToString(exclude = {"password"})
public class User {
    String username;
    String password;
    Role role;
    List<String> privileges;
}
