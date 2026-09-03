package com.distribuidora.backend.service;

import com.distribuidora.backend.dto.AuthResponse;
import com.distribuidora.backend.dto.LoginRequest;
import com.distribuidora.backend.dto.RegisterRequest;
import com.distribuidora.backend.exception.ConflictException;
import com.distribuidora.backend.model.Role;
import com.distribuidora.backend.model.User;
import com.distribuidora.backend.repository.UserRepository;
import com.distribuidora.backend.security.JwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private UserDetailsService userDetailsService;
    @Mock
    private JwtService jwtService;

    private AuthService authService;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        authService = new AuthService(
                userRepository, passwordEncoder, authenticationManager, userDetailsService, jwtService);
    }

    @Test
    void register_deveCriarUsuarioComPapelUser_quandoUsernameDisponivel() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("novo_cliente");
        request.setPassword("senha123");
        request.setEmail("cliente@exemplo.com");

        when(userRepository.existsByUsername("novo_cliente")).thenReturn(false);
        when(passwordEncoder.encode("senha123")).thenReturn("senha-criptografada");
        when(jwtService.generateToken(any(UserDetails.class), eq("USER"))).thenReturn("token-gerado");

        AuthResponse response = authService.register(request);

        assertEquals("token-gerado", response.getToken());
        assertEquals("novo_cliente", response.getUsername());
        assertEquals("USER", response.getRole());

        // confere que o usuario foi salvo com a senha ja criptografada e papel USER
        // (nunca ADMIN - conta admin nao e auto-cadastravel)
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User salvo = userCaptor.getValue();
        assertEquals("senha-criptografada", salvo.getPassword());
        assertEquals(Role.USER, salvo.getRole());
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
    void login_deveAutenticarEDevolverToken_quandoCredenciaisValidas() {
        LoginRequest request = new LoginRequest();
        request.setUsername("admin");
        request.setPassword("admin123");

        User user = new User("admin", "hash", null, Role.ADMIN);
        UserDetails userDetails = org.springframework.security.core.userdetails.User
                .withUsername("admin").password("hash").authorities("ROLE_ADMIN").build();

        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user));
        when(userDetailsService.loadUserByUsername("admin")).thenReturn(userDetails);
        when(jwtService.generateToken(userDetails, "ADMIN")).thenReturn("token-admin");

        AuthResponse response = authService.login(request);

        assertEquals("token-admin", response.getToken());
        assertEquals("admin", response.getUsername());
        assertEquals("ADMIN", response.getRole());
    }

    @Test
    void login_devePropagarExcecao_quandoCredenciaisInvalidas() {
        LoginRequest request = new LoginRequest();
        request.setUsername("admin");
        request.setPassword("senha-errada");

        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("invalido"));

        assertThrows(BadCredentialsException.class, () -> authService.login(request));

        // se a autenticacao falhou, o service nao deve nem consultar o usuario
        verify(userRepository, never()).findByUsername(anyString());
    }
}
