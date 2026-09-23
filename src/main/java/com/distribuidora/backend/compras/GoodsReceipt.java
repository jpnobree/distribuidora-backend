package com.distribuidora.backend.compras;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

// A mercadoria chegando: gera a entrada no estoque com lote e validade,
// recalcula o custo medio e abre os titulos a pagar.
//
// Recebimento nao se cancela: se a conferencia errou, a correcao e um
// movimento de estoque (perda, saida ou ajuste) e o cancelamento do titulo,
// tudo auditado. Apagar a entrada apagaria o custo medio que ela formou.
@Entity
@Table(name = "goods_receipts")
public class GoodsReceipt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private Long purchaseOrderId;

    @Column(nullable = false, updatable = false)
    private Long supplierId;

    @Column(nullable = false, updatable = false)
    private Long warehouseId;

    private String document;

    // total da nota do fornecedor, quando informado: confere com o recebido
    private BigDecimal documentTotal;

    private String notes;

    @Column(nullable = false, updatable = false)
    private String receivedBy;

    @Column(nullable = false, updatable = false)
    private Instant receivedAt = Instant.now();

    @Column(nullable = false)
    private BigDecimal total = BigDecimal.ZERO;

    @OneToMany(mappedBy = "receipt", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<GoodsReceiptItem> items = new ArrayList<>();

    protected GoodsReceipt() {
    }

    GoodsReceipt(Long purchaseOrderId, Long supplierId, Long warehouseId, String document, BigDecimal documentTotal,
                 String notes, String receivedBy) {
        this.purchaseOrderId = purchaseOrderId;
        this.supplierId = supplierId;
        this.warehouseId = warehouseId;
        this.document = document;
        this.documentTotal = documentTotal;
        this.notes = notes;
        this.receivedBy = receivedBy;
    }

    void addItem(GoodsReceiptItem item) {
        item.attach(this);
        items.add(item);
    }

    void recalculate() {
        total = items.stream().map(GoodsReceiptItem::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
    }

    public Long getId() {
        return id;
    }

    public Long getPurchaseOrderId() {
        return purchaseOrderId;
    }

    public Long getSupplierId() {
        return supplierId;
    }

    public Long getWarehouseId() {
        return warehouseId;
    }

    public String getDocument() {
        return document;
    }

    public BigDecimal getDocumentTotal() {
        return documentTotal;
    }

    public String getNotes() {
        return notes;
    }

    public String getReceivedBy() {
        return receivedBy;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public List<GoodsReceiptItem> getItems() {
        return items;
    }
}
