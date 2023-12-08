package de.dailab.jiacpp.platform.user;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Collection;

@Data
@Document(collection = "role")
public class Role {

    private @Id String name;
    private Collection<Privilege> privileges;

    public Role() {}

    public Role(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return "Role{name='" + this.name + "', privileges='" + this.privileges + "'}";
    }

}
