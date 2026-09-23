package com.distribuidora.backend.cadastros;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface SupplierRepository extends JpaRepository<Supplier, Long>, JpaSpecificationExecutor<Supplier> {
    boolean existsByDocumentAndIdNot(String document, Long id);

    boolean existsByDocument(String document);
}
