package com.distribuidora.backend.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;

import java.math.BigDecimal;

// Endpoint de conveniencia para o admin so trocar o preco, sem reenviar
// o produto inteiro (PATCH /api/products/{slug}/price).
public class PriceUpdateRequest {

    @DecimalMin(value = "0.00", message = "O preco nao pode ser negativo")
    @Digits(integer = 12, fraction = 2)
    private BigDecimal price;

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }
}
