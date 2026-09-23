package com.distribuidora.backend.financeiro;

import com.distribuidora.backend.exception.BusinessRuleException;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

// Titulo a receber: uma parcela de uma nota. O valor nunca e apagado nem
// reescrito por fora; quem move o saldo e a baixa (FinancialTransaction).
@Entity
@Table(name = "receivables")
public class Receivable {

    public enum Status {
        ABERTO("Em aberto"),
        PARCIAL("Parcialmente recebido"),
        PAGO("Recebido"),
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
    private Long invoiceId;

    @Column(nullable = false)
    private Long customerId;

    @Column(nullable = false)
    private String document;

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

    protected Receivable() {
    }

    Receivable(Long invoiceId, Long customerId, String document, int installment, int installmentsTotal,
               LocalDate issueDate, LocalDate dueDate, BigDecimal amount) {
        this.invoiceId = invoiceId;
        this.customerId = customerId;
        this.document = document;
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

    void receive(BigDecimal value) {
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
            throw new BusinessRuleException("Estorno maior que o recebido no titulo " + document + ".");
        }
        status = paidAmount.signum() == 0 ? Status.ABERTO : Status.PARCIAL;
        updatedAt = Instant.now();
    }

    void cancel() {
        if (paidAmount.signum() > 0) {
            throw new BusinessRuleException("O titulo " + document + " ja tem recebimento. "
                    + "Estorne a baixa antes de cancelar.");
        }
        status = Status.CANCELADO;
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getInvoiceId() {
        return invoiceId;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public String getDocument() {
        return document;
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public long getVersion() {
        return version;
    }
}
