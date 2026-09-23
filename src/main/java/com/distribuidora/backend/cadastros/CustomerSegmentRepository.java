package com.distribuidora.backend.cadastros;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerSegmentRepository extends JpaRepository<CustomerSegment, Long> {
    boolean existsByNameIgnoreCase(String name);

    java.util.List<CustomerSegment> findAllByOrderByNameAsc();
}
