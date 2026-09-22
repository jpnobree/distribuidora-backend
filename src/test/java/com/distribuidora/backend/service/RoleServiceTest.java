package com.distribuidora.backend.service;

import com.distribuidora.backend.audit.AuditService;
import com.distribuidora.backend.dto.RoleDtos.RoleUpdateRequest;
import com.distribuidora.backend.exception.BusinessRuleException;
import com.distribuidora.backend.model.Permission;
import com.distribuidora.backend.model.Role;
import com.distribuidora.backend.repository.PermissionRepository;
import com.distribuidora.backend.repository.RoleRepository;
import com.distribuidora.backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoleServiceTest {

    @Mock
    private RoleRepository roleRepository;
    @Mock
    private PermissionRepository permissionRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AuditService auditService;

    @InjectMocks
    private RoleService roleService;

    @Test
    void update_deveRecusarAlterarAdministrador() {
        when(roleRepository.findWithPermissionsByCode(Role.ADMINISTRADOR))
                .thenReturn(Optional.of(new Role(Role.ADMINISTRADOR, "Administrador", null, true)));

        assertThrows(BusinessRuleException.class, () -> roleService.update(
                Role.ADMINISTRADOR, new RoleUpdateRequest("Admin", null, Set.of())));
        verify(auditService, never()).recordChange(any(), any(), any(), any(), any(), any());
    }

    @Test
    void update_deveTrocarPermissoes_quandoTodasExistem() {
        Role gerente = new Role("GERENTE", "Gerente", null, false);
        Permission editar = new Permission("produtos.editar", "Produtos", "Editar produtos");
        when(roleRepository.findWithPermissionsByCode("GERENTE")).thenReturn(Optional.of(gerente));
        when(permissionRepository.findAllById(Set.of("produtos.editar"))).thenReturn(List.of(editar));

        Role updated = roleService.update("GERENTE", new RoleUpdateRequest("Gerente", null, Set.of("produtos.editar")));

        assertEquals(Set.of(editar), updated.getPermissions());
    }

    @Test
    void update_deveRecusarPermissaoInexistente() {
        when(roleRepository.findWithPermissionsByCode("GERENTE"))
                .thenReturn(Optional.of(new Role("GERENTE", "Gerente", null, false)));
        when(permissionRepository.findAllById(Set.of("financeiro.tudo"))).thenReturn(List.of());

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> roleService.update(
                "GERENTE", new RoleUpdateRequest("Gerente", null, Set.of("financeiro.tudo"))));
        assertEquals("Permissao inexistente: financeiro.tudo", ex.getMessage());
    }

    @Test
    void delete_deveRecusarPerfilDoSistema() {
        when(roleRepository.findWithPermissionsByCode(Role.CLIENTE))
                .thenReturn(Optional.of(new Role(Role.CLIENTE, "Cliente", null, true)));

        assertThrows(BusinessRuleException.class, () -> roleService.delete(Role.CLIENTE));
        verify(roleRepository, never()).delete(any());
    }

    @Test
    void delete_deveRecusarPerfilComUsuarios() {
        Role promotor = new Role("PROMOTOR", "Promotor", null, false);
        promotor.setId(20L);
        when(roleRepository.findWithPermissionsByCode("PROMOTOR")).thenReturn(Optional.of(promotor));
        when(userRepository.countByRoleId(20L)).thenReturn(3L);

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> roleService.delete("PROMOTOR"));
        assertEquals("Existem 3 usuario(s) com este perfil. Troque o perfil deles antes de excluir.", ex.getMessage());
        verify(roleRepository, never()).delete(any());
    }
}
