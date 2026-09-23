package com.distribuidora.backend.controller;

import com.distribuidora.backend.config.SecurityConfig;
import com.distribuidora.backend.dto.AuthResponse;
import com.distribuidora.backend.security.JwtService;
import com.distribuidora.backend.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Confere que o LoginRateLimitFilter conta as tentativas corretamente: o
// filtro tambem e um bean Filter, e o Spring Boot registra beans Filter
// automaticamente como filtro de servlet generico - sem desabilitar esse
// registro (ver SecurityConfig.loginRateLimitFilterRegistration), o filtro
// rodaria 2x por requisicao e o limite estouraria na metade das tentativas
// configuradas. Este teste prova, na pratica, que isso nao acontece.
@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class LoginRateLimitFilterTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @MockBean
    private JwtService jwtService;
    @MockBean
    private UserDetailsService userDetailsService;

    @Test
    void login_devePermitirExatamenteAsTentativasConfiguradas_eBloquearDepois() throws Exception {
        when(authService.login(any())).thenReturn(new AuthResponse("token", "admin", "ADMIN", null, java.util.List.of(), java.util.Set.of()));

        String body = objectMapper.writeValueAsString(new Object() {
            public final String username = "admin";
            public final String password = "admin123";
        });

        // app.rate-limit.login.capacity=5 (application.properties) - as 5
        // primeiras tentativas devem passar...
        for (int i = 1; i <= 5; i++) {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk());
        }

        // ...e a 6a, na mesma janela, deve ser bloqueada com 429.
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().is(429));
    }
}
