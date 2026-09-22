package com.distribuidora.backend.comercial;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "sales_orders")
public class SalesOrder {

    // O pedido acompanha o ciclo ate o dinheiro: aprovado reserva o estoque,
    // separado tem peso real conferido, faturado ja virou nota e titulo.
    public enum Status {
        AGUARDANDO_APROVACAO("Aguardando aprovação"),
        APROVADO("Aprovado"),
        EM_SEPARACAO("Em separação"),
        SEPARADO("Separado"),
        FATURADO("Faturado"),
        CANCELADO("Cancelado");

        private final String label;

        Status(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }

        public boolean isOpen() {
            return this != FATURADO && this != CANCELADO;
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long customerId;

    private Long sellerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    private Long paymentTermId;
    private Long priceTableId;
    private LocalDate expectedDeliveryOn;
    private String notes;

    @Column(nullable = false)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal discountTotal = BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal total = BigDecimal.ZERO;

    private BigDecimal estimatedCost;

    @Column(nullable = false)
    private boolean hasEstimatedWeight;

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
    private List<SalesOrderItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<Block> blocks = new ArrayList<>();

    protected SalesOrder() {
    }

    SalesOrder(Long customerId, Long sellerId, Long paymentTermId, Long priceTableId, LocalDate expectedDeliveryOn,
               String notes, String createdBy) {
        this.customerId = customerId;
        this.sellerId = sellerId;
        this.paymentTermId = paymentTermId;
        this.priceTableId = priceTableId;
        this.expectedDeliveryOn = expectedDeliveryOn;
        this.notes = notes;
        this.createdBy = createdBy;
        this.status = Status.AGUARDANDO_APROVACAO;
    }

    void addItem(SalesOrderItem item) {
        item.attach(this);
        items.add(item);
    }

    void addBlock(Block.Type type, String detail) {
        blocks.add(new Block(this, type, detail));
    }

    void recalculate() {
        subtotal = items.stream().map(i -> i.getQtyBase().multiply(i.getListPrice()))
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, java.math.RoundingMode.HALF_UP);
        total = items.stream().map(SalesOrderItem::getLineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        discountTotal = subtotal.subtract(total).max(BigDecimal.ZERO);
        boolean costKnown = items.stream().allMatch(i -> i.getUnitCostSnapshot() != null);
        estimatedCost = costKnown ? items.stream().map(i -> i.getQtyBase().multiply(i.getUnitCostSnapshot()))
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, java.math.RoundingMode.HALF_UP) : null;
        hasEstimatedWeight = items.stream().anyMatch(SalesOrderItem::isNominal);
    }

    void approve(String username) {
        status = Status.APROVADO;
        approvedBy = username;
        approvedAt = Instant.now();
        updatedAt = approvedAt;
    }

    // Transicoes conduzidas pela expedicao e pelo faturamento.
    public void moveTo(Status next) {
        status = next;
        updatedAt = Instant.now();
    }

    void cancel(String username, String reason) {
        status = Status.CANCELADO;
        cancelledBy = username;
        cancelledAt = Instant.now();
        cancelReason = reason;
        updatedAt = cancelledAt;
    }

    public List<Block> pendingBlocks() {
        return blocks.stream().filter(b -> b.getResolvedAt() == null).toList();
    }

    public Long getId() {
        return id;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public Long getSellerId() {
        return sellerId;
    }

    public Status getStatus() {
        return status;
    }

    public Long getPaymentTermId() {
        return paymentTermId;
    }

    public Long getPriceTableId() {
        return priceTableId;
    }

    public LocalDate getExpectedDeliveryOn() {
        return expectedDeliveryOn;
    }

    public String getNotes() {
        return notes;
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

    public BigDecimal getEstimatedCost() {
        return estimatedCost;
    }

    public boolean isHasEstimatedWeight() {
        return hasEstimatedWeight;
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

    public List<SalesOrderItem> getItems() {
        return items;
    }

    public List<Block> getBlocks() {
        return blocks;
    }

    @Entity
    @Table(name = "sales_order_blocks")
    public static class Block {

        public enum Type {
            DESCONTO("Desconto acima do limite", "pedidos.aprovar_desconto"),
            ABAIXO_CUSTO("Preço abaixo do custo", "pedidos.aprovar_desconto"),
            LIMITE_CREDITO("Limite de crédito excedido", "credito.liberar"),
            CLIENTE_BLOQUEADO("Cliente bloqueado", "credito.liberar");

            private final String label;
            private final String permission;

            Type(String label, String permission) {
                this.label = label;
                this.permission = permission;
            }

            public String getLabel() {
                return label;
            }

            public String getPermission() {
                return permission;
            }
        }

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @ManyToOne(optional = false)
        @JoinColumn(name = "order_id")
        private SalesOrder order;

        @Enumerated(EnumType.STRING)
        @Column(nullable = false)
        private Type type;

        @Column(nullable = false)
        private String detail;

        private String resolvedBy;
        private Instant resolvedAt;

        protected Block() {
        }

        Block(SalesOrder order, Type type, String detail) {
            this.order = order;
            this.type = type;
            this.detail = detail;
        }

        void resolve(String username) {
            resolvedBy = username;
            resolvedAt = Instant.now();
        }

        public Long getId() {
            return id;
        }

        public Type getType() {
            return type;
        }

        public String getDetail() {
            return detail;
        }

        public String getResolvedBy() {
            return resolvedBy;
        }

        public Instant getResolvedAt() {
            return resolvedAt;
        }
    }
}
