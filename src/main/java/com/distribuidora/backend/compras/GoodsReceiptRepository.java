package com.distribuidora.backend.compras;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface GoodsReceiptRepository extends JpaRepository<GoodsReceipt, Long>,
        JpaSpecificationExecutor<GoodsReceipt> {

    List<GoodsReceipt> findByPurchaseOrderIdOrderByIdAsc(Long purchaseOrderId);
}
