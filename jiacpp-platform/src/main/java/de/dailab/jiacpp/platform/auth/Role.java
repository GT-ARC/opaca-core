package de.dailab.jiacpp.platform.auth;

import lombok.Data;

import java.util.Collection;

@Data
public class Role {

    private String name;
    private Collection<String> privileges;

    public Role(String name) {
        this(name, null);
    }

    public Role(String name, Collection<String> privileges) {
        this.name = name;
        this.privileges = privileges;
    }
}
