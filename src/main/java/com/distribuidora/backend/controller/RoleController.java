package com.distribuidora.backend.controller;

import com.distribuidora.backend.dto.RoleDtos.PermissionResponse;
import com.distribuidora.backend.dto.RoleDtos.RoleCreateRequest;
import com.distribuidora.backend.dto.RoleDtos.RoleResponse;
import com.distribuidora.backend.dto.RoleDtos.RoleUpdateRequest;
import com.distribuidora.backend.model.Role;
import com.distribuidora.backend.service.RoleService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Perfis e permissões")
@RestController
@RequestMapping("/api")
@PreAuthorize("hasAuthority('usuarios.gerenciar')")
public class RoleController {

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @GetMapping("/roles")
    @Transactional(readOnly = true)
    public List<RoleResponse> roles() {
        return roleService.findAll().stream().map(this::toResponse).toList();
    }

    @GetMapping("/permissions")
    public List<PermissionResponse> permissions() {
        return roleService.findAllPermissions().stream().map(PermissionResponse::from).toList();
    }

    @PostMapping("/roles")
    public ResponseEntity<RoleResponse> create(@Valid @RequestBody RoleCreateRequest request) {
        return ResponseEntity.status(201).body(toResponse(roleService.create(request)));
    }

    @PutMapping("/roles/{code}")
    public RoleResponse update(@PathVariable String code, @Valid @RequestBody RoleUpdateRequest request) {
        return toResponse(roleService.update(code, request));
    }

    @DeleteMapping("/roles/{code}")
    public ResponseEntity<Void> delete(@PathVariable String code) {
        roleService.delete(code);
        return ResponseEntity.noContent().build();
    }

    private RoleResponse toResponse(Role role) {
        return RoleResponse.from(role, roleService.countUsers(role));
    }
}
