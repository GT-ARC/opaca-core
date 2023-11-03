package de.dailab.jiacpp.platform.auth;

import lombok.Data;

@Data
public class Privilege {

    private String name;

    public Privilege(String name) {
        this.name = name;
    }

}
