package de.dailab.jiacpp.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Description for a User, later stored in the connected database.
 * The user class stores its name, its password and also
 * its assigned roles, which can include multiple privileges.
 */
@Data
@AllArgsConstructor @NoArgsConstructor
public class User {
    String username;
    String password;
    List<Role> roles;

    @Data
    @AllArgsConstructor @NoArgsConstructor
    public static class Role {
        String name;
        List<String> privileges;

        @Override
        public String toString() {
            return name;
        }
    }
}
