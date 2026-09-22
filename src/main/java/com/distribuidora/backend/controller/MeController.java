package com.distribuidora.backend.controller;

import com.distribuidora.backend.dto.MeResponse;
import com.distribuidora.backend.dto.RoleSummary;
import com.distribuidora.backend.model.User;
import com.distribuidora.backend.repository.UserRepository;
import com.distribuidora.backend.security.AccessResolver;
import com.distribuidora.backend.security.AppUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;

@Tag(name = "Sessão")
@RestController
public class MeController {

    private final UserRepository userRepository;
    private final AccessResolver accessResolver;

    public MeController(UserRepository userRepository, AccessResolver accessResolver) {
        this.userRepository = userRepository;
        this.accessResolver = accessResolver;
    }

    @Operation(summary = "Dados, perfis e permissões do usuário logado")
    @GetMapping("/api/me")
    @Transactional(readOnly = true)
    public MeResponse me(@AuthenticationPrincipal AppUserPrincipal principal) {
        User user = userRepository.findWithAccessByUsername(principal.getUsername()).orElseThrow();
        return new MeResponse(
                user.getId(),
                user.getUsername(),
                user.getFullName(),
                user.getEmail(),
                user.getRoles().stream()
                        .map(RoleSummary::from)
                        .sorted(Comparator.comparing(RoleSummary::name))
                        .toList(),
                accessResolver.permissionCodes(user));
    }
}
