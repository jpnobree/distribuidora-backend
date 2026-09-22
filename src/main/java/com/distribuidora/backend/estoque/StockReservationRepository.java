package com.distribuidora.backend.estoque;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface StockReservationRepository extends JpaRepository<StockReservation, Long> {
    List<StockReservation> findByOrderItemIdInAndReleasedAtIsNull(Collection<Long> orderItemIds);
}
