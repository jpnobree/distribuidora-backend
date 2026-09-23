package com.distribuidora.backend.dto;

import com.distribuidora.backend.model.Role;

public record RoleSummary(String code, String name) {
    public static RoleSummary from(Role role) {
        return new RoleSummary(role.getCode(), role.getName());
    }
}
