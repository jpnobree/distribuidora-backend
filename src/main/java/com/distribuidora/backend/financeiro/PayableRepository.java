package com.distribuidora.backend.financeiro;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface PayableRepository extends JpaRepository<Payable, Long>, JpaSpecificationExecutor<Payable> {

    List<Payable> findByReceiptIdOrderByInstallment(Long receiptId);

    @Query("""
            select coalesce(sum(p.amount - p.paidAmount), 0) from Payable p
            where p.supplierId = :supplierId
              and p.status in (com.distribuidora.backend.financeiro.Payable.Status.ABERTO,
                               com.distribuidora.backend.financeiro.Payable.Status.PARCIAL)
            """)
    BigDecimal openTotalForSupplier(@Param("supplierId") Long supplierId);
}
