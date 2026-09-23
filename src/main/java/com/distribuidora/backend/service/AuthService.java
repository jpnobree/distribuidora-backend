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
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;
    private final JwtService jwtService;
    private final AccessResolver accessResolver;
    private final AuditService auditService;

    public AuthService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            UserDetailsService userDetailsService,
            JwtService jwtService,
            AccessResolver accessResolver,
            AuditService auditService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.userDetailsService = userDetailsService;
        this.jwtService = jwtService;
        this.accessResolver = accessResolver;
        this.auditService = auditService;
    }

    // Cadastro publico (vitrine): sempre perfil CLIENTE, que nao acessa o ERP.
    // Usuarios internos sao criados por quem tem "usuarios.gerenciar".
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new ConflictException("Nome de usuario ja esta em uso");
        }

        Role cliente = roleRepository.findByCode(Role.CLIENTE)
                .orElseThrow(() -> new IllegalStateException("Perfil CLIENTE nao encontrado"));

        User user = new User(request.getUsername(), passwordEncoder.encode(request.getPassword()), request.getEmail());
        user.getRoles().add(cliente);
        userRepository.save(user);

        auditService.recordAs(user.getId(), user.getUsername(), AuditAction.CLIENTE_CADASTRADO_SITE,
                "User", user.getUsername(), null);

        String token = jwtService.generateToken(userDetailsService.loadUserByUsername(user.getUsername()));
        return toResponse(token, user);
    }

    // Sem @Transactional de proposito: a auditoria da tentativa recusada
    // precisa ser gravada mesmo com a excecao propagando.
    public AuthResponse login(LoginRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));
        } catch (AuthenticationException ex) {
            String reason = ex instanceof DisabledException ? "Usuario desativado" : "Usuario ou senha invalidos";
            auditService.recordAs(null, request.getUsername(), AuditAction.LOGIN_FALHA, "User", request.getUsername(), reason);
            throw ex;
        }

        User user = userRepository.findWithAccessByUsername(request.getUsername())
                .orElseThrow(() -> new IllegalStateException("Usuario autenticado mas nao encontrado"));

        userRepository.updateLastLoginAt(user.getId(), Instant.now());
        auditService.recordAs(user.getId(), user.getUsername(), AuditAction.LOGIN_SUCESSO, "User", user.getUsername(), null);

        String token = jwtService.generateToken(userDetailsService.loadUserByUsername(user.getUsername()));
        return toResponse(token, user);
    }

    private AuthResponse toResponse(String token, User user) {
        return new AuthResponse(
                token,
                user.getUsername(),
                user.legacyRole(),
                user.getFullName(),
                user.getRoles().stream().map(Role::getCode).sorted().toList(),
                accessResolver.permissionCodes(user));
    }
}
