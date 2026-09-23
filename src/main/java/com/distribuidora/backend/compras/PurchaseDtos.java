package com.distribuidora.backend.compras;

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

public final class PurchaseDtos {

    private PurchaseDtos() {
    }

    // ----------------------------------------------------------- requisicoes

    public record PurchaseItemRequest(
            @NotNull Long productId,
            @NotBlank String unitCode,
            @NotNull @DecimalMin("0.001") @Digits(integer = 11, fraction = 3) BigDecimal quantity,
            // custo por unidade base; compra sem preco viraria titulo de zero
            @NotNull @DecimalMin(value = "0.0001", message = "Informe o custo de compra")
            @Digits(integer = 10, fraction = 4) BigDecimal unitCost) {
    }

    public record PurchaseOrderRequest(
            @NotNull Long supplierId,
            @NotNull Long warehouseId,
            LocalDate expectedOn,
            @Size(max = 1000) String notes,
            @Valid @NotEmpty(message = "Inclua ao menos um item") @Size(max = 200) List<PurchaseItemRequest> items) {
    }

    public record ReasonRequest(@NotBlank(message = "Informe o motivo") @Size(max = 500) String reason) {
    }

    public record ReceiptItemRequest(
            @NotNull Long purchaseOrderItemId,
            // peso/quantidade que chegou, na unidade base
            @NotNull @DecimalMin("0") @Digits(integer = 11, fraction = 3) BigDecimal qtyBase,
            @NotNull @DecimalMin(value = "0.0001", message = "Informe o custo que veio na nota")
            @Digits(integer = 10, fraction = 4) BigDecimal unitCost,
            @Size(max = 40) String lotCode,
            LocalDate manufacturedOn,
            LocalDate expiresOn) {
    }

    public record ReceiptRequest(
            @NotNull Long purchaseOrderId,
            @Size(max = 60) String document,
            @DecimalMin("0") @Digits(integer = 12, fraction = 2) BigDecimal documentTotal,
            @Size(max = 1000) String notes,
            @Valid @NotEmpty(message = "Informe o que chegou") List<ReceiptItemRequest> items) {
    }

    // ----------------------------------------------------------------- views

    public record PurchaseItemView(
            Long id, Long productId, String productName, String sku, String baseUnit, String unitCode,
            BigDecimal quantity, BigDecimal factor, BigDecimal qtyBase, BigDecimal unitCost, BigDecimal lineTotal,
            BigDecimal qtyReceived, BigDecimal pending, boolean lotControl, boolean expiryControl,
            Integer shelfLifeDays) {
    }

    public record ReceiptItemView(
            Long id, Long productId, String productName, String baseUnit, String lotCode, LocalDate expiresOn,
            BigDecimal qtyBase, BigDecimal unitCost, BigDecimal lineTotal, BigDecimal costDifference) {
    }

    public record ReceiptView(
            Long id, Long purchaseOrderId, Long supplierId, String supplierName, Long warehouseId,
            String warehouseName, String document, BigDecimal documentTotal, BigDecimal total, String notes,
            String receivedBy, Instant receivedAt, List<ReceiptItemView> items, List<String> payables) {
    }

    public record PurchaseOrderView(
            Long id, PurchaseOrder.Status status, String statusLabel, Long supplierId, String supplierName,
            String supplierDocument, Long warehouseId, String warehouseName, String paymentTermName,
            LocalDate expectedOn, String notes, BigDecimal total, String createdBy, Instant createdAt,
            String approvedBy, Instant approvedAt, String cancelledBy, Instant cancelledAt, String cancelReason,
            List<PurchaseItemView> items, List<ReceiptView> receipts, boolean canApprove, boolean canCancel,
            boolean canReceive, BigDecimal approvalThreshold, long version) {
    }

    public record PurchaseOrderSummary(
            Long id, PurchaseOrder.Status status, String statusLabel, Long supplierId, String supplierName,
            LocalDate expectedOn, BigDecimal total, int itemCount, String createdBy, Instant createdAt) {
    }

    // Sugestao calculada na hora: nada e gravado ate virar pedido.
    public record SuggestionRow(
            Long productId, String sku, String name, String baseUnit, BigDecimal available, BigDecimal incoming,
            BigDecimal minStock, BigDecimal maxStock, BigDecimal dailySales, BigDecimal coverageDays,
            Integer leadTimeDays, BigDecimal suggestedQty, String reason, Long supplierId, String supplierName,
            BigDecimal lastCost, BigDecimal averageCost) {
    }

    public record SuggestionResponse(int horizonDays, int salesWindowDays, List<SuggestionRow> rows) {
    }
}
