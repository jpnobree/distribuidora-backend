package com.distribuidora.backend.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.Collection;

// Usuario autenticado com o id do banco, para auditoria e escopo de dados
// sem precisar de uma consulta extra a cada uso.
public class AppUserPrincipal extends User {

    private final Long id;
    private final String fullName;

    public AppUserPrincipal(
            Long id,
            String username,
            String password,
            String fullName,
            boolean enabled,
            Collection<? extends GrantedAuthority> authorities) {
        super(username, password, enabled, true, true, true, authorities);
        this.id = id;
        this.fullName = fullName;
    }

    public Long getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }
}
