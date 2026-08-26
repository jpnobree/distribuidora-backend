package com.distribuidora.backend.repository;

import com.distribuidora.backend.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {
    Optional<Product> findBySlug(String slug);

    boolean existsBySlug(String slug);
}
