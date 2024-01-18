package de.gtarc.opaca.platform.user;

import jakarta.persistence.*;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * The TokenUser stores user related information in a data bank.
 * Should include all fields from Model.User. A user can only
 * have one unique role, but may be assigned multiple privileges.
 * An ID is used to store the data in the data bank.
 */
@Entity
@Data
public class TokenUser {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    private String username;
    private String password;
    private String role;
    @ElementCollection(targetClass = String.class, fetch = FetchType.EAGER)
    private List<String> privileges = new ArrayList<>();

    public TokenUser() {}

    TokenUser(String userName, String password, String role, List<String> privileges) {
        this.username = userName;
        this.password = password;
        this.role = role;
        if(privileges != null) this.privileges.addAll(privileges);
    }

    @Override
    public String toString() {
        return "User{" + "id=" + this.id + ", username='" + this.username + "', role='" + this.role +
               "', privileges=" + this.privileges.toString() + "}";
    }

}
