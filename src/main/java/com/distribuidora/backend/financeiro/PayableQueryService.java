package com.distribuidora.backend.financeiro;

import com.distribuidora.backend.cadastros.Supplier;
import com.distribuidora.backend.cadastros.SupplierRepository;
import com.distribuidora.backend.compras.GoodsReceipt;
import com.distribuidora.backend.compras.GoodsReceiptRepository;
import com.distribuidora.backend.financeiro.FinanceDtos.PayableSummary;
import com.distribuidora.backend.financeiro.FinanceDtos.PayableView;
import com.distribuidora.backend.financeiro.FinanceDtos.PayablesTotals;
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
public class PayableQueryService {

    private final PayableRepository payableRepository;
    private final FinancialTransactionRepository transactionRepository;
    private final SupplierRepository supplierRepository;
    private final GoodsReceiptRepository receiptRepository;
    private final CurrentUser currentUser;
    private final Clock clock;

    public PayableQueryService(PayableRepository payableRepository,
                               FinancialTransactionRepository transactionRepository,
                               SupplierRepository supplierRepository, GoodsReceiptRepository receiptRepository,
                               CurrentUser currentUser, Clock clock) {
        this.payableRepository = payableRepository;
        this.transactionRepository = transactionRepository;
        this.supplierRepository = supplierRepository;
        this.receiptRepository = receiptRepository;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<PayableSummary> search(Payable.Status status, Long supplierId, boolean openOnly, boolean overdueOnly,
                                       LocalDate dueFrom, LocalDate dueTo, String search, int page, int size) {
        Specification<Payable> spec = filter(status, supplierId, openOnly, overdueOnly, dueFrom, dueTo, search);
        Page<Payable> titles = payableRepository.findAll(spec,
                PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "dueDate", "id")));
        Map<Long, Supplier> suppliers = suppliers(titles.getContent());
        Map<Long, GoodsReceipt> receipts = receipts(titles.getContent());
        return titles.map(t -> summary(t, suppliers.get(t.getSupplierId()), receipts.get(t.getReceiptId())));
    }

    @Transactional(readOnly = true)
    public PayablesTotals totals(Long supplierId) {
        List<Payable> open = payableRepository.findAll(
                filter(null, supplierId, true, false, null, null, null));
        LocalDate today = LocalDate.now(clock);
        BigDecimal overdue = sum(open.stream().filter(p -> p.getDueDate().isBefore(today)).toList());
        BigDecimal dueToday = sum(open.stream().filter(p -> p.getDueDate().isEqual(today)).toList());
        BigDecimal in7 = sum(open.stream().filter(p -> inRange(p.getDueDate(), today, today.plusDays(7))).toList());
        BigDecimal in30 = sum(open.stream().filter(p -> inRange(p.getDueDate(), today, today.plusDays(30))).toList());

        LocalDate monthStart = today.withDayOfMonth(1);
        List<Long> ids = payableRepository.findAll(filter(null, supplierId, false, false, null, null, null)).stream()
                .map(Payable::getId).toList();
        BigDecimal paid = ids.isEmpty() ? BigDecimal.ZERO
                : transactionRepository.findByPayableIdInOrderByIdAsc(ids).stream()
                .filter(t -> !t.getPaidOn().isBefore(monthStart))
                .map(t -> t.getType() == FinancialTransaction.Type.BAIXA ? t.received() : t.received().negate())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new PayablesTotals(sum(open), overdue, dueToday, in7, in30, paid,
                open.stream().filter(p -> p.getDueDate().isBefore(today)).count(), open.size());
    }

    @Transactional(readOnly = true)
    public PayableView view(Long id) {
        return toView(payableRepository.findById(id)
                .orElseThrow(() -> new com.distribuidora.backend.exception.ResourceNotFoundException(
                        "Titulo nao encontrado: " + id)));
    }

    @Transactional(readOnly = true)
    public PayableView toView(Payable title) {
        Supplier supplier = supplierRepository.findById(title.getSupplierId()).orElse(null);
        GoodsReceipt receipt = title.getReceiptId() == null ? null
                : receiptRepository.findById(title.getReceiptId()).orElse(null);
        List<FinancialTransaction> transactions = transactionRepository.findByPayableIdOrderByIdAsc(title.getId());
        Set<Long> reversed = transactions.stream().map(FinancialTransaction::getReversalOf)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        List<TransactionView> extract = transactions.stream()
                .map(t -> new TransactionView(t.getId(), t.getType().name(), t.getAmount(), t.getInterest(),
                        t.getDiscount(), t.received(), t.getPaidOn(), t.getMethod().name(), t.getMethod().getLabel(),
                        t.getNotes(), t.getUsername(), t.getOccurredAt(), reversed.contains(t.getId()),
                        t.getReversalOf()))
                .toList();

        return new PayableView(summary(title, supplier, receipt),
                supplier == null ? null : supplier.getDocument(), extract,
                title.getStatus().isOpen() && currentUser.can(PayableService.BAIXAR),
                title.getStatus().isOpen() && title.getPaidAmount().signum() == 0
                        && currentUser.can(PayableService.CANCELAR),
                title.getVersion());
    }

    // ------------------------------------------------------------- apoio

    private Specification<Payable> filter(Payable.Status status, Long supplierId, boolean openOnly,
                                          boolean overdueOnly, LocalDate dueFrom, LocalDate dueTo, String search) {
        Specification<Payable> spec = Specification.where(null);
        if (status != null) {
            spec = spec.and((r, q, cb) -> cb.equal(r.get("status"), status));
        }
        if (supplierId != null) {
            spec = spec.and((r, q, cb) -> cb.equal(r.get("supplierId"), supplierId));
        }
        if (openOnly) {
            spec = spec.and((r, q, cb) -> r.get("status").in(Payable.Status.ABERTO, Payable.Status.PARCIAL));
        }
        if (overdueOnly) {
            LocalDate today = LocalDate.now(clock);
            spec = spec.and((r, q, cb) -> cb.and(cb.lessThan(r.get("dueDate"), today),
                    r.get("status").in(Payable.Status.ABERTO, Payable.Status.PARCIAL)));
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
                var s = found.from(Supplier.class);
                found.select(s.get("id")).where(cb.or(cb.like(cb.lower(s.get("legalName")), like),
                        cb.like(cb.lower(s.get("tradeName")), like)));
                return cb.or(cb.in(r.get("supplierId")).value(found), cb.like(cb.lower(r.get("document")), like));
            });
        }
        return spec;
    }

    private PayableSummary summary(Payable title, Supplier supplier, GoodsReceipt receipt) {
        LocalDate today = LocalDate.now(clock);
        boolean overdue = title.isOverdue(today);
        return new PayableSummary(title.getId(), title.getDocument(), title.getDescription(), title.getSupplierId(),
                supplier == null ? null : supplier.displayName(), title.getReceiptId(),
                receipt == null ? null : receipt.getPurchaseOrderId(),
                title.getExpenseCategory() == null ? null : title.getExpenseCategory().name(),
                title.getExpenseCategory() == null ? null : title.getExpenseCategory().getLabel(),
                title.getInstallment(),
                title.getInstallmentsTotal(), title.getIssueDate(), title.getDueDate(), title.getAmount(),
                title.getPaidAmount(), title.openAmount(), title.getStatus().name(), title.getStatus().getLabel(),
                overdue, overdue ? ChronoUnit.DAYS.between(title.getDueDate(), today) : 0);
    }

    private Map<Long, Supplier> suppliers(Collection<Payable> titles) {
        return supplierRepository.findAllById(titles.stream().map(Payable::getSupplierId).distinct().toList())
                .stream().collect(Collectors.toMap(Supplier::getId, Function.identity()));
    }

    private Map<Long, GoodsReceipt> receipts(Collection<Payable> titles) {
        return receiptRepository.findAllById(titles.stream().map(Payable::getReceiptId)
                        .filter(Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(GoodsReceipt::getId, Function.identity()));
    }

    private static boolean inRange(LocalDate date, LocalDate from, LocalDate to) {
        return !date.isBefore(from) && !date.isAfter(to);
    }

    private static BigDecimal sum(List<Payable> titles) {
        return titles.stream().map(Payable::openAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
