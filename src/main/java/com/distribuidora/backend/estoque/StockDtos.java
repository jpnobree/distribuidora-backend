package com.distribuidora.backend.estoque;

import com.distribuidora.backend.estoque.StockEnums.LossReason;
import com.distribuidora.backend.estoque.StockEnums.MovementType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class StockDtos {

    private StockDtos() {
    }

    // Quantidade sempre acompanhada da unidade digitada; o servico converte
    // para a unidade base do produto.
    public record Qty(
            @NotNull @DecimalMin(value = "0.001") @Digits(integer = 11, fraction = 3) BigDecimal quantity,
            @NotBlank String unitCode) {
    }

    public enum EntryKind { IMPLANTACAO, ENTRADA_MANUAL }

    public record EntryRequest(
            @NotNull Long productId,
            @NotNull Long warehouseId,
            @Valid @NotNull Qty qty,
            @NotNull EntryKind kind,
            @Size(max = 40) String lotCode,
            LocalDate manufacturedOn,
            LocalDate expiresOn,
            Long supplierId,
            @DecimalMin("0") @Digits(integer = 10, fraction = 4) BigDecimal unitCost,
            @Size(max = 60) String document,
            @Size(max = 500) String reason) {
    }

    public record ExitRequest(
            @NotNull Long productId,
            @NotNull Long warehouseId,
            Long lotId,
            @Valid @NotNull Qty qty,
            @NotBlank(message = "Informe o motivo da saída") @Size(max = 500) String reason,
            @Size(max = 60) String document) {
    }

    public enum LossKind { PERDA, AVARIA, BAIXA_AVARIADO, BAIXA_BLOQUEADO }

    public record LossRequest(
            @NotNull Long productId,
            @NotNull Long warehouseId,
            Long lotId,
            @Valid @NotNull Qty qty,
            @NotNull LossKind kind,
            @NotNull LossReason lossReason,
            @Size(max = 500) String reason) {
    }

    public record BlockRequest(
            @NotNull Long productId,
            @NotNull Long warehouseId,
            Long lotId,
            @Valid @NotNull Qty qty,
            boolean block,
            @NotBlank(message = "Informe o motivo") @Size(max = 500) String reason) {
    }

    public record TransferRequest(
            @NotNull Long productId,
            @NotNull Long fromWarehouseId,
            @NotNull Long toWarehouseId,
            Long lotId,
            @Valid @NotNull Qty qty,
            @Size(max = 500) String reason) {
    }

    public record MovementResponse(
            Long id, Instant occurredAt, MovementType type, String typeLabel, Long warehouseId, String warehouseName,
            Long productId, String productName, String sku, String baseUnit, Long lotId, String lotCode,
            BigDecimal quantity, BigDecimal unitCost, BigDecimal totalCost, LossReason lossReason,
            String lossReasonLabel, String reason, String document, String username) {
    }

    public record PositionRow(
            Long productId, String sku, String name, String category, String baseUnit, String storageType,
            BigDecimal physical, BigDecimal reserved, BigDecimal blocked, BigDecimal damaged, BigDecimal expired,
            BigDecimal available, BigDecimal minStock, BigDecimal maxStock, String situation,
            // null sem "produtos.custo.ver"
            BigDecimal averageCost, BigDecimal stockValue) {
    }

    public record BalanceDetail(
            Long warehouseId, String warehouseName, Long lotId, String lotCode, LocalDate expiresOn,
            Long daysToExpire, BigDecimal physical, BigDecimal reserved, BigDecimal blocked, BigDecimal damaged,
            BigDecimal available) {
    }

    public record ExpiringLot(
            Long lotId, String lotCode, Long productId, String productName, String sku, String baseUnit,
            LocalDate expiresOn, long daysToExpire, BigDecimal physical, BigDecimal available,
            BigDecimal stockValue, String supplierName) {
    }

    public record Summary(
            BigDecimal stockValue, long productsBelowMin, long productsOutOfStock, long lotsExpiringSoon,
            BigDecimal expiringSoonValue, long lotsExpiredWithStock, BigDecimal expiredValue,
            BigDecimal lossesThisMonthValue, int expiringWindowDays) {
    }

    public record FefoAllocation(Long lotId, String lotCode, LocalDate expiresOn, Long warehouseId,
                                 BigDecimal quantity) {
    }

    public record FefoResponse(BigDecimal requested, BigDecimal allocated, BigDecimal missing,
                               List<FefoAllocation> allocations) {
    }

    public record WarehouseResponse(Long id, String code, String name, boolean active) {
    }

    public record InventoryCreateRequest(@NotNull Long warehouseId, String category, @Size(max = 500) String notes) {
    }

    public record CountEntry(@NotNull Long itemId, @DecimalMin("0") @Digits(integer = 11, fraction = 3) BigDecimal countedQty) {
    }

    public record CountUpdateRequest(@Valid @NotEmpty List<CountEntry> entries) {
    }

    public record InventoryItemResponse(
            Long id, Long productId, String productName, String sku, String baseUnit, Long lotId, String lotCode,
            BigDecimal systemQty, BigDecimal countedQty, BigDecimal difference, BigDecimal differenceValue) {
    }

    public record InventoryResponse(
            Long id, Long warehouseId, String warehouseName, String category, InventoryCount.Status status,
            String notes, String createdBy, Instant createdAt, String closedBy, Instant closedAt,
            int itemCount, int countedCount, List<InventoryItemResponse> items) {
    }
}
