package com.distribuidora.backend.controller;

import com.distribuidora.backend.dto.PageResponse;
import com.distribuidora.backend.dto.PriceUpdateRequest;
import com.distribuidora.backend.dto.ProductRequest;
import com.distribuidora.backend.dto.ProductResponse;
import com.distribuidora.backend.model.Product;
import com.distribuidora.backend.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Produtos", description = "Catalogo publico e gestao de produtos (ADMIN)")
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    // Publico: catalogo para qualquer visitante. Paginado (padrao: 100 itens
    // por pagina, o suficiente para o catalogo atual sem quebrar quem ainda
    // nao usa "page"/"size"); category e search sao opcionais.
    @Operation(summary = "Lista o catalogo (publico, paginado)")
    @GetMapping
    public PageResponse<ProductResponse> findAll(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 100) Pageable pageable) {
        Page<Product> page = productService.findAll(category, search, pageable);
        return PageResponse.from(page.map(ProductResponse::from));
    }

    @Operation(summary = "Detalhe de um produto (publico)")
    @GetMapping("/{slug}")
    public ProductResponse findOne(@PathVariable String slug) {
        return ProductResponse.from(productService.findBySlug(slug));
    }

    // A partir daqui, somente ADMIN (ver SecurityConfig).
    @Operation(summary = "Cria um produto (ADMIN)")
    @PostMapping
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductRequest request) {
        Product created = productService.create(request);
        return ResponseEntity.status(201).body(ProductResponse.from(created));
    }

    @Operation(summary = "Atualiza um produto (ADMIN)")
    @PutMapping("/{slug}")
    public ProductResponse update(@PathVariable String slug, @Valid @RequestBody ProductRequest request) {
        return ProductResponse.from(productService.update(slug, request));
    }

    // Atalho para o admin alterar so o preco, sem reenviar o produto inteiro.
    @Operation(summary = "Atalho: altera so o preco (ADMIN)")
    @PatchMapping("/{slug}/price")
    public ProductResponse updatePrice(@PathVariable String slug, @RequestBody PriceUpdateRequest request) {
        return ProductResponse.from(productService.updatePrice(slug, request));
    }

    @Operation(summary = "Remove um produto (ADMIN)")
    @DeleteMapping("/{slug}")
    public ResponseEntity<Void> delete(@PathVariable String slug) {
        productService.delete(slug);
        return ResponseEntity.noContent().build();
    }
}
