package com.distribuidora.backend.service;

import com.distribuidora.backend.audit.AuditAction;
import com.distribuidora.backend.audit.AuditService;
import com.distribuidora.backend.dto.AuthResponse;
import com.distribuidora.backend.dto.LoginRequest;
import com.distribuidora.backend.dto.RegisterRequest;
import com.distribuidora.backend.exception.ConflictException;
import com.distribuidora.backend.model.Role;
import com.distribuidora.backend.model.User;
import com.distribuidora.backend.repository.RoleRepository;
import com.distribuidora.backend.repository.UserRepository;
import com.distribuidora.backend.security.AccessResolver;
import com.distribuidora.backend.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private UserDetailsService userDetailsService;
    @Mock
    private JwtService jwtService;
    @Mock
    private AccessResolver accessResolver;
    @Mock
    private AuditService auditService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, roleRepository, passwordEncoder, authenticationManager,
                userDetailsService, jwtService, accessResolver, auditService);
    }

    private static UserDetails details(String username) {
        return org.springframework.security.core.userdetails.User
                .withUsername(username).password("hash").authorities("x").build();
    }

    @Test
    void register_deveCriarUsuarioComPerfilCliente_quandoUsernameDisponivel() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("novo_cliente");
        request.setPassword("senha123");
        request.setEmail("cliente@exemplo.com");

        when(userRepository.existsByUsername("novo_cliente")).thenReturn(false);
        when(roleRepository.findByCode(Role.CLIENTE))
                .thenReturn(Optional.of(new Role(Role.CLIENTE, "Cliente (site)", null, true)));
        when(passwordEncoder.encode("senha123")).thenReturn("senha-criptografada");
        when(userDetailsService.loadUserByUsername("novo_cliente")).thenReturn(details("novo_cliente"));
        when(jwtService.generateToken(any(UserDetails.class))).thenReturn("token-gerado");

        AuthResponse response = authService.register(request);

        assertEquals("token-gerado", response.token());
        assertEquals("novo_cliente", response.username());
        assertEquals("USER", response.role());
        assertEquals(List.of(Role.CLIENTE), response.roles());

        // cadastro publico nunca pode criar um usuario interno
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User salvo = userCaptor.getValue();
        assertEquals("senha-criptografada", salvo.getPassword());
        assertEquals(1, salvo.getRoles().size());
        assertTrue(salvo.hasRole(Role.CLIENTE));
    }

    @Test
    void register_deveLancarConflictException_quandoUsernameJaEstaEmUso() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("admin");
        request.setPassword("qualquer123");

        when(userRepository.existsByUsername("admin")).thenReturn(true);

        assertThrows(ConflictException.class, () -> authService.register(request));

        verify(userRepository, never()).save(any());
    }

    @Test
    void login_deveDevolverPerfisEPermissoesMantendoPapelLegado_quandoAdministrador() {
        LoginRequest request = new LoginRequest();
        request.setUsername("admin");
        request.setPassword("admin123");

        User user = new User("admin", "hash", null);
        user.setId(1L);
        user.getRoles().add(new Role(Role.ADMINISTRADOR, "Administrador", null, true));

        when(userRepository.findWithAccessByUsername("admin")).thenReturn(Optional.of(user));
        when(userDetailsService.loadUserByUsername("admin")).thenReturn(details("admin"));
        when(jwtService.generateToken(any(UserDetails.class))).thenReturn("token-admin");
        when(accessResolver.permissionCodes(user)).thenReturn(Set.of("produtos.editar", "usuarios.gerenciar"));

        AuthResponse response = authService.login(request);

        assertEquals("token-admin", response.token());
        assertEquals("ADMIN", response.role());
        assertEquals(List.of(Role.ADMINISTRADOR), response.roles());
        assertTrue(response.permissions().contains("usuarios.gerenciar"));
        verify(userRepository).updateLastLoginAt(eq(1L), any());
        verify(auditService).recordAs(1L, "admin", AuditAction.LOGIN_SUCESSO, "User", "admin", null);
    }

    @Test
    void login_deveAuditarERepropagar_quandoCredenciaisInvalidas() {
        LoginRequest request = new LoginRequest();
        request.setUsername("admin");
        request.setPassword("senha-errada");

        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("invalido"));

        assertThrows(BadCredentialsException.class, () -> authService.login(request));

        verify(auditService).recordAs(isNull(), eq("admin"), eq(AuditAction.LOGIN_FALHA), eq("User"), eq("admin"),
                eq("Usuario ou senha invalidos"));
        verify(userRepository, never()).findWithAccessByUsername(anyString());
    }

    @Test
    void login_deveRegistrarMotivo_quandoUsuarioDesativado() {
        LoginRequest request = new LoginRequest();
        request.setUsername("ex-vendedor");
        request.setPassword("qualquer");

        when(authenticationManager.authenticate(any())).thenThrow(new DisabledException("desativado"));

        assertThrows(DisabledException.class, () -> authService.login(request));

        verify(auditService).recordAs(isNull(), eq("ex-vendedor"), eq(AuditAction.LOGIN_FALHA), eq("User"),
                eq("ex-vendedor"), eq("Usuario desativado"));
    }
}
