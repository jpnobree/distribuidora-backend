package com.distribuidora.backend.comercial;

import jakarta.persistence.*;

import java.math.BigDecimal;

// Preco, custo e desconto gravados no momento da venda: mudar o cadastro
// depois nao altera o que foi vendido.
@Entity
@Table(name = "sales_order_items")
public class SalesOrderItem {

    public enum PriceSource { TABELA_CLIENTE, PRECO_PRODUTO }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "order_id")
    private SalesOrder order;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false)
    private String unitCode;

    @Column(nullable = false)
    private BigDecimal quantity;

    @Column(nullable = false)
    private BigDecimal factor;

    @Column(nullable = false)
    private boolean nominal;

    @Column(nullable = false)
    private BigDecimal qtyBase;

    @Column(nullable = false)
    private BigDecimal listPrice;

    @Column(nullable = false)
    private BigDecimal unitPrice;

    @Column(nullable = false)
    private BigDecimal discountPercent;

    @Column(nullable = false)
    private BigDecimal lineTotal;

    private BigDecimal unitCostSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PriceSource priceSource;

    protected SalesOrderItem() {
    }

    SalesOrderItem(Long productId, String unitCode, BigDecimal quantity, BigDecimal factor, boolean nominal,
                   BigDecimal qtyBase, BigDecimal listPrice, BigDecimal unitPrice, BigDecimal discountPercent,
                   BigDecimal lineTotal, BigDecimal unitCostSnapshot, PriceSource priceSource) {
        this.productId = productId;
        this.unitCode = unitCode;
        this.quantity = quantity;
        this.factor = factor;
        this.nominal = nominal;
        this.qtyBase = qtyBase;
        this.listPrice = listPrice;
        this.unitPrice = unitPrice;
        this.discountPercent = discountPercent;
        this.lineTotal = lineTotal;
        this.unitCostSnapshot = unitCostSnapshot;
        this.priceSource = priceSource;
    }

    void attach(SalesOrder order) {
        this.order = order;
    }

    public Long getId() {
        return id;
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

    public boolean isNominal() {
        return nominal;
    }

    public BigDecimal getQtyBase() {
        return qtyBase;
    }

    public BigDecimal getListPrice() {
        return listPrice;
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

    public BigDecimal getUnitCostSnapshot() {
        return unitCostSnapshot;
    }

    public PriceSource getPriceSource() {
        return priceSource;
    }
}
