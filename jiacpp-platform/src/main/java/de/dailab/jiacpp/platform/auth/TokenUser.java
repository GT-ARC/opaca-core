package de.dailab.jiacpp.platform.auth;

import lombok.Data;

import java.util.Collection;

@Data
public class TokenUser {
    private String userName;
    private String password;
    private Collection<Role> roles;

    TokenUser(String userName, String password, Collection<Role> roles) {
        this.userName = userName;
        this.password = password;
        this.roles = roles;
    }
}
