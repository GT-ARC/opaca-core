package de.gtarc.opaca.model;

/**
 * Pre-defines roles assigned to users.
 * Per Spring Boot notation, a role needs
 * to start with the "ROLE_" prefix.
 */
public enum Role {
    ADMIN("ROLE_ADMIN"),
    CONTRIBUTOR("ROLE_CONTRIBUTOR"),
    USER("ROLE_USER"),
    GUEST("ROLE_GUEST");

    private final String role;

    Role(String role) {
        this.role = role;
    }

    public String role() {
        return role;
    }
}
