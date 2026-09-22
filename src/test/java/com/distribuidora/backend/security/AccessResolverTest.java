package com.distribuidora.backend.security;

import com.distribuidora.backend.model.Permission;
import com.distribuidora.backend.model.Role;
import com.distribuidora.backend.model.User;
import com.distribuidora.backend.repository.PermissionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccessResolverTest {

    private static final Permission EDITAR = new Permission("produtos.editar", "Produtos", "");
    private static final Permission CONTATOS = new Permission("contatos.ver", "Comercial", "");
    private static final Permission AUDITORIA = new Permission("auditoria.ver", "Sistema", "");

    @Mock
    private PermissionRepository permissionRepository;

    @InjectMocks
    private AccessResolver accessResolver;

    @Test
    void administradorRecebeTodaPermissaoExistente_semPrecisarDeConcessao() {
        User admin = new User("admin", "hash", null);
        admin.getRoles().add(new Role(Role.ADMINISTRADOR, "Administrador", null, true));
        when(permissionRepository.findAll()).thenReturn(List.of(EDITAR, CONTATOS, AUDITORIA));

        assertEquals(Set.of("produtos.editar", "contatos.ver", "auditoria.ver"), accessResolver.permissionCodes(admin));
    }

    @Test
    void usuarioComVariosPerfisRecebeAUniaoDasPermissoes() {
        Role gerente = new Role("GERENTE", "Gerente", null, false);
        gerente.setPermissions(Set.of(EDITAR, CONTATOS));
        Role vendedor = new Role("VENDEDOR", "Vendedor", null, false);
        vendedor.setPermissions(Set.of(CONTATOS));
        User user = new User("ana", "hash", null);
        user.getRoles().addAll(List.of(gerente, vendedor));

        assertEquals(Set.of("produtos.editar", "contatos.ver"), accessResolver.permissionCodes(user));
        verify(permissionRepository, never()).findAll();
    }

    @Test
    void clienteDoSiteNaoRecebePermissaoDoErp() {
        User cliente = new User("cliente", "hash", null);
        cliente.getRoles().add(new Role(Role.CLIENTE, "Cliente", null, true));

        assertEquals(Set.of(), accessResolver.permissionCodes(cliente));
    }
}
