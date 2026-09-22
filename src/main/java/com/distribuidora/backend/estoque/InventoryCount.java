package com.distribuidora.backend.estoque;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "inventory_counts")
public class InventoryCount {

    public enum Status { ABERTO, FECHADO, CANCELADO }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long warehouseId;

    // null = todas as categorias
    private String category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.ABERTO;

    private String notes;

    @Column(nullable = false)
    private String createdBy;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    private String closedBy;
    private Instant closedAt;

    @OneToMany(mappedBy = "count", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<Item> items = new ArrayList<>();

    protected InventoryCount() {
    }

    public InventoryCount(Long warehouseId, String category, String notes, String createdBy) {
        this.warehouseId = warehouseId;
        this.category = category;
        this.notes = notes;
        this.createdBy = createdBy;
    }

    void addItem(Long productId, Long lotId, BigDecimal systemQty) {
        items.add(new Item(this, productId, lotId, systemQty));
    }

    void close(String username) {
        status = Status.FECHADO;
        closedBy = username;
        closedAt = Instant.now();
    }

    void cancel(String username) {
        status = Status.CANCELADO;
        closedBy = username;
        closedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getWarehouseId() {
        return warehouseId;
    }

    public String getCategory() {
        return category;
    }

    public Status getStatus() {
        return status;
    }

    public String getNotes() {
        return notes;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getClosedBy() {
        return closedBy;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public List<Item> getItems() {
        return items;
    }

    @Entity
    @Table(name = "inventory_count_items")
    public static class Item {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @ManyToOne(optional = false)
        @JoinColumn(name = "count_id")
        private InventoryCount count;

        @Column(nullable = false)
        private Long productId;

        private Long lotId;

        @Column(nullable = false)
        private BigDecimal systemQty;

        private BigDecimal countedQty;

        protected Item() {
        }

        Item(InventoryCount count, Long productId, Long lotId, BigDecimal systemQty) {
            this.count = count;
            this.productId = productId;
            this.lotId = lotId;
            this.systemQty = systemQty;
        }

        public Long getId() {
            return id;
        }

        public Long getProductId() {
            return productId;
        }

        public Long getLotId() {
            return lotId;
        }

        public BigDecimal getSystemQty() {
            return systemQty;
        }

        public BigDecimal getCountedQty() {
            return countedQty;
        }

        void setCountedQty(BigDecimal countedQty) {
            this.countedQty = countedQty;
        }
    }
}
