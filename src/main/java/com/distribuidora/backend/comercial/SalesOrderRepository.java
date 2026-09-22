package com.distribuidora.backend.comercial;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;

public interface SalesOrderRepository extends JpaRepository<SalesOrder, Long>, JpaSpecificationExecutor<SalesOrder> {

    // Pedidos ainda nao faturados comprometem o credito do cliente. Depois do
    // faturamento quem compromete e o titulo a receber (senao contaria duas vezes).
    @Query("""
            select coalesce(sum(o.total), 0) from SalesOrder o
            where o.customerId = :customerId
              and o.status not in (com.distribuidora.backend.comercial.SalesOrder.Status.CANCELADO,
                                   com.distribuidora.backend.comercial.SalesOrder.Status.FATURADO)
            """)
    BigDecimal openTotalForCustomer(@Param("customerId") Long customerId);
}
