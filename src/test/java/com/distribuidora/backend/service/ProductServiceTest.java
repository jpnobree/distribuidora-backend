package com.distribuidora.backend.service;

import com.distribuidora.backend.exception.ResourceNotFoundException;
import com.distribuidora.backend.model.Product;
import com.distribuidora.backend.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

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
}
