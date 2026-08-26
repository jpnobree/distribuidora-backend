package com.distribuidora.backend.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

// Usado para criar ou atualizar um produto (POST/PUT /api/products).
public class ProductRequest {

    @NotBlank
    private String slug;

    @NotBlank
    private String sku;

    @NotBlank
    private String name;

    @NotBlank
    private String category;

    @NotBlank
    private String unit;

    // null = "consulte o preco"
    private Double price;

    private List<String> tags;

    private String image;

    private String description;

    private String origin;

    private boolean available = true;

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public Double getPrice() {
        return price;
    }

    public void setPrice(Double price) {
        this.price = price;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getOrigin() {
        return origin;
    }

    public void setOrigin(String origin) {
        this.origin = origin;
    }

    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }
}
