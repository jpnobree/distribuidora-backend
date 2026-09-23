package com.distribuidora.backend.compras;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

// Pedido de compra: compromisso de dinheiro com o fornecedor. Nasce aguardando
// aprovacao quando passa do limite do parametro; so pedido aprovado recebe.
@Entity
@Table(name = "purchase_orders")
public class PurchaseOrder {

    public enum Status {
        AGUARDANDO_APROVACAO("Aguardando aprovação"),
        APROVADO("Aprovado"),
        RECEBIDO_PARCIAL("Recebido em parte"),
        RECEBIDO("Recebido"),
        CANCELADO("Cancelado");

        private final String label;

        Status(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }

        public boolean isOpen() {
            return this != RECEBIDO && this != CANCELADO;
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long supplierId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.AGUARDANDO_APROVACAO;

    private Long paymentTermId;

    @Column(nullable = false)
    private Long warehouseId;

    private LocalDate expectedOn;
    private String notes;

    @Column(nullable = false)
    private BigDecimal total = BigDecimal.ZERO;

    @Column(nullable = false)
    private String createdBy;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    private String approvedBy;
    private Instant approvedAt;
    private String cancelledBy;
    private Instant cancelledAt;
    private String cancelReason;

    @Version
    private long version;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<PurchaseOrderItem> items = new ArrayList<>();

    protected PurchaseOrder() {
    }

    PurchaseOrder(Long supplierId, Long warehouseId, Long paymentTermId, LocalDate expectedOn, String notes,
                  String createdBy) {
        this.supplierId = supplierId;
        this.warehouseId = warehouseId;
        this.paymentTermId = paymentTermId;
        this.expectedOn = expectedOn;
        this.notes = notes;
        this.createdBy = createdBy;
    }

    void addItem(PurchaseOrderItem item) {
        item.attach(this);
        items.add(item);
    }

    void recalculate() {
        total = items.stream().map(PurchaseOrderItem::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
    }

    void approve(String username) {
        status = Status.APROVADO;
        approvedBy = username;
        approvedAt = Instant.now();
        updatedAt = approvedAt;
    }

    void cancel(String username, String reason) {
        status = Status.CANCELADO;
        cancelledBy = username;
        cancelledAt = Instant.now();
        cancelReason = reason;
        updatedAt = cancelledAt;
    }

    // Depois de cada recebimento: tudo que foi pedido chegou, ou so parte.
    void refreshReceiving() {
        boolean complete = items.stream().allMatch(i -> i.getQtyReceived().compareTo(i.getQtyBase()) >= 0);
        boolean started = items.stream().anyMatch(i -> i.getQtyReceived().signum() > 0);
        status = complete ? Status.RECEBIDO : started ? Status.RECEBIDO_PARCIAL : Status.APROVADO;
        updatedAt = Instant.now();
    }

    public boolean canReceive() {
        return status == Status.APROVADO || status == Status.RECEBIDO_PARCIAL;
    }

    public Long getId() {
        return id;
    }

    public Long getSupplierId() {
        return supplierId;
    }

    public Status getStatus() {
        return status;
    }

    public Long getPaymentTermId() {
        return paymentTermId;
    }

    public Long getWarehouseId() {
        return warehouseId;
    }

    public LocalDate getExpectedOn() {
        return expectedOn;
    }

    public String getNotes() {
        return notes;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getApprovedBy() {
        return approvedBy;
    }

    public Instant getApprovedAt() {
        return approvedAt;
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

    public List<PurchaseOrderItem> getItems() {
        return items;
    }
}
