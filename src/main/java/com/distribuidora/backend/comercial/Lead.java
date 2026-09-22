package com.distribuidora.backend.comercial;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

// Pre-cliente: encontrado no mapa ou cadastrado a mao; ao fechar negocio
// vira cliente sem redigitacao (convertedCustomerId).
@Entity
@Table(name = "leads")
public class Lead {

    public enum Source { MAPA, MANUAL }

    public enum Status { NOVO, CONTATADO, QUALIFICADO, CONVERTIDO, DESCARTADO }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private Long segmentId;
    private String phone;
    private String email;
    private String website;
    private String contactName;
    private String street;
    private String number;
    private String district;
    private String city;
    private String state;
    private BigDecimal latitude;
    private BigDecimal longitude;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Source source;

    private String externalId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.NOVO;

    private Long sellerId;

    @Column(length = 2000)
    private String notes;

    private Long convertedCustomerId;

    @Column(nullable = false)
    private String createdBy;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    @Version
    private long version;

    protected Lead() {
    }

    public Lead(Source source, String externalId, String createdBy) {
        this.source = source;
        this.externalId = externalId;
        this.createdBy = createdBy;
    }

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getSegmentId() {
        return segmentId;
    }

    public void setSegmentId(Long segmentId) {
        this.segmentId = segmentId;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getWebsite() {
        return website;
    }

    public void setWebsite(String website) {
        this.website = website;
    }

    public String getContactName() {
        return contactName;
    }

    public void setContactName(String contactName) {
        this.contactName = contactName;
    }

    public String getStreet() {
        return street;
    }

    public void setStreet(String street) {
        this.street = street;
    }

    public String getNumber() {
        return number;
    }

    public void setNumber(String number) {
        this.number = number;
    }

    public String getDistrict() {
        return district;
    }

    public void setDistrict(String district) {
        this.district = district;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public void setLatitude(BigDecimal latitude) {
        this.latitude = latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public void setLongitude(BigDecimal longitude) {
        this.longitude = longitude;
    }

    public Source getSource() {
        return source;
    }

    public String getExternalId() {
        return externalId;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public Long getSellerId() {
        return sellerId;
    }

    public void setSellerId(Long sellerId) {
        this.sellerId = sellerId;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Long getConvertedCustomerId() {
        return convertedCustomerId;
    }

    public void setConvertedCustomerId(Long convertedCustomerId) {
        this.convertedCustomerId = convertedCustomerId;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public long getVersion() {
        return version;
    }
}
