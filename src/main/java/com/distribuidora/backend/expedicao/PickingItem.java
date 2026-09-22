package com.distribuidora.backend.expedicao;

import jakarta.persistence.*;

import java.math.BigDecimal;

// Uma linha por lote separado: o FEFO sugere (qtyPlanned), a balanca informa
// o que saiu (qtyPicked) e a conferencia confirma (qtyChecked).
@Entity
@Table(name = "picking_items")
public class PickingItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "picking_id")
    private PickingList picking;

    @Column(nullable = false)
    private Long orderItemId;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false)
    private Long warehouseId;

    private Long lotId;

    @Column(nullable = false)
    private BigDecimal qtyPlanned = BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal qtyPicked = BigDecimal.ZERO;

    private BigDecimal qtyChecked;

    protected PickingItem() {
    }

    PickingItem(Long orderItemId, Long productId, Long warehouseId, Long lotId, BigDecimal qtyPlanned,
                BigDecimal qtyPicked) {
        this.orderItemId = orderItemId;
        this.productId = productId;
        this.warehouseId = warehouseId;
        this.lotId = lotId;
        this.qtyPlanned = qtyPlanned;
        this.qtyPicked = qtyPicked;
    }

    void attach(PickingList picking) {
        this.picking = picking;
    }

    void check(BigDecimal quantity) {
        this.qtyChecked = quantity;
    }

    public Long getId() {
        return id;
    }

    public Long getOrderItemId() {
        return orderItemId;
    }

    public Long getProductId() {
        return productId;
    }

    public Long getWarehouseId() {
        return warehouseId;
    }

    public Long getLotId() {
        return lotId;
    }

    public BigDecimal getQtyPlanned() {
        return qtyPlanned;
    }

    public BigDecimal getQtyPicked() {
        return qtyPicked;
    }

    public BigDecimal getQtyChecked() {
        return qtyChecked;
    }
}
