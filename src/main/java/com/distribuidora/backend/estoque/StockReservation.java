package com.distribuidora.backend.estoque;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "stock_reservations")
public class StockReservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long orderItemId;

    @Column(nullable = false)
    private Long warehouseId;

    @Column(nullable = false)
    private Long productId;

    private Long lotId;

    @Column(nullable = false)
    private BigDecimal quantity;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    private Instant releasedAt;

    protected StockReservation() {
    }

    StockReservation(Long orderItemId, Long warehouseId, Long productId, Long lotId, BigDecimal quantity) {
        this.orderItemId = orderItemId;
        this.warehouseId = warehouseId;
        this.productId = productId;
        this.lotId = lotId;
        this.quantity = quantity;
    }

    void markReleased() {
        releasedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getOrderItemId() {
        return orderItemId;
    }

    public Long getWarehouseId() {
        return warehouseId;
    }

    public Long getProductId() {
        return productId;
    }

    public Long getLotId() {
        return lotId;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public Instant getReleasedAt() {
        return releasedAt;
    }
}
