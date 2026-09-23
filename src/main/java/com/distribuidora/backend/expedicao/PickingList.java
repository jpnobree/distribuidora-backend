package com.distribuidora.backend.expedicao;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

// Separacao de um pedido aprovado. E aqui que o peso real aparece: o pedido
// de 20 kg sai com 20,7 kg, e e esse peso que a nota e o titulo usam.
@Entity
@Table(name = "picking_lists")
public class PickingList {

    public enum Status {
        ABERTA("Em separação"),
        SEPARADA("Separada, aguardando conferência"),
        CONFERIDA("Conferida, pronta para faturar"),
        CANCELADA("Cancelada");

        private final String label;

        Status(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private Long orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.ABERTA;

    private String notes;

    @Column(nullable = false, updatable = false)
    private String createdBy;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    private String separatedBy;
    private Instant separatedAt;
    private String checkedBy;
    private Instant checkedAt;
    private String cancelledBy;
    private Instant cancelledAt;
    private String cancelReason;

    @Version
    private long version;

    @OneToMany(mappedBy = "picking", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<PickingItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "picking", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<PickingDivergence> divergences = new ArrayList<>();

    protected PickingList() {
    }

    PickingList(Long orderId, String createdBy) {
        this.orderId = orderId;
        this.createdBy = createdBy;
    }

    void addItem(PickingItem item) {
        item.attach(this);
        items.add(item);
    }

    void clearItems() {
        items.clear();
    }

    void addDivergence(PickingDivergence divergence) {
        divergence.attach(this);
        divergences.add(divergence);
    }

    void markSeparated(String username) {
        status = Status.SEPARADA;
        separatedBy = username;
        separatedAt = Instant.now();
    }

    // Conferencia com divergencia devolve para o separador refazer.
    void backToPicking() {
        status = Status.ABERTA;
        separatedBy = null;
        separatedAt = null;
    }

    void markChecked(String username) {
        status = Status.CONFERIDA;
        checkedBy = username;
        checkedAt = Instant.now();
    }

    void cancel(String username, String reason) {
        status = Status.CANCELADA;
        cancelledBy = username;
        cancelledAt = Instant.now();
        cancelReason = reason;
    }

    public BigDecimal pickedFor(Long orderItemId) {
        return items.stream().filter(i -> i.getOrderItemId().equals(orderItemId))
                .map(PickingItem::getQtyPicked).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public Long getId() {
        return id;
    }

    public Long getOrderId() {
        return orderId;
    }

    public Status getStatus() {
        return status;
    }

    public String getNotes() {
        return notes;
    }

    void setNotes(String notes) {
        this.notes = notes;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getSeparatedBy() {
        return separatedBy;
    }

    public Instant getSeparatedAt() {
        return separatedAt;
    }

    public String getCheckedBy() {
        return checkedBy;
    }

    public Instant getCheckedAt() {
        return checkedAt;
    }

    public String getCancelledBy() {
        return cancelledBy;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public long getVersion() {
        return version;
    }

    public List<PickingItem> getItems() {
        return items;
    }

    public List<PickingDivergence> getDivergences() {
        return divergences;
    }
}
