package com.distribuidora.backend.controller;

import com.distribuidora.backend.dto.PageResponse;
import com.distribuidora.backend.dto.UserAdminDtos.PasswordResetRequest;
import com.distribuidora.backend.dto.UserAdminDtos.UserCreateRequest;
import com.distribuidora.backend.dto.UserAdminDtos.UserResponse;
import com.distribuidora.backend.dto.UserAdminDtos.UserUpdateRequest;
import com.distribuidora.backend.security.AppUserPrincipal;
import com.distribuidora.backend.service.UserAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Usuários")
@RestController
@RequestMapping("/api/users")
@PreAuthorize("hasAuthority('usuarios.gerenciar')")
public class UserAdminController {

    private final UserAdminService userAdminService;

    public UserAdminController(UserAdminService userAdminService) {
        this.userAdminService = userAdminService;
    }

    @Operation(summary = "Lista usuários (busca por nome/usuário, filtro por perfil e situação)")
    @GetMapping
    public PageResponse<UserResponse> search(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) Boolean active,
            @PageableDefault(size = 25, sort = "username", direction = Sort.Direction.ASC) Pageable pageable) {
        return PageResponse.from(userAdminService.search(search, role, active, pageable).map(UserResponse::from));
    }

    @GetMapping("/{id}")
    public UserResponse findOne(@PathVariable Long id) {
        return UserResponse.from(userAdminService.findById(id));
    }

    @PostMapping
    public ResponseEntity<UserResponse> create(@Valid @RequestBody UserCreateRequest request) {
        return ResponseEntity.status(201).body(UserResponse.from(userAdminService.create(request)));
    }

    @PutMapping("/{id}")
    public UserResponse update(
            @PathVariable Long id,
            @Valid @RequestBody UserUpdateRequest request,
            @AuthenticationPrincipal AppUserPrincipal principal) {
        return UserResponse.from(userAdminService.update(id, request, principal.getId()));
    }

    @Operation(summary = "Define uma nova senha para o usuário")
    @PostMapping("/{id}/password")
    public ResponseEntity<Void> resetPassword(@PathVariable Long id, @Valid @RequestBody PasswordResetRequest request) {
        userAdminService.resetPassword(id, request.password());
        return ResponseEntity.noContent().build();
    }
}
