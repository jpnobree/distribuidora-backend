package com.distribuidora.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.math.BigDecimal;
import java.util.Objects;

// 1 <unitCode> = <factor> unidades base. Ex.: 1 CX = 12 KG.
// nominal = peso variavel: o fator e aproximado e o peso real sai na separacao.
@Embeddable
public class ProductUnit {

    @Column(name = "unit_code", nullable = false)
    private String unitCode;

    @Column(nullable = false)
    private BigDecimal factor;

    @Column(nullable = false)
    private boolean nominal;

    @Column
    private String barcode;

    protected ProductUnit() {
    }

    public ProductUnit(String unitCode, BigDecimal factor, boolean nominal, String barcode) {
        this.unitCode = unitCode;
        this.factor = factor;
        this.nominal = nominal;
        this.barcode = barcode;
    }

    public String getUnitCode() {
        return unitCode;
    }

    public BigDecimal getFactor() {
        return factor;
    }

    public boolean isNominal() {
        return nominal;
    }

    public String getBarcode() {
        return barcode;
    }

    // Identidade = unidade: um produto tem no maximo uma conversao por unidade.
    @Override
    public boolean equals(Object o) {
        return o instanceof ProductUnit other && Objects.equals(unitCode, other.unitCode);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(unitCode);
    }
}
