package com.distribuidora.backend.financeiro;

import com.distribuidora.backend.cadastros.Customer;
import com.distribuidora.backend.cadastros.CustomerRepository;
import com.distribuidora.backend.exception.ResourceNotFoundException;
import com.distribuidora.backend.faturamento.Invoice;
import com.distribuidora.backend.faturamento.InvoiceRepository;
import com.distribuidora.backend.financeiro.FinanceDtos.ReceivableSummary;
import com.distribuidora.backend.financeiro.FinanceDtos.ReceivableView;
import com.distribuidora.backend.financeiro.FinanceDtos.ReceivablesTotals;
import com.distribuidora.backend.financeiro.FinanceDtos.TransactionView;
import com.distribuidora.backend.security.CurrentUser;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ReceivableQueryService {

    private final ReceivableRepository receivableRepository;
    private final FinancialTransactionRepository transactionRepository;
    private final CustomerRepository customerRepository;
    private final InvoiceRepository invoiceRepository;
    private final CurrentUser currentUser;
    private final Clock clock;

    public ReceivableQueryService(ReceivableRepository receivableRepository,
                                  FinancialTransactionRepository transactionRepository,
                                  CustomerRepository customerRepository, InvoiceRepository invoiceRepository,
                                  CurrentUser currentUser, Clock clock) {
        this.receivableRepository = receivableRepository;
        this.transactionRepository = transactionRepository;
        this.customerRepository = customerRepository;
        this.invoiceRepository = invoiceRepository;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<ReceivableSummary> search(Receivable.Status status, Long customerId, boolean openOnly,
                                          boolean overdueOnly, LocalDate dueFrom, LocalDate dueTo, String search,
                                          int page, int size) {
        Specification<Receivable> spec = filter(status, customerId, overdueOnly, dueFrom, dueTo, search);
        if (openOnly) {
            spec = spec.and((r, q, cb) -> r.get("status").in(Receivable.Status.ABERTO, Receivable.Status.PARCIAL));
        }
        Page<Receivable> titles = receivableRepository.findAll(spec,
                PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "dueDate", "id")));
        return titles.map(summarize(customers(titles.getContent()), invoices(titles.getContent())));
    }

    @Transactional(readOnly = true)
    public ReceivablesTotals totals(Long customerId) {
        List<Receivable> open = receivableRepository.findAll(
                filter(null, customerId, false, null, null, null).and((r, q, cb) -> r.get("status")
                        .in(Receivable.Status.ABERTO, Receivable.Status.PARCIAL)));
        LocalDate today = LocalDate.now(clock);
        BigDecimal overdue = sum(open.stream().filter(r -> r.getDueDate().isBefore(today)).toList());
        BigDecimal dueToday = sum(open.stream().filter(r -> r.getDueDate().isEqual(today)).toList());
        BigDecimal in7 = sum(open.stream().filter(r -> inRange(r.getDueDate(), today, today.plusDays(7))).toList());
        BigDecimal in30 = sum(open.stream().filter(r -> inRange(r.getDueDate(), today, today.plusDays(30))).toList());

        LocalDate monthStart = today.withDayOfMonth(1);
        BigDecimal received = transactionRepository.findByReceivableIdInOrderByIdAsc(
                        visibleIds(customerId)).stream()
                .filter(t -> !t.getPaidOn().isBefore(monthStart))
                .map(t -> t.getType() == FinancialTransaction.Type.BAIXA ? t.received() : t.received().negate())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new ReceivablesTotals(sum(open), overdue, dueToday, in7, in30, received,
                open.stream().filter(r -> r.getDueDate().isBefore(today)).count(), open.size());
    }

    @Transactional(readOnly = true)
    public ReceivableView view(Long id) {
        Receivable title = receivableRepository.findOne(filter(null, null, false, null, null, null)
                        .and((r, q, cb) -> cb.equal(r.get("id"), id)))
                .orElseThrow(() -> new ResourceNotFoundException("Titulo nao encontrado: " + id));
        return toView(title);
    }

    @Transactional(readOnly = true)
    public ReceivableView toView(Receivable title) {
        Customer customer = customerRepository.findById(title.getCustomerId()).orElse(null);
        Invoice invoice = invoiceRepository.findById(title.getInvoiceId()).orElse(null);
        Set<Long> reversed = transactionRepository.findByReceivableIdOrderByIdAsc(title.getId()).stream()
                .map(FinancialTransaction::getReversalOf).filter(Objects::nonNull).collect(Collectors.toSet());
        List<TransactionView> transactions = transactionRepository.findByReceivableIdOrderByIdAsc(title.getId())
                .stream()
                .map(t -> new TransactionView(t.getId(), t.getType().name(), t.getAmount(), t.getInterest(),
                        t.getDiscount(), t.received(), t.getPaidOn(), t.getMethod().name(), t.getMethod().getLabel(),
                        t.getNotes(), t.getUsername(), t.getOccurredAt(), reversed.contains(t.getId()),
                        t.getReversalOf()))
                .toList();

        return new ReceivableView(summary(title, customer, invoice),
                customer == null ? null : customer.getDocument(),
                customer == null ? null : customer.getPhone(),
                invoice == null ? null : invoice.getOrderId(), transactions,
                title.getStatus().isOpen() && currentUser.can(ReceivableService.BAIXAR),
                title.getStatus().isOpen() && title.getPaidAmount().signum() == 0
                        && currentUser.can(ReceivableService.CANCELAR),
                title.getVersion());
    }

    @Transactional(readOnly = true)
    public List<ReceivableSummary> forInvoice(Long invoiceId) {
        List<Receivable> titles = receivableRepository.findByInvoiceIdOrderByInstallment(invoiceId);
        return titles.stream().map(summarize(customers(titles), invoices(titles))).toList();
    }

    // ------------------------------------------------------------- apoio

    // Sem "clientes.ver_todos" o vendedor so ve os titulos dos seus clientes.
    private Specification<Receivable> filter(Receivable.Status status, Long customerId, boolean overdueOnly,
                                             LocalDate dueFrom, LocalDate dueTo, String search) {
        Specification<Receivable> spec = Specification.where(null);
        if (!currentUser.can(ReceivableService.CLIENTES_VER_TODOS)) {
            Long me = currentUser.id();
            spec = spec.and((r, q, cb) -> {
                Subquery<Long> mine = q.subquery(Long.class);
                var c = mine.from(Customer.class);
                mine.select(c.get("id")).where(cb.equal(c.get("sellerId"), me));
                return cb.in(r.get("customerId")).value(mine);
            });
        }
        if (status != null) {
            spec = spec.and((r, q, cb) -> cb.equal(r.get("status"), status));
        }
        if (customerId != null) {
            spec = spec.and((r, q, cb) -> cb.equal(r.get("customerId"), customerId));
        }
        if (overdueOnly) {
            LocalDate today = LocalDate.now(clock);
            spec = spec.and((r, q, cb) -> cb.and(cb.lessThan(r.get("dueDate"), today),
                    r.get("status").in(Receivable.Status.ABERTO, Receivable.Status.PARCIAL)));
        }
        if (dueFrom != null) {
            spec = spec.and((r, q, cb) -> cb.greaterThanOrEqualTo(r.get("dueDate"), dueFrom));
        }
        if (dueTo != null) {
            spec = spec.and((r, q, cb) -> cb.lessThanOrEqualTo(r.get("dueDate"), dueTo));
        }
        if (search != null && !search.isBlank()) {
            String like = "%" + search.trim().toLowerCase() + "%";
            spec = spec.and((r, q, cb) -> {
                Subquery<Long> found = q.subquery(Long.class);
                var c = found.from(Customer.class);
                found.select(c.get("id")).where(cb.or(cb.like(cb.lower(c.get("legalName")), like),
                        cb.like(cb.lower(c.get("tradeName")), like)));
                return cb.or(cb.in(r.get("customerId")).value(found), cb.like(cb.lower(r.get("document")), like));
            });
        }
        return spec;
    }

    private List<Long> visibleIds(Long customerId) {
        return receivableRepository.findAll(filter(null, customerId, false, null, null, null)).stream()
                .map(Receivable::getId).toList();
    }

    private Map<Long, Customer> customers(Collection<Receivable> titles) {
        return customerRepository.findAllById(titles.stream().map(Receivable::getCustomerId).distinct().toList())
                .stream().collect(Collectors.toMap(Customer::getId, Function.identity()));
    }

    private Map<Long, Invoice> invoices(Collection<Receivable> titles) {
        return invoiceRepository.findAllById(titles.stream().map(Receivable::getInvoiceId).distinct().toList())
                .stream().collect(Collectors.toMap(Invoice::getId, Function.identity()));
    }

    private Function<Receivable, ReceivableSummary> summarize(Map<Long, Customer> customers,
                                                              Map<Long, Invoice> invoices) {
        return title -> summary(title, customers.get(title.getCustomerId()), invoices.get(title.getInvoiceId()));
    }

    private ReceivableSummary summary(Receivable title, Customer customer, Invoice invoice) {
        LocalDate today = LocalDate.now(clock);
        boolean overdue = title.isOverdue(today);
        return new ReceivableSummary(title.getId(), title.getDocument(), title.getCustomerId(),
                customer == null ? null : customer.displayName(), title.getInvoiceId(),
                invoice == null ? null : invoice.getNumber(), title.getInstallment(), title.getInstallmentsTotal(),
                title.getIssueDate(), title.getDueDate(), title.getAmount(), title.getPaidAmount(),
                title.openAmount(), title.getStatus().name(), title.getStatus().getLabel(), overdue,
                overdue ? ChronoUnit.DAYS.between(title.getDueDate(), today) : 0);
    }

    private static boolean inRange(LocalDate date, LocalDate from, LocalDate to) {
        return !date.isBefore(from) && !date.isAfter(to);
    }

    private static BigDecimal sum(List<Receivable> titles) {
        return titles.stream().map(Receivable::openAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
