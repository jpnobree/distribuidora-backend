package com.distribuidora.backend.estoque;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StockBalanceRepository extends JpaRepository<StockBalance, Long> {

    // FOR UPDATE: dois movimentos simultaneos no mesmo saldo esperam um pelo
    // outro em vez de ler o mesmo valor e sobrescrever.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from StockBalance b where b.warehouseId = :w and b.productId = :p and b.lotId = :l")
    Optional<StockBalance> lockWithLot(@Param("w") Long warehouseId, @Param("p") Long productId, @Param("l") Long lotId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from StockBalance b where b.warehouseId = :w and b.productId = :p and b.lotId is null")
    Optional<StockBalance> lockWithoutLot(@Param("w") Long warehouseId, @Param("p") Long productId);

    List<StockBalance> findByProductId(Long productId);

    List<StockBalance> findByWarehouseId(Long warehouseId);
}
