package com.distribuidora.backend.service;

import com.distribuidora.backend.audit.AuditAction;
import com.distribuidora.backend.audit.AuditService;
import com.distribuidora.backend.dto.UserAdminDtos.UserCreateRequest;
import com.distribuidora.backend.dto.UserAdminDtos.UserUpdateRequest;
import com.distribuidora.backend.exception.BusinessRuleException;
import com.distribuidora.backend.exception.ConflictException;
import com.distribuidora.backend.exception.ResourceNotFoundException;
import com.distribuidora.backend.model.Role;
import com.distribuidora.backend.model.User;
import com.distribuidora.backend.repository.RoleRepository;
import com.distribuidora.backend.repository.UserRepository;
import com.distribuidora.backend.repository.UserSpecifications;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

@Service
public class UserAdminService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public UserAdminService(UserRepository userRepository, RoleRepository roleRepository,
                            PasswordEncoder passwordEncoder, AuditService auditService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public Page<User> search(String search, String roleCode, Boolean active, Pageable pageable) {
        Specification<User> spec = Specification.where(null);
        if (search != null && !search.isBlank()) {
            spec = spec.and(UserSpecifications.matchesSearch(search.trim()));
        }
        if (roleCode != null && !roleCode.isBlank()) {
            spec = spec.and(UserSpecifications.hasRole(roleCode));
        }
        if (active != null) {
            spec = spec.and(UserSpecifications.isActive(active));
        }
        Page<User> page = userRepository.findAll(spec, pageable);
        page.getContent().forEach(user -> user.getRoles().size());
        return page;
    }

    @Transactional(readOnly = true)
    public User findById(Long id) {
        return userRepository.findWithRolesById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario nao encontrado: " + id));
    }

    @Transactional
    public User create(UserCreateRequest request) {
        String username = request.username().trim();
        if (userRepository.existsByUsername(username)) {
            throw new ConflictException("Nome de usuario ja esta em uso");
        }

        User user = new User(username, passwordEncoder.encode(request.password()), blankToNull(request.email()));
        user.setFullName(request.fullName().trim());
        user.setRoles(resolveRoles(request.roleCodes()));
        userRepository.save(user);

        auditService.recordChange(AuditAction.USUARIO_CRIADO, "User", user.getUsername(), null, snapshot(user), null);
        return user;
    }

    @Transactional
    public User update(Long id, UserUpdateRequest request, Long actorId) {
        User user = findById(id);
        Map<String, Object> before = snapshot(user);
        Set<Role> newRoles = resolveRoles(request.roleCodes());
        boolean willBeAdmin = newRoles.stream().anyMatch(Role::isAdministrator);

        if (id.equals(actorId)) {
            if (!request.active()) {
                throw new BusinessRuleException("Voce nao pode desativar o proprio usuario.");
            }
            if (user.isAdministrator() && !willBeAdmin) {
                throw new BusinessRuleException("Voce nao pode remover o perfil Administrador de si mesmo.");
            }
        }

        boolean isActiveAdmin = user.isActive() && user.isAdministrator();
        boolean willBeActiveAdmin = request.active() && willBeAdmin;
        if (isActiveAdmin && !willBeActiveAdmin && userRepository.countOtherActiveAdministrators(id) == 0) {
            throw new BusinessRuleException("O sistema precisa manter pelo menos um administrador ativo.");
        }

        user.setFullName(request.fullName().trim());
        user.setEmail(blankToNull(request.email()));
        user.setActive(request.active());
        user.setRoles(newRoles);

        auditService.recordChange(AuditAction.USUARIO_ALTERADO, "User", user.getUsername(), before, snapshot(user), null);
        return user;
    }

    @Transactional
    public void resetPassword(Long id, String newPassword) {
        User user = findById(id);
        user.setPassword(passwordEncoder.encode(newPassword));
        auditService.record(AuditAction.USUARIO_SENHA_REDEFINIDA, "User", user.getUsername(), null);
    }

    private Set<Role> resolveRoles(Set<String> codes) {
        List<Role> roles = roleRepository.findByCodeIn(codes);
        if (roles.size() != codes.size()) {
            Set<String> missing = new TreeSet<>(codes);
            roles.forEach(role -> missing.remove(role.getCode()));
            throw new BusinessRuleException("Perfil inexistente: " + String.join(", ", missing));
        }
        return new HashSet<>(roles);
    }

    private static Map<String, Object> snapshot(User user) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("username", user.getUsername());
        values.put("fullName", user.getFullName());
        values.put("email", user.getEmail());
        values.put("active", user.isActive());
        values.put("roles", user.getRoles().stream().map(Role::getCode).sorted().toList());
        return values;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
