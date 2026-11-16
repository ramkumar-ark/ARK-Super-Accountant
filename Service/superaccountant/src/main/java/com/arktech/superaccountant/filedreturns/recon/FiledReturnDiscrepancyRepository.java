package com.arktech.superaccountant.filedreturns.recon;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FiledReturnDiscrepancyRepository extends JpaRepository<FiledReturnDiscrepancy, UUID> {

    List<FiledReturnDiscrepancy> findByWorkflowIdAndSupersededAtIsNull(UUID workflowId);

    List<FiledReturnDiscrepancy> findByOrganizationIdAndSupersededAtIsNull(UUID organizationId);
}
