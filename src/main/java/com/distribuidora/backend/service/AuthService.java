package com.distribuidora.backend.service;

import com.distribuidora.backend.dto.AuthResponse;
import com.distribuidora.backend.dto.LoginRequest;
import com.distribuidora.backend.dto.RegisterRequest;
import com.distribuidora.backend.exception.ConflictException;
import com.distribuidora.backend.model.Role;
import com.distribuidora.backend.model.User;
import com.distribuidora.backend.repository.UserRepository;
import com.distribuidora.backend.security.JwtService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;
    private final JwtService jwtService;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            UserDetailsService userDetailsService,
            JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.userDetailsService = userDetailsService;
        this.jwtService = jwtService;
    }

    // Cadastro publico: sempre cria papel USER. Contas ADMIN sao criadas via
    // seed (application.properties) ou diretamente no banco por seguranca.
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new ConflictException("Nome de usuario ja esta em uso");
        }

        User user = new User(
                request.getUsername(),
                passwordEncoder.encode(request.getPassword()),
                request.getEmail(),
                Role.USER);
        userRepository.save(user);

        String token = jwtService.generateToken(
                org.springframework.security.core.userdetails.User.withUsername(user.getUsername())
                        .password(user.getPassword())
                        .authorities("ROLE_" + user.getRole().name())
                        .build(),
                user.getRole().name());

        return new AuthResponse(token, user.getUsername(), user.getRole().name());
    }

    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));

        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new IllegalStateException("Usuario autenticado mas nao encontrado"));

        String token = jwtService.generateToken(userDetailsService.loadUserByUsername(user.getUsername()), user.getRole().name());
        return new AuthResponse(token, user.getUsername(), user.getRole().name());
    }
}
