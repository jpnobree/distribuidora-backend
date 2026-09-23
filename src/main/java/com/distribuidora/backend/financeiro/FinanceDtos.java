package com.distribuidora.backend.financeiro;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class FinanceDtos {

    private FinanceDtos() {
    }

    public record ReceiveRequest(
            @NotNull @DecimalMin("0.01") @Digits(integer = 12, fraction = 2) BigDecimal amount,
            @DecimalMin("0") @Digits(integer = 12, fraction = 2) BigDecimal interest,
            @DecimalMin("0") @Digits(integer = 12, fraction = 2) BigDecimal discount,
            @NotNull LocalDate paidOn,
            @NotNull FinancialTransaction.Method method,
            @Size(max = 500) String notes) {
    }

    public record ReasonRequest(@NotBlank(message = "Informe o motivo") @Size(max = 500) String reason) {
    }

    public record TransactionView(Long id, String type, BigDecimal amount, BigDecimal interest, BigDecimal discount,
                                  BigDecimal received, LocalDate paidOn, String method, String methodLabel,
                                  String notes, String username, Instant occurredAt, boolean reversed,
                                  Long reversalOf) {
    }

    public record ReceivableSummary(
            Long id, String document, Long customerId, String customerName, Long invoiceId, Long invoiceNumber,
            int installment, int installmentsTotal, LocalDate issueDate, LocalDate dueDate, BigDecimal amount,
            BigDecimal paidAmount, BigDecimal openAmount, String status, String statusLabel, boolean overdue,
            long daysLate) {
    }

    public record ReceivableView(ReceivableSummary title, String customerDocument, String customerPhone,
                                 Long orderId, List<TransactionView> transactions, boolean canReceive,
                                 boolean canCancel, long version) {
    }

    // Indicadores da carteira de recebiveis: o que ja venceu, o que vence
    // hoje e o que entra nos proximos dias.
    public record ReceivablesTotals(BigDecimal open, BigDecimal overdue, BigDecimal dueToday, BigDecimal dueIn7Days,
                                    BigDecimal dueIn30Days, BigDecimal receivedThisMonth, long overdueCount,
                                    long openCount) {
    }

    // ------------------------------------------------------------- a pagar

    public record PayableSummary(
            Long id, String document, String description, Long supplierId, String supplierName, Long receiptId,
            Long purchaseOrderId, int installment, int installmentsTotal, LocalDate issueDate, LocalDate dueDate,
            BigDecimal amount, BigDecimal paidAmount, BigDecimal openAmount, String status, String statusLabel,
            boolean overdue, long daysLate) {
    }

    public record PayableView(PayableSummary title, String supplierDocument, List<TransactionView> transactions,
                              boolean canPay, boolean canCancel, long version) {
    }

    public record PayablesTotals(BigDecimal open, BigDecimal overdue, BigDecimal dueToday, BigDecimal dueIn7Days,
                                 BigDecimal dueIn30Days, BigDecimal paidThisMonth, long overdueCount,
                                 long openCount) {
    }
}
