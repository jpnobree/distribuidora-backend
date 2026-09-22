package com.distribuidora.backend.comercial;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "system_parameters")
public class SystemParameter {

    public static final String MAX_DISCOUNT = "comercial.desconto_max_percent";
    public static final String WEIGHT_TOLERANCE = "expedicao.tolerancia_peso_percent";
    public static final String DIFFERENT_CHECKER = "expedicao.conferente_diferente";
    public static final String INVOICE_SERIES = "faturamento.serie";

    @Id
    private String key;

    @Column(nullable = false)
    private String value;

    @Column(nullable = false)
    private String description;

    protected SystemParameter() {
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getDescription() {
        return description;
    }
}
