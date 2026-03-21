package com.arktech.superaccountant.masters.repository;

import com.arktech.superaccountant.masters.models.FindingSeverity;
import com.arktech.superaccountant.masters.models.LedgerCategory;
import com.arktech.superaccountant.masters.models.ResolveStatus;
import com.arktech.superaccountant.masters.models.ValidationFinding;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ValidationFindingRepository extends JpaRepository<ValidationFinding, UUID> {
    List<ValidationFinding> findByUploadJobId(UUID uploadJobId);

    @Query("SELECT f FROM ValidationFinding f WHERE f.uploadJobId = :jobId " +
           "AND (:category IS NULL OR f.category = :category) " +
           "AND (:severity IS NULL OR f.severity = :severity) " +
           "AND (:showResolved = true OR f.resolveStatus <> com.arktech.superaccountant.masters.models.ResolveStatus.RESOLVED)")
    Page<ValidationFinding> findFiltered(
            @Param("jobId") UUID jobId,
            @Param("category") LedgerCategory category,
            @Param("severity") FindingSeverity severity,
            @Param("showResolved") boolean showResolved,
            Pageable pageable);
}
