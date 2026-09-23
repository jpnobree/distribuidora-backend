package com.distribuidora.backend.compras;

import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "purchase_order_items")
public class PurchaseOrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "purchase_order_id")
    private PurchaseOrder order;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false)
    private String unitCode;

    @Column(nullable = false)
    private BigDecimal quantity;

    @Column(nullable = false)
    private BigDecimal factor;

    @Column(nullable = false)
    private BigDecimal qtyBase;

    // custo por unidade base: e assim que o estoque e o custo medio trabalham
    @Column(nullable = false)
    private BigDecimal unitCost;

    @Column(nullable = false)
    private BigDecimal lineTotal;

    @Column(nullable = false)
    private BigDecimal qtyReceived = BigDecimal.ZERO;

    protected PurchaseOrderItem() {
    }

    PurchaseOrderItem(Long productId, String unitCode, BigDecimal quantity, BigDecimal factor, BigDecimal qtyBase,
                      BigDecimal unitCost, BigDecimal lineTotal) {
        this.productId = productId;
        this.unitCode = unitCode;
        this.quantity = quantity;
        this.factor = factor;
        this.qtyBase = qtyBase;
        this.unitCost = unitCost;
        this.lineTotal = lineTotal;
    }

    void attach(PurchaseOrder order) {
        this.order = order;
    }

    void receive(BigDecimal quantity) {
        qtyReceived = qtyReceived.add(quantity);
    }

    public BigDecimal pending() {
        return qtyBase.subtract(qtyReceived).max(BigDecimal.ZERO);
    }

    public Long getId() {
        return id;
    }

    public PurchaseOrder getOrder() {
        return order;
    }

    public Long getProductId() {
        return productId;
    }

    public String getUnitCode() {
        return unitCode;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getFactor() {
        return factor;
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

    public BigDecimal getQtyReceived() {
        return qtyReceived;
    }
}
