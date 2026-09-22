package com.distribuidora.backend.expedicao;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

// Diferenca entre o que foi pedido, o que foi separado e o que foi conferido.
// Fica registrada com autor e horario: e o que permite cobrar explicacao.
@Entity
@Table(name = "picking_divergences")
public class PickingDivergence {

    public enum Type {
        FALTA("Falta"),
        SOBRA("Sobra"),
        PESO_FORA_TOLERANCIA("Peso fora da tolerância"),
        TROCA_LOTE("Troca de lote"),
        CONFERENCIA("Divergência na conferência");

        private final String label;

        Type(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "picking_id")
    private PickingList picking;

    @Column(nullable = false)
    private Long orderItemId;

    @Column(nullable = false)
    private Long productId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Type type;

    @Column(nullable = false)
    private BigDecimal qtyExpected;

    @Column(nullable = false)
    private BigDecimal qtyFound;

    private String detail;

    @Column(nullable = false)
    private String createdBy;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected PickingDivergence() {
    }

    PickingDivergence(Long orderItemId, Long productId, Type type, BigDecimal qtyExpected, BigDecimal qtyFound,
                      String detail, String createdBy) {
        this.orderItemId = orderItemId;
        this.productId = productId;
        this.type = type;
        this.qtyExpected = qtyExpected;
        this.qtyFound = qtyFound;
        this.detail = detail;
        this.createdBy = createdBy;
    }

    void attach(PickingList picking) {
        this.picking = picking;
    }

    public Long getId() {
        return id;
    }

    public Long getOrderItemId() {
        return orderItemId;
    }

    public Long getProductId() {
        return productId;
    }

    public Type getType() {
        return type;
    }

    public BigDecimal getQtyExpected() {
        return qtyExpected;
    }

    public BigDecimal getQtyFound() {
        return qtyFound;
    }

    public String getDetail() {
        return detail;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
