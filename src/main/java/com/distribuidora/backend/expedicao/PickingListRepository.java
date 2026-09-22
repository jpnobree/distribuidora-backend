package com.distribuidora.backend.expedicao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PickingListRepository extends JpaRepository<PickingList, Long>,
        JpaSpecificationExecutor<PickingList> {

    Optional<PickingList> findByOrderIdAndStatusNot(Long orderId, PickingList.Status status);

    List<PickingList> findByOrderIdInAndStatusNot(Collection<Long> orderIds, PickingList.Status status);
}
