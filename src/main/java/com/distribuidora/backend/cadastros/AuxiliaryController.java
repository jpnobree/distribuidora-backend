package com.distribuidora.backend.cadastros;

import com.distribuidora.backend.cadastros.AuxDtos.CategoryNode;
import com.distribuidora.backend.cadastros.AuxDtos.NamedItem;
import com.distribuidora.backend.cadastros.AuxDtos.NamedItemRequest;
import com.distribuidora.backend.cadastros.AuxDtos.PaymentTermRequest;
import com.distribuidora.backend.cadastros.AuxDtos.PaymentTermResponse;
import com.distribuidora.backend.cadastros.AuxDtos.SubcategoryRequest;
import com.distribuidora.backend.cadastros.AuxDtos.UnitResponse;
import com.distribuidora.backend.repository.CategoryRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// Leitura: qualquer usuario logado (os formularios de cadastro precisam das
// listas). Escrita: "cadastros.configurar".
@Tag(name = "Tabelas auxiliares")
@RestController
@RequestMapping("/api/erp")
public class AuxiliaryController {

    private static final String CONFIGURAR = "hasAuthority('cadastros.configurar')";

    private final AuxiliaryService auxiliaryService;
    private final UnitRepository unitRepository;
    private final BrandRepository brandRepository;
    private final CustomerSegmentRepository segmentRepository;
    private final PaymentTermRepository paymentTermRepository;
    private final CategoryRepository categoryRepository;

    public AuxiliaryController(AuxiliaryService auxiliaryService, UnitRepository unitRepository,
                               BrandRepository brandRepository, CustomerSegmentRepository segmentRepository,
                               PaymentTermRepository paymentTermRepository, CategoryRepository categoryRepository) {
        this.auxiliaryService = auxiliaryService;
        this.unitRepository = unitRepository;
        this.brandRepository = brandRepository;
        this.segmentRepository = segmentRepository;
        this.paymentTermRepository = paymentTermRepository;
        this.categoryRepository = categoryRepository;
    }

    @GetMapping("/units")
    public List<UnitResponse> units() {
        return unitRepository.findAll(Sort.by("name")).stream().map(UnitResponse::from).toList();
    }

    @GetMapping("/categories")
    public List<CategoryNode> categories() {
        return categoryRepository.findAll(Sort.by("name")).stream().map(CategoryNode::from).toList();
    }

    @PreAuthorize(CONFIGURAR)
    @PostMapping("/categories")
    public ResponseEntity<CategoryNode> createSubcategory(@Valid @RequestBody SubcategoryRequest request) {
        return ResponseEntity.status(201).body(CategoryNode.from(auxiliaryService.createSubcategory(request)));
    }

    @GetMapping("/brands")
    public List<NamedItem> brands() {
        return brandRepository.findAllByOrderByNameAsc().stream()
                .map(b -> new NamedItem(b.getId(), b.getName(), b.isActive())).toList();
    }

    @PreAuthorize(CONFIGURAR)
    @PostMapping("/brands")
    public NamedItem createBrand(@Valid @RequestBody NamedItemRequest request) {
        Brand b = auxiliaryService.saveBrand(null, request);
        return new NamedItem(b.getId(), b.getName(), b.isActive());
    }

    @PreAuthorize(CONFIGURAR)
    @PutMapping("/brands/{id}")
    public NamedItem updateBrand(@PathVariable Long id, @Valid @RequestBody NamedItemRequest request) {
        Brand b = auxiliaryService.saveBrand(id, request);
        return new NamedItem(b.getId(), b.getName(), b.isActive());
    }

    @GetMapping("/customer-segments")
    public List<NamedItem> segments() {
        return segmentRepository.findAllByOrderByNameAsc().stream()
                .map(s -> new NamedItem(s.getId(), s.getName(), s.isActive())).toList();
    }

    @PreAuthorize(CONFIGURAR)
    @PostMapping("/customer-segments")
    public NamedItem createSegment(@Valid @RequestBody NamedItemRequest request) {
        CustomerSegment s = auxiliaryService.saveSegment(null, request);
        return new NamedItem(s.getId(), s.getName(), s.isActive());
    }

    @PreAuthorize(CONFIGURAR)
    @PutMapping("/customer-segments/{id}")
    public NamedItem updateSegment(@PathVariable Long id, @Valid @RequestBody NamedItemRequest request) {
        CustomerSegment s = auxiliaryService.saveSegment(id, request);
        return new NamedItem(s.getId(), s.getName(), s.isActive());
    }

    @GetMapping("/payment-terms")
    public List<PaymentTermResponse> paymentTerms() {
        return paymentTermRepository.findAllByOrderByNameAsc().stream().map(PaymentTermResponse::from).toList();
    }

    @PreAuthorize(CONFIGURAR)
    @PostMapping("/payment-terms")
    public PaymentTermResponse createPaymentTerm(@Valid @RequestBody PaymentTermRequest request) {
        return PaymentTermResponse.from(auxiliaryService.savePaymentTerm(null, request));
    }

    @PreAuthorize(CONFIGURAR)
    @PutMapping("/payment-terms/{id}")
    public PaymentTermResponse updatePaymentTerm(@PathVariable Long id, @Valid @RequestBody PaymentTermRequest request) {
        return PaymentTermResponse.from(auxiliaryService.savePaymentTerm(id, request));
    }
}
