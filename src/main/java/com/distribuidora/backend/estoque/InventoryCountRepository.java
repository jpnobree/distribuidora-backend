package com.distribuidora.backend.estoque;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InventoryCountRepository extends JpaRepository<InventoryCount, Long> {
    boolean existsByWarehouseIdAndStatus(Long warehouseId, InventoryCount.Status status);

    List<InventoryCount> findTop50ByOrderByCreatedAtDesc();
}
