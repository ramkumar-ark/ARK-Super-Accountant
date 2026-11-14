package com.arktech.superaccountant.tds.repository;

import com.arktech.superaccountant.tds.models.TdsChallan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface TdsChallanRepository extends JpaRepository<TdsChallan, UUID> {
    List<TdsChallan> findByPaymentWorkflowIdOrderByDepositDateAsc(UUID paymentWorkflowId);

    List<TdsChallan> findByPaymentWorkflowIdIn(Collection<UUID> paymentWorkflowIds);
}
