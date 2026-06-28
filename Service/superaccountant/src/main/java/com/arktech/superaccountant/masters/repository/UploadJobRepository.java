package com.arktech.superaccountant.masters.repository;

import com.arktech.superaccountant.masters.models.UploadJob;
import com.arktech.superaccountant.masters.models.UploadJobStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.UUID;

@Repository
public interface UploadJobRepository extends JpaRepository<UploadJob, UUID> {
    Page<UploadJob> findByOrganizationId(UUID organizationId, Pageable pageable);
    Page<UploadJob> findByOrganizationIdAndStatus(UUID organizationId, UploadJobStatus status, Pageable pageable);
    java.util.Optional<UploadJob> findTopByOrganizationIdAndStatusOrderByCreatedAtDesc(UUID organizationId, UploadJobStatus status);
    java.util.Optional<UploadJob> findTopByOrganizationIdAndStatusInOrderByCreatedAtDesc(UUID organizationId, Collection<UploadJobStatus> statuses);
    java.util.Optional<UploadJob> findTopByOrganizationIdAndStatusNotInOrderByCreatedAtDesc(UUID organizationId, Collection<UploadJobStatus> statuses);
}
