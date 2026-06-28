package com.arktech.superaccountant.masters.repository;

import com.arktech.superaccountant.masters.models.FindingSeverity;
import com.arktech.superaccountant.masters.models.LedgerCategory;
import com.arktech.superaccountant.masters.models.ResolveStatus;
import com.arktech.superaccountant.masters.models.ValidationFinding;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Repository
public interface ValidationFindingRepository extends JpaRepository<ValidationFinding, UUID> {
    List<ValidationFinding> findByUploadJobId(UUID uploadJobId);

    @Query("SELECT f FROM ValidationFinding f WHERE f.uploadJobId = :jobId " +
           "AND (:category IS NULL OR f.category = :category) " +
           "AND (:severity IS NULL OR f.severity = :severity) " +
           "AND (:showResolved = true OR (f.resolveStatus != com.arktech.superaccountant.masters.models.ResolveStatus.APPROVED " +
           "AND f.resolveStatus != com.arktech.superaccountant.masters.models.ResolveStatus.RESOLVED " +
           "AND f.resolveStatus != com.arktech.superaccountant.masters.models.ResolveStatus.DISCARDED))")
    Page<ValidationFinding> findFiltered(
            @Param("jobId") UUID jobId,
            @Param("category") LedgerCategory category,
            @Param("severity") FindingSeverity severity,
            @Param("showResolved") boolean showResolved,
            Pageable pageable);

    @Query("SELECT f FROM ValidationFinding f WHERE f.uploadJobId = :jobId " +
           "AND LOWER(f.ledgerName) = LOWER(:ledgerName) " +
           "AND (f.resolveStatus = com.arktech.superaccountant.masters.models.ResolveStatus.OPEN " +
           "OR f.resolveStatus = com.arktech.superaccountant.masters.models.ResolveStatus.ACKNOWLEDGED)")
    List<ValidationFinding> findOpenByUploadJobIdAndLedgerName(
            @Param("jobId") UUID jobId,
            @Param("ledgerName") String ledgerName);

    /**
     * Counts HIGH-severity findings for a given upload job that are still unresolved
     * (status OPEN or ACKNOWLEDGED). Used by {@code MastersGateService} to determine
     * whether a compliance endpoint should be gated.
     *
     * @param jobId the upload job to scope the count to
     * @return count of HIGH + (OPEN | ACKNOWLEDGED) findings for the job
     */
    @Query("SELECT COUNT(f) FROM ValidationFinding f WHERE f.uploadJobId = :jobId " +
           "AND f.severity = com.arktech.superaccountant.masters.models.FindingSeverity.HIGH " +
           "AND (f.resolveStatus = com.arktech.superaccountant.masters.models.ResolveStatus.OPEN " +
           "OR f.resolveStatus = com.arktech.superaccountant.masters.models.ResolveStatus.ACKNOWLEDGED)")
    long countHighSeverityUnresolved(@Param("jobId") UUID jobId);

    @Query("SELECT COUNT(f) FROM ValidationFinding f WHERE f.uploadJobId = :jobId " +
           "AND f.resolveStatus IN " +
           "(com.arktech.superaccountant.masters.models.ResolveStatus.OPEN, " +
           "com.arktech.superaccountant.masters.models.ResolveStatus.ACKNOWLEDGED)")
    long countUnresolvedByUploadJobId(@Param("jobId") UUID jobId);

    /**
     * Counts ALL unresolved findings for a given upload job (regardless of severity).
     * Used by {@code MastersGateService} to determine whether a compliance endpoint should be gated.
     * A finding is considered unresolved if its resolveStatus is not APPROVED, RESOLVED, or DISCARDED.
     *
     * @param jobId the upload job to scope the count to
     * @return count of findings where resolveStatus NOT IN (APPROVED, RESOLVED, DISCARDED)
     */
    @Query("SELECT COUNT(f) FROM ValidationFinding f WHERE f.uploadJobId = :jobId " +
           "AND f.resolveStatus NOT IN " +
           "(com.arktech.superaccountant.masters.models.ResolveStatus.APPROVED, " +
           "com.arktech.superaccountant.masters.models.ResolveStatus.RESOLVED, " +
           "com.arktech.superaccountant.masters.models.ResolveStatus.DISCARDED)")
    long countUnresolvedForGate(@Param("jobId") UUID jobId);

    @Modifying
    @Transactional
    @Query("UPDATE ValidationFinding f SET f.resolveStatus = " +
           "com.arktech.superaccountant.masters.models.ResolveStatus.DISCARDED " +
           "WHERE f.uploadJobId = :jobId " +
           "AND f.resolveStatus IN " +
           "(com.arktech.superaccountant.masters.models.ResolveStatus.OPEN, " +
           "com.arktech.superaccountant.masters.models.ResolveStatus.ACKNOWLEDGED)")
    void discardOpenFindingsByUploadJobId(@Param("jobId") UUID jobId);
}
