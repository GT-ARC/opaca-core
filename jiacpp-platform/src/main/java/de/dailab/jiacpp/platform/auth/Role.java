package de.dailab.jiacpp.platform.auth;

import lombok.Data;

import java.util.Collection;

@Data
public class Role {

    private String name;
    private Collection<Privilege> privileges;

    public Role(String name, Collection<Privilege> privileges) {
        this.name = name;
        this.privileges = privileges;
    }
}
