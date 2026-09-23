package com.distribuidora.backend.cadastros;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CustomerRepository extends JpaRepository<Customer, Long>, JpaSpecificationExecutor<Customer> {
    boolean existsByDocument(String document);

    boolean existsByDocumentAndIdNot(String document, Long id);
}
