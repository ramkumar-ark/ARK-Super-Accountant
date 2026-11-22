package com.arktech.superaccountant.tallywriteback.repository;

import com.arktech.superaccountant.tallywriteback.models.TallyExportBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TallyExportBatchRepository extends JpaRepository<TallyExportBatch, UUID> {

    List<TallyExportBatch> findByOrganizationIdOrderByGeneratedAtDesc(UUID organizationId);

    Optional<TallyExportBatch> findByIdAndOrganizationId(UUID id, UUID organizationId);
}
