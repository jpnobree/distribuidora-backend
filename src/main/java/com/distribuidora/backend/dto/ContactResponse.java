package com.distribuidora.backend.dto;

import com.distribuidora.backend.model.ContactMessage;

import java.time.Instant;

public class ContactResponse {

    private Long id;
    private String requesterUsername;
    private String productSlug;
    private String message;
    private String phone;
    private Instant createdAt;
    private boolean answered;

    public static ContactResponse from(ContactMessage entity) {
        ContactResponse response = new ContactResponse();
        response.id = entity.getId();
        response.requesterUsername = entity.getRequester().getUsername();
        response.productSlug = entity.getProductSlug();
        response.message = entity.getMessage();
        response.phone = entity.getPhone();
        response.createdAt = entity.getCreatedAt();
        response.answered = entity.isAnswered();
        return response;
    }

    public Long getId() {
        return id;
    }

    public String getRequesterUsername() {
        return requesterUsername;
    }

    public String getProductSlug() {
        return productSlug;
    }

    public String getMessage() {
        return message;
    }

    public String getPhone() {
        return phone;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public boolean isAnswered() {
        return answered;
    }
}
