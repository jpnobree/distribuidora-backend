package com.distribuidora.backend.dto;

import java.util.List;
import java.util.Set;

// "role" (ADMIN/USER) continua existindo para a vitrine; o ERP usa
// "roles" e "permissions".
public record AuthResponse(
        String token,
        String username,
        String role,
        String fullName,
        List<String> roles,
        Set<String> permissions) {
}
