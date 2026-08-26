package com.distribuidora.backend.controller;

import com.distribuidora.backend.dto.PriceUpdateRequest;
import com.distribuidora.backend.dto.ProductRequest;
import com.distribuidora.backend.dto.ProductResponse;
import com.distribuidora.backend.model.Product;
import com.distribuidora.backend.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    // Publico: catalogo para qualquer visitante (equivalente a vitrine atual).
    @GetMapping
    public List<ProductResponse> findAll() {
        return productService.findAll().stream().map(ProductResponse::from).toList();
    }

    @GetMapping("/{slug}")
    public ProductResponse findOne(@PathVariable String slug) {
        return ProductResponse.from(productService.findBySlug(slug));
    }

    // A partir daqui, somente ADMIN (ver SecurityConfig).
    @PostMapping
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductRequest request) {
        Product created = productService.create(request);
        return ResponseEntity.status(201).body(ProductResponse.from(created));
    }

    @PutMapping("/{slug}")
    public ProductResponse update(@PathVariable String slug, @Valid @RequestBody ProductRequest request) {
        return ProductResponse.from(productService.update(slug, request));
    }

    // Atalho para o admin alterar so o preco, sem reenviar o produto inteiro.
    @PatchMapping("/{slug}/price")
    public ProductResponse updatePrice(@PathVariable String slug, @RequestBody PriceUpdateRequest request) {
        return ProductResponse.from(productService.updatePrice(slug, request));
    }

    @DeleteMapping("/{slug}")
    public ResponseEntity<Void> delete(@PathVariable String slug) {
        productService.delete(slug);
        return ResponseEntity.noContent().build();
    }
}
