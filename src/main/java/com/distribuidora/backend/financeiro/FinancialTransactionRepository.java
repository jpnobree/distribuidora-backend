package com.distribuidora.backend.financeiro;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface FinancialTransactionRepository extends JpaRepository<FinancialTransaction, Long> {

    List<FinancialTransaction> findByReceivableIdOrderByIdAsc(Long receivableId);

    List<FinancialTransaction> findByReceivableIdInOrderByIdAsc(Collection<Long> receivableIds);

    boolean existsByReversalOf(Long transactionId);
}
