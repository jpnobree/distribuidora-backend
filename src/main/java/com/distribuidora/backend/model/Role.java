package com.distribuidora.backend.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "roles")
public class Role {

    public static final String ADMINISTRADOR = "ADMINISTRADOR";
    public static final String CLIENTE = "CLIENTE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column
    private String description;

    @Column(name = "system_role", nullable = false)
    private boolean systemRole;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @ManyToMany
    @JoinTable(
            name = "role_permissions",
            joinColumns = @JoinColumn(name = "role_id"),
            inverseJoinColumns = @JoinColumn(name = "permission_code"))
    private Set<Permission> permissions = new HashSet<>();

    protected Role() {
    }

    public Role(String code, String name, String description, boolean systemRole) {
        this.code = code;
        this.name = name;
        this.description = description;
        this.systemRole = systemRole;
    }

    public boolean isAdministrator() {
        return ADMINISTRADOR.equals(code);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isSystemRole() {
        return systemRole;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Set<Permission> getPermissions() {
        return permissions;
    }

    public void setPermissions(Set<Permission> permissions) {
        this.permissions = permissions;
    }
}
