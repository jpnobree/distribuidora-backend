package com.distribuidora.backend.financeiro;

import com.distribuidora.backend.exception.BusinessRuleException;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

// Titulo a pagar: uma parcela da nota do fornecedor. Espelha o titulo a
// receber, e as baixas dos dois vao para o mesmo livro.
@Entity
@Table(name = "payables")
public class Payable {

    public enum Status {
        ABERTO("Em aberto"),
        PARCIAL("Parcialmente pago"),
        PAGO("Pago"),
        CANCELADO("Cancelado");

        private final String label;

        Status(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }

        public boolean isOpen() {
            return this == ABERTO || this == PARCIAL;
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long receiptId;

    @Column(nullable = false)
    private Long supplierId;

    @Column(nullable = false)
    private String document;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private int installment;

    @Column(name = "installments_total", nullable = false)
    private int installmentsTotal;

    @Column(nullable = false)
    private LocalDate issueDate;

    @Column(nullable = false)
    private LocalDate dueDate;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private BigDecimal paidAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.ABERTO;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    @Version
    private long version;

    protected Payable() {
    }

    Payable(Long receiptId, Long supplierId, String document, String description, int installment,
            int installmentsTotal, LocalDate issueDate, LocalDate dueDate, BigDecimal amount) {
        this.receiptId = receiptId;
        this.supplierId = supplierId;
        this.document = document;
        this.description = description;
        this.installment = installment;
        this.installmentsTotal = installmentsTotal;
        this.issueDate = issueDate;
        this.dueDate = dueDate;
        this.amount = amount;
    }

    public BigDecimal openAmount() {
        return amount.subtract(paidAmount);
    }

    public boolean isOverdue(LocalDate today) {
        return status.isOpen() && dueDate.isBefore(today);
    }

    void pay(BigDecimal value) {
        if (!status.isOpen()) {
            throw new BusinessRuleException("Titulo " + document + " nao esta em aberto.");
        }
        if (value.compareTo(openAmount()) > 0) {
            throw new BusinessRuleException("Valor maior que o saldo do titulo " + document + ".");
        }
        paidAmount = paidAmount.add(value);
        status = openAmount().signum() == 0 ? Status.PAGO : Status.PARCIAL;
        updatedAt = Instant.now();
    }

    void reverse(BigDecimal value) {
        if (status == Status.CANCELADO) {
            throw new BusinessRuleException("Titulo cancelado nao aceita estorno.");
        }
        paidAmount = paidAmount.subtract(value);
        if (paidAmount.signum() < 0) {
            throw new BusinessRuleException("Estorno maior que o pago no titulo " + document + ".");
        }
        status = paidAmount.signum() == 0 ? Status.ABERTO : Status.PARCIAL;
        updatedAt = Instant.now();
    }

    void cancel() {
        if (paidAmount.signum() > 0) {
            throw new BusinessRuleException("O titulo " + document + " ja tem pagamento. "
                    + "Estorne a baixa antes de cancelar.");
        }
        status = Status.CANCELADO;
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getReceiptId() {
        return receiptId;
    }

    public Long getSupplierId() {
        return supplierId;
    }

    public String getDocument() {
        return document;
    }

    public String getDescription() {
        return description;
    }

    public int getInstallment() {
        return installment;
    }

    public int getInstallmentsTotal() {
        return installmentsTotal;
    }

    public LocalDate getIssueDate() {
        return issueDate;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getPaidAmount() {
        return paidAmount;
    }

    public Status getStatus() {
        return status;
    }

    public long getVersion() {
        return version;
    }
}
