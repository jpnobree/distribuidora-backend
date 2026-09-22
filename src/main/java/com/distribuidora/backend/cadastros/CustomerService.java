package com.distribuidora.backend.cadastros;

import com.distribuidora.backend.audit.AuditAction;
import com.distribuidora.backend.audit.AuditService;
import com.distribuidora.backend.cadastros.PartyDtos.AddressDto;
import com.distribuidora.backend.cadastros.PartyDtos.CustomerRequest;
import com.distribuidora.backend.cadastros.PartyDtos.CustomerResponse;
import com.distribuidora.backend.exception.BusinessRuleException;
import com.distribuidora.backend.exception.ConflictException;
import com.distribuidora.backend.exception.ResourceNotFoundException;
import com.distribuidora.backend.model.User;
import com.distribuidora.backend.repository.UserRepository;
import com.distribuidora.backend.security.CurrentUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class CustomerService {

    static final String VER_TODOS = "clientes.ver_todos";
    static final String CREDITO_ALTERAR = "clientes.credito.alterar";
    static final String PRECOS_GERENCIAR = "precos.gerenciar";

    private final CustomerRepository customerRepository;
    private final PaymentTermRepository paymentTermRepository;
    private final CustomerSegmentRepository segmentRepository;
    private final UserRepository userRepository;
    private final CurrentUser currentUser;
    private final AuditService auditService;

    public CustomerService(CustomerRepository customerRepository, PaymentTermRepository paymentTermRepository,
                           CustomerSegmentRepository segmentRepository, UserRepository userRepository,
                           CurrentUser currentUser, AuditService auditService) {
        this.customerRepository = customerRepository;
        this.paymentTermRepository = paymentTermRepository;
        this.segmentRepository = segmentRepository;
        this.userRepository = userRepository;
        this.currentUser = currentUser;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public Page<CustomerResponse> search(String search, Long segmentId, Long sellerId, Customer.Status status,
                                         String city, Pageable pageable) {
        Specification<Customer> spec = Specification.where(portfolioScope());
        if (search != null && !search.isBlank()) {
            spec = spec.and(PartySpecifications.matches(search.trim()));
        }
        if (segmentId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("segmentId"), segmentId));
        }
        if (sellerId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("sellerId"), sellerId));
        }
        if (status != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        if (city != null && !city.isBlank()) {
            spec = spec.and((root, query, cb) ->
                    cb.equal(cb.lower(root.get("address").get("city")), city.trim().toLowerCase()));
        }
        Page<Customer> page = customerRepository.findAll(spec, pageable);
        Names names = names(page.getContent());
        return page.map(c -> toResponse(c, names));
    }

    @Transactional(readOnly = true)
    public CustomerResponse findOne(Long id) {
        Customer customer = getVisible(id);
        return toResponse(customer, names(List.of(customer)));
    }

    @Transactional
    public CustomerResponse create(CustomerRequest request) {
        String document = PartySupport.validDocument(request.party().document());
        if (customerRepository.existsByDocument(document)) {
            throw new ConflictException("Ja existe um cliente com este CPF/CNPJ.");
        }
        boolean creditTouched = request.creditLimit().signum() > 0 || request.status() != Customer.Status.ATIVO;
        if (creditTouched) {
            requireCreditPermission();
        }
        requirePricePermissionIfChanged(null, request.priceTableId());

        Customer customer = new Customer();
        customer.setSellerId(resolveSeller(request.sellerId(), null));
        applyCommon(customer, request, document);
        customer.setCreditLimit(request.creditLimit());
        customer.setStatus(request.status());
        customerRepository.save(customer);

        Map<String, Object> created = snapshot(customer);
        created.putAll(creditSnapshot(customer));
        auditService.recordChange(AuditAction.CLIENTE_CRIADO, "Customer", customer.getId(), null, created, null);
        return toResponse(customer, names(List.of(customer)));
    }

    @Transactional
    public CustomerResponse update(Long id, CustomerRequest request) {
        Customer customer = getVisible(id);
        PartySupport.checkVersion(request.version(), customer.getVersion());
        String document = PartySupport.validDocument(request.party().document());
        if (customerRepository.existsByDocumentAndIdNot(document, id)) {
            throw new ConflictException("Ja existe um cliente com este CPF/CNPJ.");
        }

        Map<String, Object> before = snapshot(customer);
        Map<String, Object> creditBefore = creditSnapshot(customer);
        boolean creditChanged = customer.getCreditLimit().compareTo(request.creditLimit()) != 0
                || customer.getStatus() != request.status();
        if (creditChanged) {
            requireCreditPermission();
        }
        requirePricePermissionIfChanged(customer.getPriceTableId(), request.priceTableId());

        customer.setSellerId(resolveSeller(request.sellerId(), customer.getSellerId()));
        applyCommon(customer, request, document);
        customer.setCreditLimit(request.creditLimit());
        customer.setStatus(request.status());

        auditService.recordChange(AuditAction.CLIENTE_ALTERADO, "Customer", id, before, snapshot(customer), null);
        // Credito auditado separado: e o que o financeiro procura numa investigacao.
        auditService.recordChange(AuditAction.CLIENTE_CREDITO_ALTERADO, "Customer", id, creditBefore,
                creditSnapshot(customer), null);
        return toResponse(customer, names(List.of(customer)));
    }

    // Quem nao tem "clientes.ver_todos" enxerga so a propria carteira.
    private Specification<Customer> portfolioScope() {
        if (currentUser.can(VER_TODOS)) {
            return null;
        }
        Long me = currentUser.id();
        return (root, query, cb) -> cb.equal(root.get("sellerId"), me);
    }

    private Customer getVisible(Long id) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente nao encontrado: " + id));
        if (!currentUser.can(VER_TODOS) && !Objects.equals(customer.getSellerId(), currentUser.id())) {
            // 404 e nao 403: nao revela que o cliente existe em outra carteira
            throw new ResourceNotFoundException("Cliente nao encontrado: " + id);
        }
        return customer;
    }

    // Sem "ver_todos", o vendedor so cadastra para si e nao transfere clientes.
    private Long resolveSeller(Long requested, Long current) {
        if (!currentUser.can(VER_TODOS)) {
            return current != null ? current : currentUser.id();
        }
        if (requested == null) {
            return null;
        }
        User seller = userRepository.findById(requested)
                .orElseThrow(() -> new BusinessRuleException("Vendedor inexistente."));
        if (!seller.isActive()) {
            throw new BusinessRuleException("O vendedor escolhido esta desativado.");
        }
        return seller.getId();
    }

    // A tabela define o preco do cliente: vendedor nao escolhe a propria.
    private void requirePricePermissionIfChanged(Long current, Long requested) {
        if (!Objects.equals(current, requested) && !currentUser.can(PRECOS_GERENCIAR)) {
            throw new AccessDeniedException("Voce nao tem permissao para definir a tabela de preco do cliente.");
        }
    }

    private void requireCreditPermission() {
        if (!currentUser.can(CREDITO_ALTERAR)) {
            throw new AccessDeniedException("Voce nao tem permissao para alterar limite de credito ou bloquear clientes.");
        }
    }

    private void applyCommon(Customer customer, CustomerRequest request, String document) {
        if (request.paymentTermId() != null && !paymentTermRepository.existsById(request.paymentTermId())) {
            throw new BusinessRuleException("Condicao de pagamento inexistente.");
        }
        if (request.segmentId() != null && !segmentRepository.existsById(request.segmentId())) {
            throw new BusinessRuleException("Segmento inexistente.");
        }
        PartySupport.apply(customer, request.party(), document);
        customer.setPersonType(PartySupport.personType(document));
        customer.setSegmentId(request.segmentId());
        customer.setPaymentTermId(request.paymentTermId());
        customer.setPriceTableId(request.priceTableId());
        customer.setLatitude(request.latitude());
        customer.setLongitude(request.longitude());
    }

    private static Map<String, Object> snapshot(Customer c) {
        Map<String, Object> values = PartySupport.snapshot(c);
        values.put("segmentId", c.getSegmentId());
        values.put("sellerId", c.getSellerId());
        values.put("paymentTermId", c.getPaymentTermId());
        values.put("priceTableId", c.getPriceTableId());
        return values;
    }

    private static Map<String, Object> creditSnapshot(Customer c) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("creditLimit", c.getCreditLimit());
        values.put("status", c.getStatus());
        return values;
    }

    private record Names(Map<Long, String> terms, Map<Long, String> segments, Map<Long, String> sellers) {
    }

    private Names names(Collection<Customer> customers) {
        List<Long> sellerIds = customers.stream().map(Customer::getSellerId).filter(Objects::nonNull).distinct().toList();
        return new Names(
                paymentTermRepository.findAll().stream().collect(Collectors.toMap(PaymentTerm::getId, PaymentTerm::getName)),
                segmentRepository.findAll().stream().collect(Collectors.toMap(CustomerSegment::getId, CustomerSegment::getName)),
                userRepository.findAllById(sellerIds).stream().collect(Collectors.toMap(User::getId,
                        u -> u.getFullName() != null ? u.getFullName() : u.getUsername())));
    }

    private static CustomerResponse toResponse(Customer c, Names n) {
        return new CustomerResponse(c.getId(), c.getLegalName(), c.getTradeName(), c.displayName(), c.getDocument(),
                c.getPersonType(), c.getStateRegistration(), c.getPhone(), c.getWhatsapp(), c.getEmail(),
                c.getContactName(), AddressDto.from(c.getAddress()), c.getNotes(), c.getSegmentId(),
                n.segments().get(c.getSegmentId()), c.getSellerId(), n.sellers().get(c.getSellerId()),
                c.getPaymentTermId(), n.terms().get(c.getPaymentTermId()), c.getPriceTableId(), c.getCreditLimit(), c.getStatus(),
                c.getLatitude(), c.getLongitude(), c.getCreatedAt(), c.getUpdatedAt(), c.getVersion());
    }
}
