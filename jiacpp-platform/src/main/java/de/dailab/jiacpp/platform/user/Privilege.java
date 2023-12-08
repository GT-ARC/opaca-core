package de.dailab.jiacpp.platform.user;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Collection;

@Data
@Document(collection = "privilege")
public class Privilege {

    private @Id String name;

    private Collection<Role> roles;

    public Privilege() {}

    Privilege(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return "Privilege{name='" + this.name + "'}";
    }

}
