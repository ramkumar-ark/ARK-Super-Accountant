package com.arktech.superaccountant.tds.repository;

import com.arktech.superaccountant.masters.models.ResolveStatus;
import com.arktech.superaccountant.tds.models.TdsFinding;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TdsFindingRepository extends JpaRepository<TdsFinding, UUID> {
    List<TdsFinding> findByWorkflowIdOrderByVoucherDateAsc(UUID workflowId);

    long countByWorkflowIdAndResolveStatusNotIn(UUID workflowId, Collection<ResolveStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<TdsFinding> findByIdForUpdate(UUID id);
}
