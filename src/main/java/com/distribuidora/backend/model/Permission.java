package com.distribuidora.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// Permissoes sao criadas pelas migrations de cada modulo (o codigo e quem as
// verifica), nunca pela interface.
@Entity
@Table(name = "permissions")
public class Permission {

    @Id
    private String code;

    @Column(nullable = false)
    private String module;

    @Column(nullable = false)
    private String description;

    protected Permission() {
    }

    public Permission(String code, String module, String description) {
        this.code = code;
        this.module = module;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getModule() {
        return module;
    }

    public String getDescription() {
        return description;
    }
}
