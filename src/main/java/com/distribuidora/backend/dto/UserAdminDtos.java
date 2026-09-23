package com.distribuidora.backend.dto;

import com.distribuidora.backend.model.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

public final class UserAdminDtos {

    private UserAdminDtos() {
    }

    public record UserResponse(
            Long id,
            String username,
            String fullName,
            String email,
            boolean active,
            Instant createdAt,
            Instant lastLoginAt,
            List<RoleSummary> roles) {

        public static UserResponse from(User user) {
            return new UserResponse(
                    user.getId(),
                    user.getUsername(),
                    user.getFullName(),
                    user.getEmail(),
                    user.isActive(),
                    user.getCreatedAt(),
                    user.getLastLoginAt(),
                    user.getRoles().stream()
                            .map(RoleSummary::from)
                            .sorted(Comparator.comparing(RoleSummary::name))
                            .toList());
        }
    }

    public record UserCreateRequest(
            @NotBlank
            @Size(min = 3, max = 50)
            @Pattern(regexp = "[a-zA-Z0-9._-]+", message = "Use apenas letras, números, ponto, hífen e sublinhado")
            String username,
            @NotBlank @Size(max = 120) String fullName,
            @Email @Size(max = 255) String email,
            @NotBlank
            @Size(min = 8, max = 72, message = "A senha precisa ter entre 8 e 72 caracteres")
            String password,
            @NotEmpty(message = "Escolha pelo menos um perfil") Set<String> roleCodes) {
    }

    public record UserUpdateRequest(
            @NotBlank @Size(max = 120) String fullName,
            @Email @Size(max = 255) String email,
            @NotEmpty(message = "Escolha pelo menos um perfil") Set<String> roleCodes,
            boolean active) {
    }

    public record PasswordResetRequest(
            @NotBlank
            @Size(min = 8, max = 72, message = "A senha precisa ter entre 8 e 72 caracteres")
            String password) {
    }
}
