package de.dailab.jiacpp.platform.user;

import jakarta.persistence.*;
import lombok.Data;

import java.util.Collection;

@Entity
@Data
public class TokenUser {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    private String username;
    private String password;

    @ManyToMany
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(
                    name = "user_id", referencedColumnName = "id"),
            inverseJoinColumns = @JoinColumn(
                    name = "role_id", referencedColumnName = "id")
    )
    private Collection<Role> roles;

    public TokenUser() {}

    TokenUser(String userName, String password, Collection<Role> roles) {
        this.username = userName;
        this.password = password;
        this.roles = roles;
    }

    @Override
    public String toString() {
        return "User{" + "id=" + this.id + ", username='" + this.username + "'}";
    }

}
