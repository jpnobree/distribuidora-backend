package com.distribuidora.backend.compras;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long>,
        JpaSpecificationExecutor<PurchaseOrder> {

    // Quanto de cada produto ja esta comprado e ainda nao chegou: a sugestao
    // nao pode mandar comprar de novo o que esta a caminho.
    @Query("""
            select i.productId, coalesce(sum(i.qtyBase - i.qtyReceived), 0)
            from PurchaseOrderItem i
            where i.order.status in (com.distribuidora.backend.compras.PurchaseOrder.Status.APROVADO,
                                     com.distribuidora.backend.compras.PurchaseOrder.Status.RECEBIDO_PARCIAL,
                                     com.distribuidora.backend.compras.PurchaseOrder.Status.AGUARDANDO_APROVACAO)
            group by i.productId
            """)
    List<Object[]> incomingByProduct();

    @Query("""
            select coalesce(sum(o.total), 0) from PurchaseOrder o
            where o.supplierId = :supplierId and o.status in
                (com.distribuidora.backend.compras.PurchaseOrder.Status.APROVADO,
                 com.distribuidora.backend.compras.PurchaseOrder.Status.RECEBIDO_PARCIAL)
            """)
    BigDecimal openTotalForSupplier(@Param("supplierId") Long supplierId);
}
