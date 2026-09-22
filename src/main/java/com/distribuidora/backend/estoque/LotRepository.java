package com.distribuidora.backend.estoque;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LotRepository extends JpaRepository<Lot, Long> {
    Optional<Lot> findByProductIdAndCode(Long productId, String code);
}
