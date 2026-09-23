package com.distribuidora.backend.faturamento;

import com.distribuidora.backend.cadastros.Customer;
import com.distribuidora.backend.cadastros.CustomerRepository;
import com.distribuidora.backend.cadastros.PaymentTerm;
import com.distribuidora.backend.cadastros.PaymentTermRepository;
import com.distribuidora.backend.exception.ResourceNotFoundException;
import com.distribuidora.backend.faturamento.InvoiceDtos.InvoiceItemView;
import com.distribuidora.backend.faturamento.InvoiceDtos.InvoiceSummary;
import com.distribuidora.backend.faturamento.InvoiceDtos.InvoiceTotals;
import com.distribuidora.backend.faturamento.InvoiceDtos.InvoiceView;
import com.distribuidora.backend.financeiro.ReceivableQueryService;
import com.distribuidora.backend.security.CurrentUser;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class InvoiceQueryService {

    static final String CUSTO_VER = "produtos.custo.ver";

    private final InvoiceRepository invoiceRepository;
    private final CustomerRepository customerRepository;
    private final PaymentTermRepository paymentTermRepository;
    private final ReceivableQueryService receivableQueryService;
    private final CurrentUser currentUser;
    private final Clock clock;

    public InvoiceQueryService(InvoiceRepository invoiceRepository, CustomerRepository customerRepository,
                               PaymentTermRepository paymentTermRepository,
                               ReceivableQueryService receivableQueryService, CurrentUser currentUser, Clock clock) {
        this.invoiceRepository = invoiceRepository;
        this.customerRepository = customerRepository;
        this.paymentTermRepository = paymentTermRepository;
        this.receivableQueryService = receivableQueryService;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<InvoiceSummary> search(Invoice.Status status, Long customerId, String search, int page, int size) {
        Specification<Invoice> spec = Specification.where(null);
        if (status != null) {
            spec = spec.and((r, q, cb) -> cb.equal(r.get("status"), status));
        }
        if (customerId != null) {
            spec = spec.and((r, q, cb) -> cb.equal(r.get("customerId"), customerId));
        }
        if (search != null && !search.isBlank()) {
            String term = search.trim();
            String like = "%" + term.toLowerCase() + "%";
            spec = spec.and((r, q, cb) -> {
                Subquery<Long> customers = q.subquery(Long.class);
                var c = customers.from(Customer.class);
                customers.select(c.get("id")).where(cb.or(cb.like(cb.lower(c.get("legalName")), like),
                        cb.like(cb.lower(c.get("tradeName")), like)));
                var byCustomer = cb.in(r.get("customerId")).value(customers);
                return term.chars().allMatch(Character::isDigit)
                        ? cb.or(byCustomer, cb.equal(r.get("number"), Long.parseLong(term)))
                        : byCustomer;
            });
        }
        Page<Invoice> invoices = invoiceRepository.findAll(spec,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "issuedAt", "id")));
        Map<Long, Customer> customers = customers(invoices.getContent());
        return invoices.map(i -> new InvoiceSummary(i.getId(), i.getNumber(), i.getSeries(), i.display(),
                i.getOrderId(), i.getCustomerId(),
                customers.containsKey(i.getCustomerId()) ? customers.get(i.getCustomerId()).displayName() : null,
                i.getStatus(), i.getStatus().getLabel(), i.getIssuedAt(), i.getIssuedBy(), i.getTotal(),
                i.getItems().size()));
    }

    @Transactional(readOnly = true)
    public InvoiceView view(Long id) {
        return toView(invoiceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Documento nao encontrado: " + id)));
    }

    @Transactional(readOnly = true)
    public InvoiceView toView(Invoice invoice) {
        Customer customer = customerRepository.findById(invoice.getCustomerId()).orElse(null);
        boolean seesCost = currentUser.can(CUSTO_VER);
        List<InvoiceItemView> items = invoice.getItems().stream()
                .map(i -> new InvoiceItemView(i.getId(), i.getProductId(), i.getDescription(), i.getBaseUnit(),
                        i.getQuantity(), i.getQtyOrdered(), i.getUnitPrice(), i.getDiscountPercent(), i.getLineTotal(),
                        seesCost ? i.getUnitCost() : null, seesCost ? i.getLineCost() : null))
                .toList();
        BigDecimal margin = seesCost && invoice.getCostTotal() != null && invoice.getTotal().signum() > 0
                ? invoice.getTotal().subtract(invoice.getCostTotal()).multiply(BigDecimal.valueOf(100))
                .divide(invoice.getTotal(), 1, RoundingMode.HALF_UP) : null;

        return new InvoiceView(invoice.getId(), invoice.getNumber(), invoice.getSeries(), invoice.display(),
                invoice.getOrderId(), invoice.getPickingId(), invoice.getCustomerId(),
                customer == null ? null : customer.displayName(), customer == null ? null : customer.getDocument(),
                invoice.getStatus(), invoice.getStatus().getLabel(), invoice.getFiscalStatus().name(),
                invoice.getFiscalStatus().getLabel(), invoice.getFiscalNumber(), invoice.getFiscalKey(),
                invoice.getFiscalMessage(), invoice.getIssuedAt(), invoice.getIssuedBy(),
                invoice.getPaymentTermId() == null ? null : paymentTermRepository.findById(invoice.getPaymentTermId())
                        .map(PaymentTerm::getName).orElse(null),
                invoice.getSubtotal(), invoice.getDiscountTotal(), invoice.getTotal(),
                seesCost ? invoice.getCostTotal() : null, margin, items,
                receivableQueryService.forInvoice(invoice.getId()), invoice.getCancelledBy(), invoice.getCancelledAt(),
                invoice.getCancelReason(),
                invoice.getStatus() == Invoice.Status.EMITIDA && currentUser.can(InvoiceService.CANCELAR),
                invoice.getVersion());
    }

    // Faturamento do dia e do mes. Documento cancelado nao entra.
    @Transactional(readOnly = true)
    public InvoiceTotals totals() {
        ZoneId zone = clock.getZone();
        LocalDate today = LocalDate.now(clock);
        Instant monthStart = today.withDayOfMonth(1).atStartOfDay(zone).toInstant();
        List<Invoice> issued = invoiceRepository.findAll((r, q, cb) -> cb.and(
                cb.equal(r.get("status"), Invoice.Status.EMITIDA),
                cb.greaterThanOrEqualTo(r.get("issuedAt"), monthStart)));

        BigDecimal month = issued.stream().map(Invoice::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal dayTotal = issued.stream()
                .filter(i -> LocalDate.ofInstant(i.getIssuedAt(), zone).isEqual(today))
                .map(Invoice::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean seesCost = currentUser.can(CUSTO_VER);
        BigDecimal cost = seesCost && issued.stream().allMatch(i -> i.getCostTotal() != null)
                ? issued.stream().map(Invoice::getCostTotal).reduce(BigDecimal.ZERO, BigDecimal::add) : null;
        BigDecimal marginValue = cost == null ? null : month.subtract(cost);
        BigDecimal marginPercent = marginValue == null || month.signum() == 0 ? null
                : marginValue.multiply(BigDecimal.valueOf(100)).divide(month, 1, RoundingMode.HALF_UP);
        BigDecimal ticket = issued.isEmpty() ? BigDecimal.ZERO
                : month.divide(BigDecimal.valueOf(issued.size()), 2, RoundingMode.HALF_UP);

        return new InvoiceTotals(dayTotal, month, cost, marginValue, marginPercent, issued.size(), ticket);
    }

    private Map<Long, Customer> customers(Collection<Invoice> invoices) {
        return customerRepository.findAllById(invoices.stream().map(Invoice::getCustomerId).distinct().toList())
                .stream().collect(Collectors.toMap(Customer::getId, Function.identity()));
    }
}
