package com.distribuidora.backend.service;

import com.distribuidora.backend.audit.AuditAction;
import com.distribuidora.backend.audit.AuditService;
import com.distribuidora.backend.dto.RoleDtos.RoleCreateRequest;
import com.distribuidora.backend.dto.RoleDtos.RoleUpdateRequest;
import com.distribuidora.backend.exception.BusinessRuleException;
import com.distribuidora.backend.exception.ConflictException;
import com.distribuidora.backend.exception.ResourceNotFoundException;
import com.distribuidora.backend.model.Permission;
import com.distribuidora.backend.model.Role;
import com.distribuidora.backend.repository.PermissionRepository;
import com.distribuidora.backend.repository.RoleRepository;
import com.distribuidora.backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

@Service
public class RoleService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public RoleService(RoleRepository roleRepository, PermissionRepository permissionRepository,
                       UserRepository userRepository, AuditService auditService) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<Role> findAll() {
        return roleRepository.findAllByOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public List<Permission> findAllPermissions() {
        return permissionRepository.findAllByOrderByModuleAscCodeAsc();
    }

    public long countUsers(Role role) {
        return userRepository.countByRoleId(role.getId());
    }

    @Transactional
    public Role create(RoleCreateRequest request) {
        if (roleRepository.existsByCode(request.code())) {
            throw new ConflictException("Ja existe um perfil com o codigo " + request.code());
        }
        Role role = new Role(request.code(), request.name().trim(), blankToNull(request.description()), false);
        role.setPermissions(resolvePermissions(request.permissions()));
        roleRepository.save(role);

        auditService.recordChange(AuditAction.PERFIL_CRIADO, "Role", role.getCode(), null, snapshot(role), null);
        return role;
    }

    @Transactional
    public Role update(String code, RoleUpdateRequest request) {
        Role role = findByCode(code);
        if (role.isAdministrator()) {
            throw new BusinessRuleException(
                    "O perfil Administrador sempre tem todas as permissoes e nao pode ser alterado.");
        }
        Map<String, Object> before = snapshot(role);

        role.setName(request.name().trim());
        role.setDescription(blankToNull(request.description()));
        role.setPermissions(resolvePermissions(request.permissions()));

        auditService.recordChange(AuditAction.PERFIL_ALTERADO, "Role", role.getCode(), before, snapshot(role), null);
        return role;
    }

    @Transactional
    public void delete(String code) {
        Role role = findByCode(code);
        if (role.isSystemRole()) {
            throw new BusinessRuleException("Perfis do sistema nao podem ser excluidos.");
        }
        long users = countUsers(role);
        if (users > 0) {
            throw new BusinessRuleException(
                    "Existem " + users + " usuario(s) com este perfil. Troque o perfil deles antes de excluir.");
        }
        auditService.recordChange(AuditAction.PERFIL_EXCLUIDO, "Role", role.getCode(), snapshot(role), null, null);
        roleRepository.delete(role);
    }

    private Role findByCode(String code) {
        return roleRepository.findWithPermissionsByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("Perfil nao encontrado: " + code));
    }

    private Set<Permission> resolvePermissions(Set<String> codes) {
        List<Permission> permissions = permissionRepository.findAllById(codes);
        if (permissions.size() != codes.size()) {
            Set<String> missing = new TreeSet<>(codes);
            permissions.forEach(permission -> missing.remove(permission.getCode()));
            throw new BusinessRuleException("Permissao inexistente: " + String.join(", ", missing));
        }
        return new HashSet<>(permissions);
    }

    private static Map<String, Object> snapshot(Role role) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("name", role.getName());
        values.put("description", role.getDescription());
        values.put("permissions", role.getPermissions().stream().map(Permission::getCode).sorted().toList());
        return values;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
