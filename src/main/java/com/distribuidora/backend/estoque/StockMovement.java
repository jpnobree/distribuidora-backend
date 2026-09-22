package com.distribuidora.backend.estoque;

import com.distribuidora.backend.estoque.StockEnums.Bucket;
import com.distribuidora.backend.estoque.StockEnums.LossReason;
import com.distribuidora.backend.estoque.StockEnums.MovementType;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

// Somente inclusao (trigger na V5). Correcao = novo movimento no sentido oposto.
@Entity
@Table(name = "stock_movements")
public class StockMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private Instant occurredAt = Instant.now();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private MovementType type;

    @Column(nullable = false, updatable = false)
    private Long warehouseId;

    @Column(nullable = false, updatable = false)
    private Long productId;

    @Column(updatable = false)
    private Long lotId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private Bucket fromBucket;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private Bucket toBucket;

    @Column(nullable = false, updatable = false)
    private BigDecimal quantity;

    @Column(updatable = false)
    private BigDecimal unitCost;

    @Enumerated(EnumType.STRING)
    @Column(updatable = false)
    private LossReason lossReason;

    @Column(updatable = false)
    private String reason;

    @Column(updatable = false)
    private String document;

    @Column(updatable = false)
    private Long userId;

    @Column(updatable = false)
    private String username;

    @Column(updatable = false)
    private String sourceType;

    @Column(updatable = false)
    private Long sourceId;

    @Column(updatable = false)
    private UUID groupId;

    protected StockMovement() {
    }

    StockMovement(MovementType type, Long warehouseId, Long productId, Long lotId, BigDecimal quantity,
                  BigDecimal unitCost, LossReason lossReason, String reason, String document, Long userId,
                  String username, String sourceType, Long sourceId, UUID groupId) {
        this.type = type;
        this.warehouseId = warehouseId;
        this.productId = productId;
        this.lotId = lotId;
        this.fromBucket = type.from();
        this.toBucket = type.to();
        this.quantity = quantity;
        this.unitCost = unitCost;
        this.lossReason = lossReason;
        this.reason = reason;
        this.document = document;
        this.userId = userId;
        this.username = username;
        this.sourceType = sourceType;
        this.sourceId = sourceId;
        this.groupId = groupId;
    }

    public Long getId() {
        return id;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public MovementType getType() {
        return type;
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

    public BigDecimal getUnitCost() {
        return unitCost;
    }

    public LossReason getLossReason() {
        return lossReason;
    }

    public String getReason() {
        return reason;
    }

    public String getDocument() {
        return document;
    }

    public String getUsername() {
        return username;
    }

    public UUID getGroupId() {
        return groupId;
    }
}
