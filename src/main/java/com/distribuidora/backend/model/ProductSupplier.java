package com.distribuidora.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.math.BigDecimal;
import java.util.Objects;

// Fornecedor que tambem vende o produto (alternativo ao principal).
@Embeddable
public class ProductSupplier {

    @Column(name = "supplier_id", nullable = false)
    private Long supplierId;

    @Column
    private String supplierSku;

    @Column
    private BigDecimal lastCost;

    @Column
    private Integer leadTimeDays;

    protected ProductSupplier() {
    }

    public ProductSupplier(Long supplierId, String supplierSku, BigDecimal lastCost, Integer leadTimeDays) {
        this.supplierId = supplierId;
        this.supplierSku = supplierSku;
        this.lastCost = lastCost;
        this.leadTimeDays = leadTimeDays;
    }

    public Long getSupplierId() {
        return supplierId;
    }

    public String getSupplierSku() {
        return supplierSku;
    }

    public BigDecimal getLastCost() {
        return lastCost;
    }

    public Integer getLeadTimeDays() {
        return leadTimeDays;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ProductSupplier other && Objects.equals(supplierId, other.supplierId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(supplierId);
    }
}
