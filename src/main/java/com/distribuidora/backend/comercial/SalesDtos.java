package com.distribuidora.backend.comercial;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
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

public final class SalesDtos {

    private SalesDtos() {
    }

    public record OrderItemRequest(
            @NotNull Long productId,
            @NotBlank String unitCode,
            @NotNull @DecimalMin("0.001") @Digits(integer = 11, fraction = 3) BigDecimal quantity,
            // por unidade base; vazio = preco de tabela
            @DecimalMin("0") @Digits(integer = 12, fraction = 2) BigDecimal unitPrice) {
    }

    public record OrderRequest(
            @NotNull Long customerId,
            LocalDate expectedDeliveryOn,
            @Size(max = 1000) String notes,
            @Valid @NotEmpty(message = "Inclua ao menos um item") @Size(max = 200) List<OrderItemRequest> items) {
    }

    public record CancelRequest(@NotBlank(message = "Informe o motivo do cancelamento") @Size(max = 500) String reason) {
    }

    public record ReservationView(String lotCode, LocalDate expiresOn, String warehouseName, BigDecimal quantity) {
    }

    public record OrderItemView(
            Long id, Long productId, String productName, String sku, String baseUnit, String unitCode,
            BigDecimal quantity, BigDecimal factor, boolean nominal, BigDecimal qtyBase, BigDecimal listPrice,
            BigDecimal unitPrice, BigDecimal discountPercent, BigDecimal lineTotal, String priceSource,
            List<ReservationView> reservations) {
    }

    public record BlockView(Long id, String type, String label, String detail, String requiredPermission,
                            String resolvedBy, Instant resolvedAt) {
    }

    public record OrderView(
            Long id, SalesOrder.Status status, Long customerId, String customerName, String customerDocument,
            Long sellerId, String sellerName, String paymentTermName, String priceTableName,
            LocalDate expectedDeliveryOn, String notes, BigDecimal subtotal, BigDecimal discountTotal,
            BigDecimal total,
            // null sem "produtos.custo.ver"
            BigDecimal estimatedCost, BigDecimal marginPercent,
            boolean hasEstimatedWeight, String createdBy, Instant createdAt, String approvedBy, Instant approvedAt,
            String cancelledBy, Instant cancelledAt, String cancelReason, List<OrderItemView> items,
            List<BlockView> blocks, boolean canApprove, boolean canCancel,
            // para onde o pedido andou: separacao e documento de saida
            Long pickingId, Long invoiceId, long version) {
    }

    public record OrderSummary(
            Long id, SalesOrder.Status status, Long customerId, String customerName, String sellerName,
            BigDecimal total, boolean hasEstimatedWeight, int itemCount, List<String> pendingBlocks,
            String createdBy, Instant createdAt) {
    }

    public record UnitOption(String unitCode, BigDecimal factor, boolean nominal) {
    }

    public record Quote(
            Long productId, String name, String sku, String baseUnit, boolean variableWeight,
            List<UnitOption> units, BigDecimal listPrice, String priceSource, BigDecimal available,
            BigDecimal averageCost, BigDecimal maxDiscountPercent) {
    }

    public record CreditView(
            Long customerId, String status, BigDecimal creditLimit, BigDecimal openOrders,
            BigDecimal openReceivables, BigDecimal available, boolean cashTerm, String paymentTermName,
            String priceTableName) {
    }

    public record PriceTableView(Long id, String name, boolean active, int itemCount) {
    }

    public record PriceTableItemView(Long productId, String productName, String sku, String baseUnit,
                                     BigDecimal basePrice, BigDecimal price, BigDecimal averageCost) {
    }

    public record PriceTableRequest(@NotBlank @Size(max = 80) String name, boolean active) {
    }

    public record PriceItemRequest(@NotNull Long productId,
                                   @NotNull @DecimalMin("0") @Digits(integer = 12, fraction = 2) BigDecimal price) {
    }

    public record PriceItemsRequest(@Valid @NotNull List<PriceItemRequest> items, List<Long> removeProductIds) {
    }

    public record DiscountParameterRequest(
            @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal maxDiscountPercent) {
    }
}
