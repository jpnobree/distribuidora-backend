package com.distribuidora.backend.controller;

import com.distribuidora.backend.dto.PageResponse;
import com.distribuidora.backend.dto.PriceUpdateRequest;
import com.distribuidora.backend.dto.ProductRequest;
import com.distribuidora.backend.dto.ProductResponse;
import com.distribuidora.backend.estoque.StockQueryService;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Tag(name = "Produtos", description = "Catalogo publico e gestao de produtos (ADMIN)")
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;
    private final StockQueryService stockQueryService;

    public ProductController(ProductService productService, StockQueryService stockQueryService) {
        this.productService = productService;
        this.stockQueryService = stockQueryService;
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
        Map<Long, BigDecimal> stock = stockQueryService.availableFor(page.map(Product::getId).getContent());
        return PageResponse.from(page.map(p -> ProductResponse.from(p, stock.getOrDefault(p.getId(), BigDecimal.ZERO))));
    }

    @Operation(summary = "Detalhe de um produto (publico)")
    @GetMapping("/{slug}")
    public ProductResponse findOne(@PathVariable String slug) {
        Product product = productService.findBySlug(slug);
        return ProductResponse.from(product,
                stockQueryService.availableFor(List.of(product.getId())).getOrDefault(product.getId(), BigDecimal.ZERO));
    }

    // A partir daqui, exige a permissao "produtos.editar" (ver SecurityConfig).
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
    public ProductResponse updatePrice(@PathVariable String slug, @Valid @RequestBody PriceUpdateRequest request) {
        return ProductResponse.from(productService.updatePrice(slug, request));
    }

    @Operation(summary = "Remove um produto (ADMIN)")
    @DeleteMapping("/{slug}")
    public ResponseEntity<Void> delete(@PathVariable String slug) {
        productService.delete(slug);
        return ResponseEntity.noContent().build();
    }
}
