package com.distribuidora.backend.compras;

import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "goods_receipt_items")
public class GoodsReceiptItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "receipt_id")
    private GoodsReceipt receipt;

    @Column(nullable = false)
    private Long purchaseOrderItemId;

    @Column(nullable = false)
    private Long productId;

    private Long lotId;

    @Column(nullable = false)
    private BigDecimal qtyBase;

    @Column(nullable = false)
    private BigDecimal unitCost;

    @Column(nullable = false)
    private BigDecimal lineTotal;

    // quanto o custo desta chegada difere do combinado no pedido
    private BigDecimal costDifference;

    protected GoodsReceiptItem() {
    }

    GoodsReceiptItem(Long purchaseOrderItemId, Long productId, Long lotId, BigDecimal qtyBase, BigDecimal unitCost,
                     BigDecimal lineTotal, BigDecimal costDifference) {
        this.purchaseOrderItemId = purchaseOrderItemId;
        this.productId = productId;
        this.lotId = lotId;
        this.qtyBase = qtyBase;
        this.unitCost = unitCost;
        this.lineTotal = lineTotal;
        this.costDifference = costDifference;
    }

    void attach(GoodsReceipt receipt) {
        this.receipt = receipt;
    }

    public Long getId() {
        return id;
    }

    public Long getPurchaseOrderItemId() {
        return purchaseOrderItemId;
    }

    public Long getProductId() {
        return productId;
    }

    public Long getLotId() {
        return lotId;
    }

    public BigDecimal getQtyBase() {
        return qtyBase;
    }

    public BigDecimal getUnitCost() {
        return unitCost;
    }

    public BigDecimal getLineTotal() {
        return lineTotal;
    }

    public BigDecimal getCostDifference() {
        return costDifference;
    }
}
