package com.distribuidora.backend.faturamento;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice, Long>, JpaSpecificationExecutor<Invoice> {

    Optional<Invoice> findByOrderIdAndStatus(Long orderId, Invoice.Status status);

    List<Invoice> findByOrderIdOrderByIdDesc(Long orderId);

    @Query(value = "SELECT nextval('invoice_number_seq')", nativeQuery = true)
    Long nextNumber();
}
