package com.distribuidora.backend.cadastros;

import com.distribuidora.backend.model.Product.StorageType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class ProductDtos {

    private ProductDtos() {
    }

    public record UnitConversion(
            @NotBlank String unitCode,
            @NotNull @DecimalMin(value = "0.0001") @Digits(integer = 10, fraction = 4) BigDecimal factor,
            boolean nominal,
            @Pattern(regexp = "\\d{8}|\\d{12,14}", message = "Codigo de barras invalido") String barcode) {
    }

    public record SupplierLink(
            @NotNull Long supplierId,
            @Size(max = 40) String supplierSku,
            @DecimalMin("0") @Digits(integer = 10, fraction = 4) BigDecimal lastCost,
            @Min(0) @Max(365) Integer leadTimeDays) {
    }

    public record ErpProductRequest(
            @NotBlank @Size(max = 40) String sku,
            @Pattern(regexp = "\\d{8}|\\d{12,14}", message = "Codigo de barras invalido") String barcode,
            @NotBlank @Size(max = 255) String name,
            @Size(max = 2000) String description,
            @NotBlank String category,
            Long subcategoryId,
            Long brandId,
            @NotBlank String baseUnit,
            // texto exibido na vitrine; vazio = sigla da unidade base
            @Size(max = 40) String unitLabel,
            boolean variableWeight,
            @DecimalMin("0") @Digits(integer = 7, fraction = 3) BigDecimal grossWeightKg,
            @NotNull StorageType storageType,
            boolean lotControl,
            boolean expiryControl,
            @Min(1) @Max(3650) Integer shelfLifeDays,
            @DecimalMin("0") @Digits(integer = 12, fraction = 2) BigDecimal price,
            @DecimalMin("0") @Digits(integer = 10, fraction = 4) BigDecimal costPrice,
            @DecimalMin("0") @Digits(integer = 11, fraction = 3) BigDecimal minStock,
            @DecimalMin("0") @Digits(integer = 11, fraction = 3) BigDecimal maxStock,
            @Min(0) @Max(365) Integer leadTimeDays,
            Long mainSupplierId,
            @Valid List<UnitConversion> units,
            @Valid List<SupplierLink> suppliers,
            boolean active,
            boolean available,
            Long version) {
    }

    public record ErpProductResponse(
            Long id, String slug, String sku, String barcode, String name, String description,
            String category, Long subcategoryId, Long brandId, String brandName,
            String baseUnit, String unitLabel, boolean variableWeight, BigDecimal grossWeightKg,
            StorageType storageType, boolean lotControl, boolean expiryControl, Integer shelfLifeDays,
            BigDecimal price,
            // null quando o usuario nao tem "produtos.custo.ver"
            BigDecimal costPrice, BigDecimal averageCost, BigDecimal marginPercent,
            BigDecimal minStock, BigDecimal maxStock, Integer leadTimeDays,
            Long mainSupplierId, String mainSupplierName,
            List<UnitConversion> units, List<SupplierLink> suppliers,
            boolean active, boolean available, String image, Instant updatedAt, long version) {
    }
}
