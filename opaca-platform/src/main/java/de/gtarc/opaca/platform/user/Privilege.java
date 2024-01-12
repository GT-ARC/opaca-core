package de.gtarc.opaca.platform.user;

import jakarta.persistence.*;
import lombok.Data;

import java.util.Collection;

@Entity
@Data
public class Privilege {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    private String name;

    @ManyToMany(mappedBy = "privileges")
    private Collection<Role> roles;

    public Privilege() {}

    Privilege(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return "Privilege{id=" + this.id + ", name='" + this.name + "'}";
    }

}
