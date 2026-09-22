package com.distribuidora.backend.cadastros;

import com.distribuidora.backend.cadastros.ProductDtos.ErpProductRequest;
import com.distribuidora.backend.cadastros.ProductDtos.ErpProductResponse;
import com.distribuidora.backend.dto.PageResponse;
import com.distribuidora.backend.model.Product;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

// Cadastro completo de produto (o /api/products continua sendo o catalogo
// publico da vitrine, com os mesmos produtos).
@Tag(name = "Produtos (ERP)")
@RestController
@RequestMapping("/api/erp/products")
public class ErpProductController {

    private final ErpProductService productService;

    public ErpProductController(ErpProductService productService) {
        this.productService = productService;
    }

    @PreAuthorize("hasAuthority('produtos.ver')")
    @GetMapping
    public PageResponse<ErpProductResponse> search(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) Product.StorageType storageType,
            @RequestParam(required = false) Boolean active,
            @PageableDefault(size = 50, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return PageResponse.from(productService.search(search, category, supplierId, storageType, active, pageable));
    }

    @PreAuthorize("hasAuthority('produtos.ver')")
    @GetMapping("/{id}")
    public ErpProductResponse findOne(@PathVariable Long id) {
        return productService.findOne(id);
    }

    @PreAuthorize("hasAuthority('produtos.editar')")
    @PostMapping
    public ResponseEntity<ErpProductResponse> create(@Valid @RequestBody ErpProductRequest request) {
        return ResponseEntity.status(201).body(productService.create(request));
    }

    @PreAuthorize("hasAuthority('produtos.editar')")
    @PutMapping("/{id}")
    public ErpProductResponse update(@PathVariable Long id, @Valid @RequestBody ErpProductRequest request) {
        return productService.update(id, request);
    }
}
