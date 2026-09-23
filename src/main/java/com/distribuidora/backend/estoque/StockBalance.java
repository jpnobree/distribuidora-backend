package com.distribuidora.backend.estoque;

import com.distribuidora.backend.estoque.StockEnums.Bucket;
import com.distribuidora.backend.exception.BusinessRuleException;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "stock_balances")
public class StockBalance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long warehouseId;

    @Column(nullable = false)
    private Long productId;

    private Long lotId;

    @Column(nullable = false)
    private BigDecimal qtyPhysical = BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal qtyReserved = BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal qtyBlocked = BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal qtyDamaged = BigDecimal.ZERO;

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    @Version
    private long version;

    protected StockBalance() {
    }

    public StockBalance(Long warehouseId, Long productId, Long lotId) {
        this.warehouseId = warehouseId;
        this.productId = productId;
        this.lotId = lotId;
    }

    public BigDecimal available() {
        return qtyPhysical.subtract(qtyReserved).subtract(qtyBlocked).subtract(qtyDamaged);
    }

    public BigDecimal quantityIn(Bucket bucket) {
        return switch (bucket) {
            case DISPONIVEL -> available();
            case BLOQUEADO -> qtyBlocked;
            case AVARIADO -> qtyDamaged;
            case EXTERNO -> throw new IllegalArgumentException("EXTERNO nao tem saldo");
        };
    }

    // Aplica "quantity sai de from e vai para to". Recusa deixar qualquer
    // situacao negativa: estoque negativo nao existe neste sistema.
    public void move(Bucket from, Bucket to, BigDecimal quantity) {
        if (from != Bucket.EXTERNO && quantityIn(from).compareTo(quantity) < 0) {
            throw new BusinessRuleException("Saldo insuficiente: " + describe(from) + " "
                    + quantityIn(from).stripTrailingZeros().toPlainString() + ", solicitado "
                    + quantity.stripTrailingZeros().toPlainString() + ".");
        }
        adjust(from, quantity.negate());
        adjust(to, quantity);
        updatedAt = Instant.now();
    }

    // Reserva nao e movimento fisico: a mercadoria continua no deposito, so
    // deixa de estar disponivel para outro pedido.
    public void reserve(BigDecimal quantity) {
        if (available().compareTo(quantity) < 0) {
            throw new BusinessRuleException("Saldo disponivel insuficiente para reservar.");
        }
        qtyReserved = qtyReserved.add(quantity);
        updatedAt = Instant.now();
    }

    public void release(BigDecimal quantity) {
        qtyReserved = qtyReserved.subtract(quantity).max(BigDecimal.ZERO);
        updatedAt = Instant.now();
    }

    // delta = quanto a situacao ganha. EXTERNO e o "lado de fora": quando ele
    // ganha, o fisico da empresa perde (e vice-versa). DISPONIVEL e derivado.
    private void adjust(Bucket bucket, BigDecimal delta) {
        switch (bucket) {
            case EXTERNO -> qtyPhysical = qtyPhysical.subtract(delta);
            case DISPONIVEL -> { }
            case BLOQUEADO -> qtyBlocked = qtyBlocked.add(delta);
            case AVARIADO -> qtyDamaged = qtyDamaged.add(delta);
        }
    }

    private static String describe(Bucket bucket) {
        return switch (bucket) {
            case DISPONIVEL -> "disponivel";
            case BLOQUEADO -> "bloqueado";
            case AVARIADO -> "avariado";
            case EXTERNO -> "externo";
        };
    }

    public Long getId() {
        return id;
    }

    public Long getWarehouseId() {
        return warehouseId;
    }

    public Long getProductId() {
        return productId;
    }

    public Long getLotId() {
        return lotId;
    }

    public BigDecimal getQtyPhysical() {
        return qtyPhysical;
    }

    public BigDecimal getQtyReserved() {
        return qtyReserved;
    }

    public BigDecimal getQtyBlocked() {
        return qtyBlocked;
    }

    public BigDecimal getQtyDamaged() {
        return qtyDamaged;
    }
}
