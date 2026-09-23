package com.distribuidora.backend.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Identificador legivel usado pelo front-end (ex: "picanha-premium-98562")
    @Column(nullable = false, unique = true)
    private String slug;

    @Column(nullable = false)
    private String sku;

    @Column(nullable = false)
    private String name;

    // Slug da categoria (ver Category.slug)
    @Column(nullable = false)
    private String category;

    @Column(nullable = false)
    private String unit;

    // null = "consulte o preco"
    @Column
    private BigDecimal price;

    @ElementCollection
    @CollectionTable(name = "product_tags", joinColumns = @JoinColumn(name = "product_id"))
    @Column(name = "tag")
    private List<String> tags = new ArrayList<>();

    @Column
    private String image;

    @Column(length = 2000)
    private String description;

    @Column
    private String origin;

    @Column(nullable = false)
    private boolean available = true;

    // --- Campos operacionais do ERP (V4) ---

    @Column
    private String barcode;

    @Column(name = "brand_id")
    private Long brandId;

    @Column(name = "subcategory_id")
    private Long subcategoryId;

    // Quantidades de estoque e pedido sao sempre guardadas nesta unidade.
    @Column(name = "base_unit", nullable = false)
    private String baseUnit;

    @Column(nullable = false)
    private boolean variableWeight;

    @Column(name = "gross_weight_kg")
    private BigDecimal grossWeightKg;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StorageType storageType = StorageType.SECO;

    @Column(nullable = false)
    private boolean lotControl;

    @Column(nullable = false)
    private boolean expiryControl;

    @Column
    private Integer shelfLifeDays;

    @Column
    private BigDecimal costPrice;

    // Recalculado pelas entradas de compra (fase 5); no cadastro nasce igual ao custo.
    @Column
    private BigDecimal averageCost;

    @Column
    private BigDecimal minStock;

    @Column
    private BigDecimal maxStock;

    @Column
    private Integer leadTimeDays;

    @Column(name = "main_supplier_id")
    private Long mainSupplierId;

    // Inativo: nao aparece na vitrine e nao pode ser vendido.
    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    @Version
    private long version;

    @ElementCollection
    @CollectionTable(name = "product_units", joinColumns = @JoinColumn(name = "product_id"))
    private Set<ProductUnit> units = new HashSet<>();

    @ElementCollection
    @CollectionTable(name = "product_suppliers", joinColumns = @JoinColumn(name = "product_id"))
    private Set<ProductSupplier> suppliers = new HashSet<>();

    public enum StorageType { SECO, REFRIGERADO, CONGELADO }

    public Product() {
    }

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    // Unidade base a partir do texto livre usado pela vitrine ("kg", "cx"...).
    public static String baseUnitFromLabel(String unitLabel) {
        if (unitLabel == null) {
            return "UN";
        }
        return switch (unitLabel.trim().toLowerCase()) {
            case "kg" -> "KG";
            case "cx" -> "CX";
            case "pct" -> "PCT";
            default -> "UN";
        };
    }

    public String getBarcode() {
        return barcode;
    }

    public void setBarcode(String barcode) {
        this.barcode = barcode;
    }

    public Long getBrandId() {
        return brandId;
    }

    public void setBrandId(Long brandId) {
        this.brandId = brandId;
    }

    public Long getSubcategoryId() {
        return subcategoryId;
    }

    public void setSubcategoryId(Long subcategoryId) {
        this.subcategoryId = subcategoryId;
    }

    public String getBaseUnit() {
        return baseUnit;
    }

    public void setBaseUnit(String baseUnit) {
        this.baseUnit = baseUnit;
    }

    public boolean isVariableWeight() {
        return variableWeight;
    }

    public void setVariableWeight(boolean variableWeight) {
        this.variableWeight = variableWeight;
    }

    public BigDecimal getGrossWeightKg() {
        return grossWeightKg;
    }

    public void setGrossWeightKg(BigDecimal grossWeightKg) {
        this.grossWeightKg = grossWeightKg;
    }

    public StorageType getStorageType() {
        return storageType;
    }

    public void setStorageType(StorageType storageType) {
        this.storageType = storageType;
    }

    public boolean isLotControl() {
        return lotControl;
    }

    public void setLotControl(boolean lotControl) {
        this.lotControl = lotControl;
    }

    public boolean isExpiryControl() {
        return expiryControl;
    }

    public void setExpiryControl(boolean expiryControl) {
        this.expiryControl = expiryControl;
    }

    public Integer getShelfLifeDays() {
        return shelfLifeDays;
    }

    public void setShelfLifeDays(Integer shelfLifeDays) {
        this.shelfLifeDays = shelfLifeDays;
    }

    public BigDecimal getCostPrice() {
        return costPrice;
    }

    public void setCostPrice(BigDecimal costPrice) {
        this.costPrice = costPrice;
    }

    public BigDecimal getAverageCost() {
        return averageCost;
    }

    public void setAverageCost(BigDecimal averageCost) {
        this.averageCost = averageCost;
    }

    public BigDecimal getMinStock() {
        return minStock;
    }

    public void setMinStock(BigDecimal minStock) {
        this.minStock = minStock;
    }

    public BigDecimal getMaxStock() {
        return maxStock;
    }

    public void setMaxStock(BigDecimal maxStock) {
        this.maxStock = maxStock;
    }

    public Integer getLeadTimeDays() {
        return leadTimeDays;
    }

    public void setLeadTimeDays(Integer leadTimeDays) {
        this.leadTimeDays = leadTimeDays;
    }

    public Long getMainSupplierId() {
        return mainSupplierId;
    }

    public void setMainSupplierId(Long mainSupplierId) {
        this.mainSupplierId = mainSupplierId;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }

    public Set<ProductUnit> getUnits() {
        return units;
    }

    public Set<ProductSupplier> getSuppliers() {
        return suppliers;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
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
