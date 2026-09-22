package com.distribuidora.backend.dto;

import java.util.List;
import java.util.Set;

public record MeResponse(
        Long id,
        String username,
        String fullName,
        String email,
        List<RoleSummary> roles,
        Set<String> permissions) {
}
