package com.arktech.superaccountant.tds.repository;

import com.arktech.superaccountant.masters.models.ResolveStatus;
import com.arktech.superaccountant.tds.models.TdsFinding;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TdsFindingRepository extends JpaRepository<TdsFinding, UUID> {
    List<TdsFinding> findByWorkflowIdOrderByVoucherDateAsc(UUID workflowId);

    long countByWorkflowIdAndResolveStatusNotIn(UUID workflowId, Collection<ResolveStatus> statuses);

    List<TdsFinding> findByWorkflowIdAndSupersededAtIsNullOrderByVoucherDateAsc(UUID workflowId);

    @Query("SELECT f FROM TdsFinding f WHERE f.resolvedBy = 'system' AND f.resolveStatus = "
            + "com.arktech.superaccountant.masters.models.ResolveStatus.RESOLVED AND "
            + "((f.workflowId = :workflowId AND f.supersededAt IS NOT NULL) "
            + "OR f.workflowId = :predecessorId) ORDER BY f.voucherDate ASC")
    List<TdsFinding> findAutoResolvedByReupload(@Param("workflowId") UUID workflowId,
                                                @Param("predecessorId") UUID predecessorId);

    long countByWorkflowIdAndSupersededAtIsNullAndResolveStatusNotIn(UUID workflowId,
                                                                    Collection<ResolveStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT f FROM TdsFinding f WHERE f.id = :id")
    Optional<TdsFinding> findByIdForUpdate(@Param("id") UUID id);
}
