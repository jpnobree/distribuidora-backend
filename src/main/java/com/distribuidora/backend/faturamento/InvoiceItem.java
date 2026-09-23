package com.distribuidora.backend.faturamento;

import jakarta.persistence.*;

import java.math.BigDecimal;

// Linha da nota: quantidade = peso real separado; qty_ordered guarda o que o
// cliente pediu, para explicar a diferenca de peso variavel.
@Entity
@Table(name = "invoice_items")
public class InvoiceItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "invoice_id")
    private Invoice invoice;

    @Column(nullable = false)
    private Long orderItemId;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private String baseUnit;

    @Column(nullable = false)
    private BigDecimal quantity;

    @Column(nullable = false)
    private BigDecimal qtyOrdered;

    @Column(nullable = false)
    private BigDecimal unitPrice;

    @Column(nullable = false)
    private BigDecimal discountPercent = BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal lineTotal;

    private BigDecimal unitCost;
    private BigDecimal lineCost;

    protected InvoiceItem() {
    }

    InvoiceItem(Long orderItemId, Long productId, String description, String baseUnit, BigDecimal quantity,
                BigDecimal qtyOrdered, BigDecimal unitPrice, BigDecimal discountPercent, BigDecimal lineTotal,
                BigDecimal unitCost, BigDecimal lineCost) {
        this.orderItemId = orderItemId;
        this.productId = productId;
        this.description = description;
        this.baseUnit = baseUnit;
        this.quantity = quantity;
        this.qtyOrdered = qtyOrdered;
        this.unitPrice = unitPrice;
        this.discountPercent = discountPercent;
        this.lineTotal = lineTotal;
        this.unitCost = unitCost;
        this.lineCost = lineCost;
    }

    void attach(Invoice invoice) {
        this.invoice = invoice;
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

    public String getDescription() {
        return description;
    }

    public String getBaseUnit() {
        return baseUnit;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getQtyOrdered() {
        return qtyOrdered;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public BigDecimal getDiscountPercent() {
        return discountPercent;
    }

    public BigDecimal getLineTotal() {
        return lineTotal;
    }

    public BigDecimal getUnitCost() {
        return unitCost;
    }

    public BigDecimal getLineCost() {
        return lineCost;
    }
}
