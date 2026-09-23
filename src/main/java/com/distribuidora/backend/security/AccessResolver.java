package com.distribuidora.backend.security;

import com.distribuidora.backend.model.Permission;
import com.distribuidora.backend.model.Role;
import com.distribuidora.backend.model.User;
import com.distribuidora.backend.repository.PermissionRepository;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

@Component
public class AccessResolver {

    private final PermissionRepository permissionRepository;

    public AccessResolver(PermissionRepository permissionRepository) {
        this.permissionRepository = permissionRepository;
    }

    // Administrador recebe toda permissao existente, inclusive as criadas por
    // migrations futuras, sem precisar concede-las uma a uma.
    public Set<String> permissionCodes(User user) {
        if (user.isAdministrator()) {
            return permissionRepository.findAll().stream()
                    .map(Permission::getCode)
                    .collect(Collectors.toCollection(TreeSet::new));
        }
        return user.getRoles().stream()
                .map(Role::getPermissions)
                .flatMap(Set::stream)
                .map(Permission::getCode)
                .collect(Collectors.toCollection(TreeSet::new));
    }
}
