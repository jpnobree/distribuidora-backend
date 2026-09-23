package com.distribuidora.backend.comercial;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PriceTableRepository extends JpaRepository<PriceTable, Long> {
    boolean existsByNameIgnoreCase(String name);

    List<PriceTable> findAllByOrderByNameAsc();
}
