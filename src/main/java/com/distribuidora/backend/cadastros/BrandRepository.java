package com.distribuidora.backend.cadastros;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BrandRepository extends JpaRepository<Brand, Long> {
    boolean existsByNameIgnoreCase(String name);

    java.util.List<Brand> findAllByOrderByNameAsc();
}
