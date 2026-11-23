package com.arktech.superaccountant.daybook.repository;

import com.arktech.superaccountant.daybook.models.DayBookUploadJob;
import com.arktech.superaccountant.masters.models.UploadJobStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DayBookUploadJobRepository extends JpaRepository<DayBookUploadJob, UUID> {

    Page<DayBookUploadJob> findByOrganizationId(UUID organizationId, Pageable pageable);

    Optional<DayBookUploadJob> findTopByOrganizationIdOrderByCreatedAtDesc(UUID organizationId);

    Optional<DayBookUploadJob> findTopByOrganizationIdAndStatusOrderByCreatedAtDesc(
            UUID organizationId, UploadJobStatus status);

    Optional<DayBookUploadJob> findTopByOrganizationIdAndStatusInOrderByCreatedAtDesc(
            UUID organizationId, java.util.Collection<UploadJobStatus> statuses);

    List<DayBookUploadJob> findAllByOrganizationIdAndStatusOrderByCreatedAtDesc(
            UUID organizationId, UploadJobStatus status);

    List<DayBookUploadJob> findAllByOrganizationIdAndStatusInOrderByCreatedAtDesc(
            UUID organizationId, java.util.Collection<UploadJobStatus> statuses);

    @org.springframework.data.jpa.repository.Query("""
            select j from DayBookUploadJob j
            where j.organizationId = :organizationId
              and j.status = :status
              and j.coverageFrom <= :periodEnd
              and j.coverageTo >= :periodStart
            order by j.createdAt desc
            limit 1
            """)
    Optional<DayBookUploadJob> findLatestCovering(
            @org.springframework.data.repository.query.Param("organizationId") UUID organizationId,
            @org.springframework.data.repository.query.Param("status") UploadJobStatus status,
            @org.springframework.data.repository.query.Param("periodStart") java.time.LocalDate periodStart,
            @org.springframework.data.repository.query.Param("periodEnd") java.time.LocalDate periodEnd);

    /**
     * Tenant-scoped lookup: verifies both ID and organization ownership before returning job.
     * Use this method on all read endpoints to prevent cross-tenant data access (T-4-01).
     */
    Optional<DayBookUploadJob> findByIdAndOrganizationId(UUID id, UUID organizationId);

    boolean existsByCreatedForWorkflowId(UUID createdForWorkflowId);
}
