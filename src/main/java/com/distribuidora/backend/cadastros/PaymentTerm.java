package com.distribuidora.backend.cadastros;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;

// Prazo de cada parcela em dias a partir do faturamento. {0} = a vista;
// {28,35,42} = tres parcelas. Usado pelo financeiro para gerar os titulos.
@Entity
@Table(name = "payment_terms")
public class PaymentTerm {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false, columnDefinition = "integer[]")
    private List<Integer> installmentDays = new ArrayList<>();

    @Column(nullable = false)
    private boolean active = true;

    protected PaymentTerm() {
    }

    public PaymentTerm(String name, List<Integer> installmentDays) {
        this.name = name;
        this.installmentDays = new ArrayList<>(installmentDays);
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

    public List<Integer> getInstallmentDays() {
        return installmentDays;
    }

    public void setInstallmentDays(List<Integer> installmentDays) {
        this.installmentDays = new ArrayList<>(installmentDays);
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
