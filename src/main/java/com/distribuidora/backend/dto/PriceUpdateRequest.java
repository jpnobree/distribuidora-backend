package com.distribuidora.backend.dto;

// Endpoint de conveniencia para o admin so trocar o preco, sem reenviar
// o produto inteiro (PATCH /api/products/{slug}/price).
public class PriceUpdateRequest {

    private Double price;

    public Double getPrice() {
        return price;
    }

    public void setPrice(Double price) {
        this.price = price;
    }
}
