package com.distribuidora.backend.cadastros;

import com.distribuidora.backend.audit.AuditAction;
import com.distribuidora.backend.audit.AuditService;
import com.distribuidora.backend.cadastros.PartyDtos.AddressDto;
import com.distribuidora.backend.cadastros.PartyDtos.SupplierRequest;
import com.distribuidora.backend.cadastros.PartyDtos.SupplierResponse;
import com.distribuidora.backend.exception.BusinessRuleException;
import com.distribuidora.backend.exception.ConflictException;
import com.distribuidora.backend.exception.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SupplierService {

    private final SupplierRepository supplierRepository;
    private final PaymentTermRepository paymentTermRepository;
    private final AuditService auditService;

    public SupplierService(SupplierRepository supplierRepository, PaymentTermRepository paymentTermRepository,
                           AuditService auditService) {
        this.supplierRepository = supplierRepository;
        this.paymentTermRepository = paymentTermRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public Page<SupplierResponse> search(String search, Boolean active, Pageable pageable) {
        Specification<Supplier> spec = Specification.where(null);
        if (search != null && !search.isBlank()) {
            spec = spec.and(PartySpecifications.matches(search.trim()));
        }
        if (active != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("active"), active));
        }
        Map<Long, String> terms = paymentTermNames();
        return supplierRepository.findAll(spec, pageable).map(s -> toResponse(s, terms));
    }

    @Transactional(readOnly = true)
    public SupplierResponse findOne(Long id) {
        return toResponse(get(id), paymentTermNames());
    }

    @Transactional
    public SupplierResponse create(SupplierRequest request) {
        String document = PartySupport.validDocument(request.party().document());
        if (supplierRepository.existsByDocument(document)) {
            throw new ConflictException("Ja existe um fornecedor com este CPF/CNPJ.");
        }
        Supplier supplier = new Supplier();
        apply(supplier, request, document);
        supplierRepository.save(supplier);
        auditService.recordChange(AuditAction.FORNECEDOR_CRIADO, "Supplier", supplier.getId(), null,
                snapshot(supplier), null);
        return toResponse(supplier, paymentTermNames());
    }

    @Transactional
    public SupplierResponse update(Long id, SupplierRequest request) {
        Supplier supplier = get(id);
        PartySupport.checkVersion(request.version(), supplier.getVersion());
        String document = PartySupport.validDocument(request.party().document());
        if (supplierRepository.existsByDocumentAndIdNot(document, id)) {
            throw new ConflictException("Ja existe um fornecedor com este CPF/CNPJ.");
        }
        Map<String, Object> before = snapshot(supplier);
        apply(supplier, request, document);
        auditService.recordChange(AuditAction.FORNECEDOR_ALTERADO, "Supplier", supplier.getId(), before,
                snapshot(supplier), null);
        return toResponse(supplier, paymentTermNames());
    }

    Supplier get(Long id) {
        return supplierRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Fornecedor nao encontrado: " + id));
    }

    private void apply(Supplier supplier, SupplierRequest request, String document) {
        if (request.paymentTermId() != null && !paymentTermRepository.existsById(request.paymentTermId())) {
            throw new BusinessRuleException("Condicao de pagamento inexistente.");
        }
        PartySupport.apply(supplier, request.party(), document);
        supplier.setPaymentTermId(request.paymentTermId());
        supplier.setLeadTimeDays(request.leadTimeDays());
        supplier.setActive(request.active());
    }

    private static Map<String, Object> snapshot(Supplier supplier) {
        Map<String, Object> values = PartySupport.snapshot(supplier);
        values.put("paymentTermId", supplier.getPaymentTermId());
        values.put("leadTimeDays", supplier.getLeadTimeDays());
        values.put("active", supplier.isActive());
        return values;
    }

    private Map<Long, String> paymentTermNames() {
        return paymentTermRepository.findAll().stream()
                .collect(Collectors.toMap(PaymentTerm::getId, PaymentTerm::getName));
    }

    private static SupplierResponse toResponse(Supplier s, Map<Long, String> terms) {
        return new SupplierResponse(s.getId(), s.getLegalName(), s.getTradeName(), s.displayName(), s.getDocument(),
                PartySupport.personType(s.getDocument()), s.getStateRegistration(), s.getPhone(), s.getWhatsapp(),
                s.getEmail(), s.getContactName(), AddressDto.from(s.getAddress()), s.getNotes(), s.getPaymentTermId(),
                terms.get(s.getPaymentTermId()), s.getLeadTimeDays(), s.isActive(), s.getCreatedAt(),
                s.getUpdatedAt(), s.getVersion());
    }
}
