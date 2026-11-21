package com.arktech.superaccountant.masters.repository;

import com.arktech.superaccountant.masters.models.FindingSeverity;
import com.arktech.superaccountant.masters.models.LedgerCategory;
import com.arktech.superaccountant.masters.models.MismatchType;
import com.arktech.superaccountant.masters.models.ValidationFinding;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ValidationFindingRepository extends JpaRepository<ValidationFinding, UUID> {
    List<ValidationFinding> findByUploadJobId(UUID uploadJobId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT f FROM ValidationFinding f WHERE f.id = :id")
    Optional<ValidationFinding> findByIdForUpdate(@Param("id") UUID id);

    /**
     * Findings of one job. {@code resolvedOnly} lists only closed findings (APPROVED, RESOLVED, DISCARDED);
     * otherwise {@code showResolved} decides whether closed findings are mixed in with the open and pending ones.
     */
    /**
     * A finding belongs to the selected primary Tally group when its uploaded snapshot's group chain ends with
     * that group (so any sub-group below it matches), or, for a ledger missing from the upload (or uploaded
     * without a group), when its configured master's chain or expected parent group is that group. Findings
     * with no Tally group at all only match when no group is selected.
     */
    String GROUP_CLAUSE =
           "AND (:topGroup IS NULL OR ("
           + "EXISTS (SELECT s.id FROM UploadLedgerSnapshot s WHERE s.uploadJobId = f.uploadJobId "
           + "AND LOWER(s.ledgerName) = LOWER(f.ledgerName) "
           + "AND (LOWER(s.groupChain) = :topGroup OR LOWER(s.groupChain) LIKE CONCAT('%|', :topGroup) "
           + "OR ((s.groupChain IS NULL OR s.groupChain = '') AND LOWER(s.parentGroup) = :topGroup))) "
           + "OR (NOT EXISTS (SELECT s2.id FROM UploadLedgerSnapshot s2 WHERE s2.uploadJobId = f.uploadJobId "
           + "AND LOWER(s2.ledgerName) = LOWER(f.ledgerName) "
           + "AND ((s2.groupChain IS NOT NULL AND s2.groupChain <> '') OR s2.parentGroup IS NOT NULL)) "
           + "AND EXISTS (SELECT m.id FROM PreconfiguredMaster m WHERE m.organizationId = :orgId "
           + "AND LOWER(m.ledgerName) = LOWER(f.ledgerName) "
           + "AND (LOWER(m.groupChain) = :topGroup OR LOWER(m.groupChain) LIKE CONCAT('%|', :topGroup) "
           + "OR ((m.groupChain IS NULL OR m.groupChain = '') AND LOWER(m.expectedParentGroup) = :topGroup))))"
           + ")) ";

    @Query("SELECT f FROM ValidationFinding f WHERE f.uploadJobId = :jobId " +
           "AND (:category IS NULL OR f.category = :category) " +
           "AND (:severity IS NULL OR f.severity = :severity) " +
           "AND (:ruleCode IS NULL OR f.ruleCode = :ruleCode) " +
           "AND (:mismatchType IS NULL OR f.mismatchType = :mismatchType) " +
           GROUP_CLAUSE +
           "AND ((:resolvedOnly = true AND f.resolveStatus IN (com.arktech.superaccountant.masters.models.ResolveStatus.APPROVED, com.arktech.superaccountant.masters.models.ResolveStatus.RESOLVED, com.arktech.superaccountant.masters.models.ResolveStatus.DISCARDED)) " +
           "OR (:resolvedOnly = false AND (:showResolved = true " +
           "OR f.resolveStatus NOT IN (com.arktech.superaccountant.masters.models.ResolveStatus.APPROVED, com.arktech.superaccountant.masters.models.ResolveStatus.RESOLVED, com.arktech.superaccountant.masters.models.ResolveStatus.DISCARDED)))) " +
           "ORDER BY f.ledgerName, f.id")
    Page<ValidationFinding> findFiltered(
            @Param("jobId") UUID jobId,
            @Param("orgId") UUID orgId,
            @Param("category") LedgerCategory category,
            @Param("severity") FindingSeverity severity,
            @Param("ruleCode") String ruleCode,
            @Param("mismatchType") MismatchType mismatchType,
            @Param("topGroup") String topGroup,
            @Param("showResolved") boolean showResolved,
            @Param("resolvedOnly") boolean resolvedOnly,
            Pageable pageable);

    /** Every finding in the given status matching the filter, locked for update: the target set of a bulk action. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT f FROM ValidationFinding f WHERE f.uploadJobId = :jobId " +
           "AND f.resolveStatus = :status " +
           "AND (:category IS NULL OR f.category = :category) " +
           "AND (:severity IS NULL OR f.severity = :severity) " +
           "AND (:ruleCode IS NULL OR f.ruleCode = :ruleCode) " +
           "AND (:mismatchType IS NULL OR f.mismatchType = :mismatchType) " +
           GROUP_CLAUSE +
           "ORDER BY f.ledgerName, f.id")
    List<ValidationFinding> findMatchingForUpdate(
            @Param("jobId") UUID jobId,
            @Param("status") com.arktech.superaccountant.masters.models.ResolveStatus status,
            @Param("orgId") UUID orgId,
            @Param("category") LedgerCategory category,
            @Param("severity") FindingSeverity severity,
            @Param("ruleCode") String ruleCode,
            @Param("mismatchType") MismatchType mismatchType,
            @Param("topGroup") String topGroup);

    /** Same targeting as {@link #findMatchingForUpdate} without the lock, for counting. */
    @Query("SELECT f FROM ValidationFinding f WHERE f.uploadJobId = :jobId " +
           "AND f.resolveStatus = :status " +
           "AND (:category IS NULL OR f.category = :category) " +
           "AND (:severity IS NULL OR f.severity = :severity) " +
           "AND (:ruleCode IS NULL OR f.ruleCode = :ruleCode) " +
           "AND (:mismatchType IS NULL OR f.mismatchType = :mismatchType) " +
           GROUP_CLAUSE)
    List<ValidationFinding> findMatching(
            @Param("jobId") UUID jobId,
            @Param("status") com.arktech.superaccountant.masters.models.ResolveStatus status,
            @Param("orgId") UUID orgId,
            @Param("category") LedgerCategory category,
            @Param("severity") FindingSeverity severity,
            @Param("ruleCode") String ruleCode,
            @Param("mismatchType") MismatchType mismatchType,
            @Param("topGroup") String topGroup);

    /** The listed findings of one job, locked for update, whatever their status. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT f FROM ValidationFinding f WHERE f.uploadJobId = :jobId AND f.id IN :ids " +
           "ORDER BY f.ledgerName, f.id")
    List<ValidationFinding> findByIdsForUpdate(@Param("jobId") UUID jobId, @Param("ids") java.util.Collection<UUID> ids);

    @Query("SELECT f FROM ValidationFinding f WHERE f.uploadJobId = :jobId " +
           "AND LOWER(f.ledgerName) = LOWER(:ledgerName) " +
           "AND f.resolveStatus = com.arktech.superaccountant.masters.models.ResolveStatus.OPEN")
    List<ValidationFinding> findOpenByUploadJobIdAndLedgerName(
            @Param("jobId") UUID jobId,
            @Param("ledgerName") String ledgerName);

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

    /** The C2 blocking rule: every non-terminal finding of the job, at any severity. */
    long countByUploadJobIdAndResolveStatusNotIn(java.util.UUID uploadJobId,
            java.util.Collection<com.arktech.superaccountant.masters.models.ResolveStatus> statuses);

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
