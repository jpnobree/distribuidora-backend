package com.distribuidora.backend.dto;

import jakarta.validation.constraints.NotBlank;

public class ContactRequest {

    // slug do produto sobre o qual o cliente quer falar (opcional)
    private String productSlug;

    @NotBlank
    private String message;

    private String phone;

    public String getProductSlug() {
        return productSlug;
    }

    public void setProductSlug(String productSlug) {
        this.productSlug = productSlug;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }
}
