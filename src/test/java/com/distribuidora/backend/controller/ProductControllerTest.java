package com.distribuidora.backend.controller;

import com.distribuidora.backend.config.SecurityConfig;
import com.distribuidora.backend.dto.ProductRequest;
import com.distribuidora.backend.model.Product;
import com.distribuidora.backend.security.JwtService;
import com.distribuidora.backend.service.ProductService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Testa a camada HTTP de verdade: rotas, serializacao JSON e as regras reais
// do SecurityConfig (publico vs. ADMIN) - nao so o service isolado.
@WebMvcTest(ProductController.class)
@Import(SecurityConfig.class)
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ProductService productService;

    // dependencias do JwtAuthFilter/SecurityConfig - mockadas so para o
    // contexto de seguranca conseguir montar, sem precisar de JWT real
    @MockBean
    private JwtService jwtService;
    @MockBean
    private UserDetailsService userDetailsService;

    private Product picanha() {
        Product product = new Product();
        product.setSlug("picanha-premium-98562");
        product.setSku("98562");
        product.setName("Picanha Premium");
        product.setCategory("carnes-aves");
        product.setUnit("kg");
        product.setPrice(79.9);
        product.setTags(List.of("Premium"));
        product.setAvailable(true);
        return product;
    }

    @Test
    void listarProdutos_devePermitirAcessoPublico_semAutenticacao() throws Exception {
        when(productService.findAll()).thenReturn(List.of(picanha()));

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("picanha-premium-98562"))
                .andExpect(jsonPath("$[0].name").value("Picanha Premium"));
    }

    @Test
    void criarProduto_deveRejeitar_quandoNaoAutenticado() throws Exception {
        ProductRequest request = validRequest();

        // sem token, o Spring Security trata a requisicao como usuario anonimo:
        // ele "esta autenticado" (como anonimo), so nao tem o papel ADMIN exigido
        // pela rota - por isso a resposta e 403 (Access Denied), nao 401.
        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());

        verify(productService, never()).create(any());
    }

    @Test
    @WithMockUser(roles = "USER")
    void criarProduto_deveRejeitar_quandoUsuarioNaoEhAdmin() throws Exception {
        ProductRequest request = validRequest();

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());

        verify(productService, never()).create(any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void criarProduto_deveCriar_quandoAdmin() throws Exception {
        ProductRequest request = validRequest();
        when(productService.create(any(ProductRequest.class))).thenReturn(picanha());

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("picanha-premium-98562"));

        verify(productService).create(any(ProductRequest.class));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void criarProduto_deveRetornar400_quandoCamposObrigatoriosFaltando() throws Exception {
        ProductRequest request = new ProductRequest(); // sem slug/sku/name/category/unit

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.name").exists());

        verify(productService, never()).create(any());
    }

    private ProductRequest validRequest() {
        ProductRequest request = new ProductRequest();
        request.setSlug("azeitona-verde-99001");
        request.setSku("99001");
        request.setName("Azeitona Verde com Caroço");
        request.setCategory("mercearia");
        request.setUnit("balde 2kg");
        request.setPrice(32.5);
        request.setAvailable(true);
        return request;
    }
}
