package com.distribuidora.backend.cadastros;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentTermRepository extends JpaRepository<PaymentTerm, Long> {
    boolean existsByNameIgnoreCase(String name);

    java.util.List<PaymentTerm> findAllByOrderByNameAsc();
}
