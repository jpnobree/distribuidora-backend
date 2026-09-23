package com.distribuidora.backend.cadastros;

import com.distribuidora.backend.audit.AuditAction;
import com.distribuidora.backend.audit.AuditService;
import com.distribuidora.backend.cadastros.AuxDtos.NamedItemRequest;
import com.distribuidora.backend.cadastros.AuxDtos.PaymentTermRequest;
import com.distribuidora.backend.cadastros.AuxDtos.SubcategoryRequest;
import com.distribuidora.backend.exception.BusinessRuleException;
import com.distribuidora.backend.exception.ConflictException;
import com.distribuidora.backend.exception.ResourceNotFoundException;
import com.distribuidora.backend.model.Category;
import com.distribuidora.backend.repository.CategoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AuxiliaryService {

    private final BrandRepository brandRepository;
    private final CustomerSegmentRepository segmentRepository;
    private final PaymentTermRepository paymentTermRepository;
    private final CategoryRepository categoryRepository;
    private final AuditService auditService;

    public AuxiliaryService(BrandRepository brandRepository, CustomerSegmentRepository segmentRepository,
                            PaymentTermRepository paymentTermRepository, CategoryRepository categoryRepository,
                            AuditService auditService) {
        this.brandRepository = brandRepository;
        this.segmentRepository = segmentRepository;
        this.paymentTermRepository = paymentTermRepository;
        this.categoryRepository = categoryRepository;
        this.auditService = auditService;
    }

    @Transactional
    public Brand saveBrand(Long id, NamedItemRequest request) {
        String name = request.name().trim();
        Brand brand = id == null ? null : brandRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Marca nao encontrada: " + id));
        if ((brand == null || !brand.getName().equalsIgnoreCase(name)) && brandRepository.existsByNameIgnoreCase(name)) {
            throw new ConflictException("Ja existe a marca " + name);
        }
        Map<String, Object> before = brand == null ? null : snapshot(brand.getName(), brand.isActive());
        if (brand == null) {
            brand = new Brand(name);
        }
        brand.setName(name);
        brand.setActive(request.active());
        brandRepository.save(brand);
        auditService.recordChange(AuditAction.CADASTRO_AUXILIAR_ALTERADO, "Brand", brand.getId(), before,
                snapshot(brand.getName(), brand.isActive()), null);
        return brand;
    }

    @Transactional
    public CustomerSegment saveSegment(Long id, NamedItemRequest request) {
        String name = request.name().trim();
        CustomerSegment segment = id == null ? null : segmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Segmento nao encontrado: " + id));
        if ((segment == null || !segment.getName().equalsIgnoreCase(name))
                && segmentRepository.existsByNameIgnoreCase(name)) {
            throw new ConflictException("Ja existe o segmento " + name);
        }
        Map<String, Object> before = segment == null ? null : snapshot(segment.getName(), segment.isActive());
        if (segment == null) {
            segment = new CustomerSegment(name);
        }
        segment.setName(name);
        segment.setActive(request.active());
        segmentRepository.save(segment);
        auditService.recordChange(AuditAction.CADASTRO_AUXILIAR_ALTERADO, "CustomerSegment", segment.getId(), before,
                snapshot(segment.getName(), segment.isActive()), null);
        return segment;
    }

    @Transactional
    public PaymentTerm savePaymentTerm(Long id, PaymentTermRequest request) {
        String name = request.name().trim();
        PaymentTerm term = id == null ? null : paymentTermRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Condicao de pagamento nao encontrada: " + id));
        if ((term == null || !term.getName().equalsIgnoreCase(name)) && paymentTermRepository.existsByNameIgnoreCase(name)) {
            throw new ConflictException("Ja existe a condicao " + name);
        }
        List<Integer> days = request.installmentDays().stream().distinct().sorted().toList();
        if (days.size() > 1 && days.get(0) == 0) {
            throw new BusinessRuleException("Parcela de 0 dias (a vista) nao pode ser combinada com outras.");
        }
        Map<String, Object> before = null;
        if (term == null) {
            term = new PaymentTerm(name, days);
        } else {
            before = paymentTermSnapshot(term);
        }
        term.setName(name);
        term.setInstallmentDays(days);
        term.setActive(request.active());
        paymentTermRepository.save(term);
        auditService.recordChange(AuditAction.CADASTRO_AUXILIAR_ALTERADO, "PaymentTerm", term.getId(), before,
                paymentTermSnapshot(term), null);
        return term;
    }

    @Transactional
    public Category createSubcategory(SubcategoryRequest request) {
        Category parent = categoryRepository.findById(request.parentId())
                .orElseThrow(() -> new ResourceNotFoundException("Categoria nao encontrada: " + request.parentId()));
        if (parent.getParentId() != null) {
            throw new BusinessRuleException("Subcategoria so pode ficar abaixo de uma categoria principal.");
        }
        String slug = parent.getSlug() + "-" + slugify(request.name());
        if (categoryRepository.existsBySlug(slug)) {
            throw new ConflictException("Ja existe essa subcategoria em " + parent.getName());
        }
        Category sub = new Category(slug, request.name().trim(), null);
        sub.setParentId(parent.getId());
        categoryRepository.save(sub);
        auditService.recordChange(AuditAction.CADASTRO_AUXILIAR_ALTERADO, "Category", slug, null,
                Map.of("name", sub.getName(), "parent", parent.getSlug()), null);
        return sub;
    }

    static String slugify(String value) {
        return Normalizer.normalize(value.trim().toLowerCase(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
    }

    private static Map<String, Object> snapshot(String name, boolean active) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("name", name);
        values.put("active", active);
        return values;
    }

    private static Map<String, Object> paymentTermSnapshot(PaymentTerm term) {
        Map<String, Object> values = snapshot(term.getName(), term.isActive());
        values.put("installmentDays", List.copyOf(term.getInstallmentDays()));
        return values;
    }
}
