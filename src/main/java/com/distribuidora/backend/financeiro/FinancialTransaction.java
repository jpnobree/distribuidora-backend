package com.distribuidora.backend.financeiro;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

// Recebimento ou estorno. Somente inclusao (trigger na V7): baixa errada se
// corrige com um estorno, nunca apagando a linha.
@Entity
@Table(name = "financial_transactions")
public class FinancialTransaction {

    public enum Type { BAIXA, ESTORNO }

    public enum Method {
        DINHEIRO("Dinheiro"),
        PIX("PIX"),
        BOLETO("Boleto"),
        TRANSFERENCIA("Transferência"),
        CARTAO("Cartão"),
        CHEQUE("Cheque"),
        OUTRO("Outro");

        private final String label;

        Method(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private Long receivableId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private Type type;

    @Column(nullable = false, updatable = false)
    private BigDecimal amount;

    @Column(nullable = false, updatable = false)
    private BigDecimal interest = BigDecimal.ZERO;

    @Column(nullable = false, updatable = false)
    private BigDecimal discount = BigDecimal.ZERO;

    @Column(nullable = false, updatable = false)
    private LocalDate paidOn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private Method method;

    @Column(updatable = false)
    private String notes;

    @Column(updatable = false)
    private Long userId;

    @Column(nullable = false, updatable = false)
    private String username;

    @Column(nullable = false, updatable = false)
    private Instant occurredAt = Instant.now();

    @Column(updatable = false)
    private Long reversalOf;

    protected FinancialTransaction() {
    }

    FinancialTransaction(Long receivableId, Type type, BigDecimal amount, BigDecimal interest, BigDecimal discount,
                         LocalDate paidOn, Method method, String notes, Long userId, String username, Long reversalOf) {
        this.receivableId = receivableId;
        this.type = type;
        this.amount = amount;
        this.interest = interest;
        this.discount = discount;
        this.paidOn = paidOn;
        this.method = method;
        this.notes = notes;
        this.userId = userId;
        this.username = username;
        this.reversalOf = reversalOf;
    }

    // Quanto entrou no caixa: principal + juros/multa - desconto concedido.
    public BigDecimal received() {
        return amount.add(interest).subtract(discount);
    }

    public Long getId() {
        return id;
    }

    public Long getReceivableId() {
        return receivableId;
    }

    public Type getType() {
        return type;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getInterest() {
        return interest;
    }

    public BigDecimal getDiscount() {
        return discount;
    }

    public LocalDate getPaidOn() {
        return paidOn;
    }

    public Method getMethod() {
        return method;
    }

    public String getNotes() {
        return notes;
    }

    public String getUsername() {
        return username;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public Long getReversalOf() {
        return reversalOf;
    }
}
