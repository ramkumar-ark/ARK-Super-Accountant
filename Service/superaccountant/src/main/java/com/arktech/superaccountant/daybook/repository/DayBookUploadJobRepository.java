package com.arktech.superaccountant.daybook.repository;

import com.arktech.superaccountant.daybook.models.DayBookUploadJob;
import com.arktech.superaccountant.masters.models.UploadJobStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DayBookUploadJobRepository extends JpaRepository<DayBookUploadJob, UUID> {

    Page<DayBookUploadJob> findByOrganizationId(UUID organizationId, Pageable pageable);

    Optional<DayBookUploadJob> findTopByOrganizationIdOrderByCreatedAtDesc(UUID organizationId);

    /**
     * Tenant-scoped lookup: verifies both ID and organization ownership before returning job.
     * Use this method on all read endpoints to prevent cross-tenant data access (T-4-01).
     */
    Optional<DayBookUploadJob> findByIdAndOrganizationId(UUID id, UUID organizationId);
}
