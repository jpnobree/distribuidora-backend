package com.distribuidora.backend.faturamento;

import com.distribuidora.backend.financeiro.FinanceDtos.ReceivableSummary;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class InvoiceDtos {

    private InvoiceDtos() {
    }

    public record ReasonRequest(@NotBlank(message = "Informe o motivo") @Size(max = 500) String reason) {
    }

    public record InvoiceItemView(Long id, Long productId, String description, String baseUnit, BigDecimal quantity,
                                  BigDecimal qtyOrdered, BigDecimal unitPrice, BigDecimal discountPercent,
                                  BigDecimal lineTotal, BigDecimal unitCost, BigDecimal lineCost) {
    }

    public record InvoiceView(Long id, Long number, String series, String display, Long orderId, Long pickingId,
                              Long customerId, String customerName, String customerDocument, Invoice.Status status,
                              String statusLabel, String fiscalStatus, String fiscalStatusLabel, String fiscalNumber,
                              String fiscalKey, String fiscalMessage, Instant issuedAt, String issuedBy,
                              String paymentTermName, BigDecimal subtotal, BigDecimal discountTotal, BigDecimal total,
                              // null sem "produtos.custo.ver"
                              BigDecimal costTotal, BigDecimal marginPercent,
                              List<InvoiceItemView> items, List<ReceivableSummary> receivables, String cancelledBy,
                              Instant cancelledAt, String cancelReason, boolean canCancel, long version) {
    }

    public record InvoiceSummary(Long id, Long number, String series, String display, Long orderId, Long customerId,
                                 String customerName, Invoice.Status status, String statusLabel, Instant issuedAt,
                                 String issuedBy, BigDecimal total, int itemCount) {
    }

    // Faturamento do dia e do mes com margem: o numero que o dono olha.
    public record InvoiceTotals(BigDecimal today, BigDecimal month, BigDecimal monthCost, BigDecimal monthMargin,
                                BigDecimal monthMarginPercent, long monthCount, BigDecimal averageTicket) {
    }
}
