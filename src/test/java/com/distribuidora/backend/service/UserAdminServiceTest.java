package com.distribuidora.backend.service;

import com.distribuidora.backend.audit.AuditAction;
import com.distribuidora.backend.audit.AuditService;
import com.distribuidora.backend.dto.UserAdminDtos.UserCreateRequest;
import com.distribuidora.backend.dto.UserAdminDtos.UserUpdateRequest;
import com.distribuidora.backend.exception.BusinessRuleException;
import com.distribuidora.backend.exception.ConflictException;
import com.distribuidora.backend.model.Role;
import com.distribuidora.backend.model.User;
import com.distribuidora.backend.repository.RoleRepository;
import com.distribuidora.backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAdminServiceTest {

    private static final Role ADMIN = new Role(Role.ADMINISTRADOR, "Administrador", null, true);
    private static final Role VENDEDOR = new Role("VENDEDOR", "Vendedor", null, false);

    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuditService auditService;

    @InjectMocks
    private UserAdminService userAdminService;

    private User user(long id, boolean active, Role... roles) {
        User user = new User("user" + id, "hash", null);
        user.setId(id);
        user.setFullName("Usuario " + id);
        user.setActive(active);
        user.getRoles().addAll(List.of(roles));
        return user;
    }

    @Test
    void create_deveCriptografarSenhaEAuditarSemGravarASenha() {
        when(userRepository.existsByUsername("maria.vendas")).thenReturn(false);
        when(roleRepository.findByCodeIn(Set.of("VENDEDOR"))).thenReturn(List.of(VENDEDOR));
        when(passwordEncoder.encode("senhaForte1")).thenReturn("hash-bcrypt");

        User created = userAdminService.create(new UserCreateRequest(
                "maria.vendas", "Maria Souza", "maria@realfrios.com.br", "senhaForte1", Set.of("VENDEDOR")));

        assertEquals("hash-bcrypt", created.getPassword());
        assertTrue(created.hasRole("VENDEDOR"));
        verify(auditService).recordChange(eq(AuditAction.USUARIO_CRIADO), eq("User"), any(), isNull(),
                eq(Map.of(
                        "username", "maria.vendas",
                        "fullName", "Maria Souza",
                        "email", "maria@realfrios.com.br",
                        "active", true,
                        "roles", List.of("VENDEDOR"))),
                isNull());
    }

    @Test
    void create_deveRecusar_quandoUsernameJaExiste() {
        when(userRepository.existsByUsername("admin")).thenReturn(true);

        assertThrows(ConflictException.class, () -> userAdminService.create(
                new UserCreateRequest("admin", "Outro", null, "senhaForte1", Set.of("VENDEDOR"))));
        verify(userRepository, never()).save(any());
    }

    @Test
    void create_deveRecusar_quandoPerfilNaoExiste() {
        when(userRepository.existsByUsername("joao")).thenReturn(false);
        when(roleRepository.findByCodeIn(Set.of("VENDEDOR", "INEXISTENTE"))).thenReturn(List.of(VENDEDOR));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> userAdminService.create(
                new UserCreateRequest("joao", "Joao", null, "senhaForte1", Set.of("VENDEDOR", "INEXISTENTE"))));
        assertEquals("Perfil inexistente: INEXISTENTE", ex.getMessage());
    }

    @Test
    void update_deveImpedirQueUsuarioSeDesative() {
        User admin = user(1, true, ADMIN);
        when(userRepository.findWithRolesById(1L)).thenReturn(Optional.of(admin));
        when(roleRepository.findByCodeIn(Set.of(Role.ADMINISTRADOR))).thenReturn(List.of(ADMIN));

        assertThrows(BusinessRuleException.class, () -> userAdminService.update(
                1L, new UserUpdateRequest("Admin", null, Set.of(Role.ADMINISTRADOR), false), 1L));
        assertTrue(admin.isActive());
    }

    @Test
    void update_deveImpedirQueAdministradorRemovaOProprioPerfil() {
        User admin = user(1, true, ADMIN);
        when(userRepository.findWithRolesById(1L)).thenReturn(Optional.of(admin));
        when(roleRepository.findByCodeIn(Set.of("VENDEDOR"))).thenReturn(List.of(VENDEDOR));

        assertThrows(BusinessRuleException.class, () -> userAdminService.update(
                1L, new UserUpdateRequest("Admin", null, Set.of("VENDEDOR"), true), 1L));
        assertTrue(admin.isAdministrator());
    }

    @Test
    void update_deveImpedirDesativarOUltimoAdministradorAtivo() {
        User outroAdmin = user(2, true, ADMIN);
        when(userRepository.findWithRolesById(2L)).thenReturn(Optional.of(outroAdmin));
        when(roleRepository.findByCodeIn(Set.of(Role.ADMINISTRADOR))).thenReturn(List.of(ADMIN));
        when(userRepository.countOtherActiveAdministrators(2L)).thenReturn(0L);

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> userAdminService.update(
                2L, new UserUpdateRequest("Admin 2", null, Set.of(Role.ADMINISTRADOR), false), 1L));
        assertEquals("O sistema precisa manter pelo menos um administrador ativo.", ex.getMessage());
    }

    @Test
    void update_devePermitirDesativarAdministrador_quandoExisteOutroAtivo() {
        User outroAdmin = user(2, true, ADMIN);
        when(userRepository.findWithRolesById(2L)).thenReturn(Optional.of(outroAdmin));
        when(roleRepository.findByCodeIn(Set.of(Role.ADMINISTRADOR))).thenReturn(List.of(ADMIN));
        when(userRepository.countOtherActiveAdministrators(2L)).thenReturn(1L);

        userAdminService.update(2L, new UserUpdateRequest("Admin 2", null, Set.of(Role.ADMINISTRADOR), false), 1L);

        assertFalse(outroAdmin.isActive());
        verify(auditService).recordChange(eq(AuditAction.USUARIO_ALTERADO), eq("User"), eq("user2"), any(), any(),
                isNull());
    }

    @Test
    void update_naoConsultaAdministradores_quandoUsuarioNaoEhAdministrador() {
        User vendedor = user(3, true, VENDEDOR);
        when(userRepository.findWithRolesById(3L)).thenReturn(Optional.of(vendedor));
        when(roleRepository.findByCodeIn(Set.of("VENDEDOR"))).thenReturn(List.of(VENDEDOR));

        userAdminService.update(3L, new UserUpdateRequest("Vendedor", null, Set.of("VENDEDOR"), false), 1L);

        assertFalse(vendedor.isActive());
        verify(userRepository, never()).countOtherActiveAdministrators(anyLong());
    }
}
