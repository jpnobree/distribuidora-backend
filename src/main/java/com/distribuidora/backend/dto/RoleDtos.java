package com.distribuidora.backend.dto;

import com.distribuidora.backend.model.Permission;
import com.distribuidora.backend.model.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Set;

public final class RoleDtos {

    private RoleDtos() {
    }

    public record RoleResponse(
            Long id,
            String code,
            String name,
            String description,
            boolean systemRole,
            // Administrador tem todas as permissoes por definicao
            boolean allPermissions,
            List<String> permissions,
            long userCount) {

        public static RoleResponse from(Role role, long userCount) {
            return new RoleResponse(
                    role.getId(),
                    role.getCode(),
                    role.getName(),
                    role.getDescription(),
                    role.isSystemRole(),
                    role.isAdministrator(),
                    role.getPermissions().stream().map(Permission::getCode).sorted().toList(),
                    userCount);
        }
    }

    public record PermissionResponse(String code, String module, String description) {
        public static PermissionResponse from(Permission permission) {
            return new PermissionResponse(permission.getCode(), permission.getModule(), permission.getDescription());
        }
    }

    public record RoleCreateRequest(
            @NotBlank
            @Pattern(regexp = "[A-Z][A-Z0-9_]{2,39}", message = "Use de 3 a 40 letras maiúsculas, números ou _")
            String code,
            @NotBlank @Size(max = 80) String name,
            @Size(max = 255) String description,
            @NotNull Set<String> permissions) {
    }

    public record RoleUpdateRequest(
            @NotBlank @Size(max = 80) String name,
            @Size(max = 255) String description,
            @NotNull Set<String> permissions) {
    }
}
