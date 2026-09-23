package com.distribuidora.backend.comercial;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Entity
@Table(name = "price_tables")
public class PriceTable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    // produto -> preco por unidade base
    @ElementCollection
    @CollectionTable(name = "price_table_items", joinColumns = @JoinColumn(name = "price_table_id"))
    @MapKeyColumn(name = "product_id")
    @Column(name = "price", nullable = false)
    private Map<Long, BigDecimal> prices = new HashMap<>();

    protected PriceTable() {
    }

    public PriceTable(String name) {
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Map<Long, BigDecimal> getPrices() {
        return prices;
    }
}
