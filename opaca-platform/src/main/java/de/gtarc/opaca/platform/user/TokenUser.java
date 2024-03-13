package de.gtarc.opaca.platform.user;

import de.gtarc.opaca.model.Role;
import lombok.Data;
import lombok.ToString;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.List;

/**
 * The TokenUser stores user related information in a database.
 * Should include all fields from Model.User. A user can only
 * have one unique role, but may be assigned multiple privileges.
 * An ID is used to store the data in the database.
 */
@Data
@ToString(exclude = {"password"})
@Document(collection = "tokenUser")
public class TokenUser {

    private @Id String username;
    private String password;
    private Role role;
    private List<String> privileges = new ArrayList<>();

    public TokenUser() {}

    TokenUser(String userName, String password, Role role, List<String> privileges) {
        this.username = userName;
        this.password = password;
        this.role = role;
        if(privileges != null) this.privileges.addAll(privileges);
    }
}
