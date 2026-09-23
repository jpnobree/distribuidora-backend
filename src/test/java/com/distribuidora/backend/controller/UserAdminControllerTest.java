package com.distribuidora.backend.controller;

import com.distribuidora.backend.config.SecurityConfig;
import com.distribuidora.backend.model.User;
import com.distribuidora.backend.security.JwtService;
import com.distribuidora.backend.service.UserAdminService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserAdminController.class)
@Import(SecurityConfig.class)
class UserAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserAdminService userAdminService;
    @MockBean
    private JwtService jwtService;
    @MockBean
    private UserDetailsService userDetailsService;

    @Test
    @WithMockUser(authorities = {"contatos.ver", "produtos.editar"})
    void listarUsuarios_deveNegar_quandoFaltaPermissaoUsuariosGerenciar() throws Exception {
        mockMvc.perform(get("/api/users")).andExpect(status().isForbidden());

        verify(userAdminService, never()).search(any(), any(), any(), any());
    }

    @Test
    @WithMockUser(authorities = "usuarios.gerenciar")
    void listarUsuarios_devePermitir_quandoTemPermissao() throws Exception {
        User vendedor = new User("maria", "hash", null);
        vendedor.setId(5L);
        vendedor.setFullName("Maria Souza");
        when(userAdminService.search(isNull(), isNull(), isNull(), any())).thenReturn(new PageImpl<>(List.of(vendedor)));

        mockMvc.perform(get("/api/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].username").value("maria"))
                // o hash da senha nunca sai na API
                .andExpect(jsonPath("$.content[0].password").doesNotExist());
    }
}
