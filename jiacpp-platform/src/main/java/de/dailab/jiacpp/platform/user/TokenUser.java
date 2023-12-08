package de.dailab.jiacpp.platform.user;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Collection;

@Data
@Document(collection = "tokenUser")
public class TokenUser {

    private @Id String username;
    private String password;

    private Collection<Role> roles;

    public TokenUser() {}

    TokenUser(String userName, String password, Collection<Role> roles) {
        this.username = userName;
        this.password = password;
        this.roles = roles;
    }

    @Override
    public String toString() {
        return "User{username='" + this.username + "', roles='" + this.roles + "'}";
    }

}
