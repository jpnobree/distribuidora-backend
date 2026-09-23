package com.distribuidora.backend.faturamento;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

// Documento de saida do pedido, com o peso realmente separado. A emissao
// fiscal e uma porta (FiscalDocumentProvider): hoje o documento e interno.
@Entity
@Table(name = "invoices")
public class Invoice {

    public enum Status {
        EMITIDA("Emitida"),
        CANCELADA("Cancelada");

        private final String label;

        Status(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    public enum FiscalStatus {
        INTERNO("Documento interno (sem valor fiscal)"),
        PENDENTE("Aguardando autorização"),
        AUTORIZADO("Autorizado"),
        REJEITADO("Rejeitado");

        private final String label;

        FiscalStatus(String label) {
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
    private Long number;

    @Column(nullable = false, updatable = false)
    private String series;

    @Column(nullable = false, updatable = false)
    private Long orderId;

    @Column(nullable = false, updatable = false)
    private Long pickingId;

    @Column(nullable = false, updatable = false)
    private Long customerId;

    private Long paymentTermId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.EMITIDA;

    @Column(nullable = false, updatable = false)
    private Instant issuedAt = Instant.now();

    @Column(nullable = false, updatable = false)
    private String issuedBy;

    @Column(nullable = false)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal discountTotal = BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal total = BigDecimal.ZERO;

    private BigDecimal costTotal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FiscalStatus fiscalStatus = FiscalStatus.INTERNO;

    private String fiscalNumber;
    private String fiscalKey;
    private String fiscalMessage;
    private Instant fiscalIssuedAt;

    private String cancelledBy;
    private Instant cancelledAt;
    private String cancelReason;

    @Version
    private long version;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<InvoiceItem> items = new ArrayList<>();

    protected Invoice() {
    }

    Invoice(Long number, String series, Long orderId, Long pickingId, Long customerId, Long paymentTermId,
            String issuedBy) {
        this.number = number;
        this.series = series;
        this.orderId = orderId;
        this.pickingId = pickingId;
        this.customerId = customerId;
        this.paymentTermId = paymentTermId;
        this.issuedBy = issuedBy;
    }

    void addItem(InvoiceItem item) {
        item.attach(this);
        items.add(item);
    }

    void recalculate() {
        subtotal = items.stream().map(i -> i.getQuantity().multiply(i.getUnitPrice()))
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        total = items.stream().map(InvoiceItem::getLineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        discountTotal = subtotal.subtract(total).max(BigDecimal.ZERO);
        boolean costKnown = items.stream().allMatch(i -> i.getLineCost() != null);
        costTotal = costKnown
                ? items.stream().map(InvoiceItem::getLineCost).reduce(BigDecimal.ZERO, BigDecimal::add) : null;
    }

    void applyFiscal(FiscalStatus status, String number, String key, String message) {
        this.fiscalStatus = status;
        this.fiscalNumber = number;
        this.fiscalKey = key;
        this.fiscalMessage = message;
        this.fiscalIssuedAt = Instant.now();
    }

    void cancel(String username, String reason) {
        status = Status.CANCELADA;
        cancelledBy = username;
        cancelledAt = Instant.now();
        cancelReason = reason;
    }

    public String display() {
        return number + "/" + series;
    }

    public BigDecimal marginValue() {
        return costTotal == null ? null : total.subtract(costTotal);
    }

    public Long getId() {
        return id;
    }

    public Long getNumber() {
        return number;
    }

    public String getSeries() {
        return series;
    }

    public Long getOrderId() {
        return orderId;
    }

    public Long getPickingId() {
        return pickingId;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public Long getPaymentTermId() {
        return paymentTermId;
    }

    public Status getStatus() {
        return status;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public String getIssuedBy() {
        return issuedBy;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public BigDecimal getDiscountTotal() {
        return discountTotal;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public BigDecimal getCostTotal() {
        return costTotal;
    }

    public FiscalStatus getFiscalStatus() {
        return fiscalStatus;
    }

    public String getFiscalNumber() {
        return fiscalNumber;
    }

    public String getFiscalKey() {
        return fiscalKey;
    }

    public String getFiscalMessage() {
        return fiscalMessage;
    }

    public Instant getFiscalIssuedAt() {
        return fiscalIssuedAt;
    }

    public String getCancelledBy() {
        return cancelledBy;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public long getVersion() {
        return version;
    }

    public List<InvoiceItem> getItems() {
        return items;
    }
}
