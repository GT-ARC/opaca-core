package de.gtarc.opaca.platform.user;

import jakarta.persistence.*;
import lombok.Data;

import java.util.Collection;

@Entity
@Data
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    private String name;
    @ManyToMany
    private Collection<TokenUser> tokenUsers;

    @ManyToMany
    @JoinTable(
            name = "role_privileges",
            joinColumns = @JoinColumn(
                    name = "role_id", referencedColumnName = "id"),
            inverseJoinColumns = @JoinColumn(
                    name = "privilege_id", referencedColumnName = "id")
    )
    private Collection<Privilege> privileges;

    public Role() {}

    public Role(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return "Role{id=" + this.id + ", name='" + this.name + "'}";
    }

}
