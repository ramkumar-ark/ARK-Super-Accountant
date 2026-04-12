package com.arktech.superaccountant.masters.repository;

import com.arktech.superaccountant.masters.models.UploadJob;
import com.arktech.superaccountant.masters.models.UploadJobStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface UploadJobRepository extends JpaRepository<UploadJob, UUID> {
    Page<UploadJob> findByOrganizationId(UUID organizationId, Pageable pageable);
    Page<UploadJob> findByOrganizationIdAndStatus(UUID organizationId, UploadJobStatus status, Pageable pageable);
}
