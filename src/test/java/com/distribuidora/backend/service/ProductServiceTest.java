package com.distribuidora.backend.service;

import com.distribuidora.backend.audit.AuditAction;
import com.distribuidora.backend.audit.AuditService;
import com.distribuidora.backend.dto.PriceUpdateRequest;
import com.distribuidora.backend.exception.ResourceNotFoundException;
import com.distribuidora.backend.model.Product;
import com.distribuidora.backend.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;
    @Mock
    private AuditService auditService;

    @InjectMocks
    private ProductService productService;

    @Test
    void findBySlug_deveRetornarProduto_quandoSlugExiste() {
        String slug = "picanha-premium-98562";
        Product produtoExistente = new Product();
        produtoExistente.setSlug(slug);
        produtoExistente.setName("Picanha Premium");

        when(productRepository.findBySlug(slug)).thenReturn(Optional.of(produtoExistente));

        Product resultado = productService.findBySlug(slug);

        assertEquals("Picanha Premium", resultado.getName());
        assertEquals(slug, resultado.getSlug());
        verify(productRepository).findBySlug(slug);
    }

    @Test
    void findBySlug_deveLancarResourceNotFoundException_quandoSlugNaoExiste() {
        // Arrange: o mock simula "nao achei nada no banco"
        String slugInexistente = "produto-que-nao-existe";
        when(productRepository.findBySlug(slugInexistente)).thenReturn(Optional.empty());

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> productService.findBySlug(slugInexistente)
        );

        assertEquals("Produto nao encontrado: " + slugInexistente, exception.getMessage());
        verify(productRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void updatePrice_deveAuditarPrecoAnteriorENovo() {
        Product produto = new Product();
        produto.setSlug("picanha-premium-98562");
        produto.setPrice(new BigDecimal("79.90"));
        when(productRepository.findBySlug("picanha-premium-98562")).thenReturn(Optional.of(produto));
        when(productRepository.save(produto)).thenReturn(produto);

        PriceUpdateRequest request = new PriceUpdateRequest();
        request.setPrice(new BigDecimal("84.50"));

        productService.updatePrice("picanha-premium-98562", request);

        verify(auditService).recordChange(
                eq(AuditAction.PRODUTO_PRECO_ALTERADO),
                eq("Product"),
                eq("picanha-premium-98562"),
                eq(Map.of("price", new BigDecimal("79.90"))),
                eq(Map.of("price", new BigDecimal("84.50"))),
                isNull());
    }
}
