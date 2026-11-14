package com.arktech.superaccountant.gst.repository;

import com.arktech.superaccountant.gst.models.GstDaybookFinding;
import com.arktech.superaccountant.masters.models.FindingSeverity;
import com.arktech.superaccountant.masters.models.ResolveStatus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GstDaybookFindingRepository extends JpaRepository<GstDaybookFinding, UUID> {

    List<GstDaybookFinding> findByWorkflowIdOrderByVoucherDateAscCreatedAtAsc(UUID workflowId);

    List<GstDaybookFinding> findByWorkflowIdAndResolveStatusNotIn(UUID workflowId, Collection<ResolveStatus> statuses);

    long countByWorkflowIdAndSeverityAndResolveStatusNotIn(UUID workflowId, FindingSeverity severity,
                                                          Collection<ResolveStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from GstDaybookFinding f where f.id = :id")
    Optional<GstDaybookFinding> findByIdForUpdate(@Param("id") UUID id);
}
