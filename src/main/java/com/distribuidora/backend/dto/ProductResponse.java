package com.distribuidora.backend.dto;

import com.distribuidora.backend.model.Product;

import java.math.BigDecimal;
import java.util.List;

public class ProductResponse {

    private String id;
    private String sku;
    private String name;
    private String category;
    private String unit;
    private BigDecimal price;
    private List<String> tags;
    private String image;
    private String description;
    private String origin;
    private boolean available;
    // disponivel na unidade base (kg, na maioria); so vem no catalogo publico
    private BigDecimal stock;

    public static ProductResponse from(Product product, BigDecimal stock) {
        ProductResponse response = from(product);
        response.stock = stock;
        return response;
    }

    public static ProductResponse from(Product product) {
        ProductResponse response = new ProductResponse();
        response.id = product.getSlug();
        response.sku = product.getSku();
        response.name = product.getName();
        response.category = product.getCategory();
        response.unit = product.getUnit();
        response.price = product.getPrice();
        response.tags = product.getTags();
        response.image = product.getImage();
        response.description = product.getDescription();
        response.origin = product.getOrigin();
        response.available = product.isAvailable();
        return response;
    }

    public String getId() {
        return id;
    }

    public String getSku() {
        return sku;
    }

    public String getName() {
        return name;
    }

    public String getCategory() {
        return category;
    }

    public String getUnit() {
        return unit;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public List<String> getTags() {
        return tags;
    }

    public String getImage() {
        return image;
    }

    public String getDescription() {
        return description;
    }

    public String getOrigin() {
        return origin;
    }

    public boolean isAvailable() {
        return available;
    }

    public BigDecimal getStock() {
        return stock;
    }
}
