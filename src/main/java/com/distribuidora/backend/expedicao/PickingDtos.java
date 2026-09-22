package com.distribuidora.backend.expedicao;

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

public final class PickingDtos {

    private PickingDtos() {
    }

    // ----------------------------------------------------------- requisicoes

    public record PickLine(
            @NotNull Long warehouseId,
            Long lotId,
            @NotNull @DecimalMin("0") @Digits(integer = 11, fraction = 3) BigDecimal quantity) {
    }

    public record PickItemRequest(@NotNull Long orderItemId,
                                  @Valid @NotNull List<PickLine> lines,
                                  @Size(max = 500) String note) {
    }

    public record SeparationRequest(@Valid @NotEmpty(message = "Informe o que foi separado") List<PickItemRequest> items,
                                    @Size(max = 500) String notes) {
    }

    public record CheckLine(@NotNull Long pickingItemId,
                            @NotNull @DecimalMin("0") @Digits(integer = 11, fraction = 3) BigDecimal quantity) {
    }

    public record CheckRequest(@Valid @NotEmpty(message = "Confira as linhas da separação") List<CheckLine> lines,
                               @Size(max = 500) String notes) {
    }

    public record ReasonRequest(@NotBlank(message = "Informe o motivo") @Size(max = 500) String reason) {
    }

    // ----------------------------------------------------------------- views

    public record LotOption(Long lotId, String lotCode, LocalDate expiresOn, Long warehouseId, String warehouseName,
                            BigDecimal available) {
    }

    public record PickingLineView(Long id, Long lotId, String lotCode, LocalDate expiresOn, Long warehouseId,
                                  String warehouseName, BigDecimal qtyPlanned, BigDecimal qtyPicked,
                                  BigDecimal qtyChecked) {
    }

    public record PickingItemView(Long orderItemId, Long productId, String productName, String sku, String baseUnit,
                                  boolean variableWeight, String unitCode, BigDecimal quantity, BigDecimal qtyOrdered,
                                  BigDecimal qtyPicked, BigDecimal qtyChecked, BigDecimal unitPrice,
                                  List<PickingLineView> lines, List<LotOption> lots) {
    }

    public record DivergenceView(Long id, String type, String label, Long productId, String productName,
                                 BigDecimal qtyExpected, BigDecimal qtyFound, String detail, String createdBy,
                                 Instant createdAt) {
    }

    public record PickingView(Long id, Long orderId, PickingList.Status status, String statusLabel, Long customerId,
                              String customerName, LocalDate expectedDeliveryOn, String notes,
                              BigDecimal weightTolerancePercent, boolean checkerMustDiffer, String createdBy,
                              Instant createdAt, String separatedBy, Instant separatedAt, String checkedBy,
                              Instant checkedAt, String cancelledBy, Instant cancelledAt, String cancelReason,
                              BigDecimal orderTotal, BigDecimal pickedTotal, List<PickingItemView> items,
                              List<DivergenceView> divergences, boolean canSeparate, boolean canCheck,
                              boolean canCancel, boolean canInvoice, Long invoiceId, long version) {
    }

    public record PickingSummary(Long id, Long orderId, PickingList.Status status, String statusLabel,
                                 String customerName, LocalDate expectedDeliveryOn, int itemCount,
                                 int divergenceCount, String createdBy, Instant createdAt, String separatedBy,
                                 String checkedBy) {
    }

    // Pedidos aprovados que ainda nao tem separacao: a fila da expedicao.
    public record PendingOrder(Long orderId, String customerName, LocalDate expectedDeliveryOn, int itemCount,
                               BigDecimal total, boolean hasEstimatedWeight, Instant approvedAt) {
    }
}
