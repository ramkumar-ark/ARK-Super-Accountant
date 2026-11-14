package com.arktech.superaccountant.workflow.snapshot;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TamperChangeRepository extends JpaRepository<TamperChange, UUID> {
    List<TamperChange> findByOrganizationId(UUID organizationId);

    List<TamperChange> findByUploadJobId(UUID uploadJobId);

    boolean existsByUploadJobId(UUID uploadJobId);
}
