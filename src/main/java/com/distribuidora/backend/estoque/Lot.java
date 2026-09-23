package com.distribuidora.backend.estoque;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "lots")
public class Lot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false)
    private String code;

    private Long supplierId;
    private LocalDate manufacturedOn;
    private LocalDate expiresOn;

    @Column(nullable = false)
    private Instant receivedAt = Instant.now();

    private String invoiceNumber;

    protected Lot() {
    }

    public Lot(Long productId, String code, Long supplierId, LocalDate manufacturedOn, LocalDate expiresOn,
               String invoiceNumber) {
        this.productId = productId;
        this.code = code;
        this.supplierId = supplierId;
        this.manufacturedOn = manufacturedOn;
        this.expiresOn = expiresOn;
        this.invoiceNumber = invoiceNumber;
    }

    public boolean isExpired(LocalDate today) {
        return expiresOn != null && expiresOn.isBefore(today);
    }

    public Long getId() {
        return id;
    }

    public Long getProductId() {
        return productId;
    }

    public String getCode() {
        return code;
    }

    public Long getSupplierId() {
        return supplierId;
    }

    public LocalDate getManufacturedOn() {
        return manufacturedOn;
    }

    public LocalDate getExpiresOn() {
        return expiresOn;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public String getInvoiceNumber() {
        return invoiceNumber;
    }
}
