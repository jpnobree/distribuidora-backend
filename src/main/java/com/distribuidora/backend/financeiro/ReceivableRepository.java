package com.distribuidora.backend.financeiro;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface ReceivableRepository extends JpaRepository<Receivable, Long>, JpaSpecificationExecutor<Receivable> {

    List<Receivable> findByInvoiceIdOrderByInstallment(Long invoiceId);

    // Saldo devedor do cliente: entra na exposicao de credito junto com os
    // pedidos ainda nao faturados.
    @Query("""
            select coalesce(sum(r.amount - r.paidAmount), 0) from Receivable r
            where r.customerId = :customerId
              and r.status in (com.distribuidora.backend.financeiro.Receivable.Status.ABERTO,
                               com.distribuidora.backend.financeiro.Receivable.Status.PARCIAL)
            """)
    BigDecimal openTotalForCustomer(@Param("customerId") Long customerId);
}
