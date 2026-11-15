package com.arktech.superaccountant.workflow.snapshot;

import com.arktech.superaccountant.workflow.models.WorkflowType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FrozenMasterAttributeRepository
        extends JpaRepository<FrozenMasterAttribute, UUID> {

    List<FrozenMasterAttribute> findByOrganizationIdAndReleasedAtIsNull(UUID organizationId);

    List<FrozenMasterAttribute> findByOrganizationIdAndLedgerKeyAndAttributeAndWorkflowTypeAndReleasedAtIsNull(
            UUID organizationId, String ledgerKey, String attribute, WorkflowType workflowType);

    List<FrozenMasterAttribute> findByOrganizationIdAndLedgerKeyAndWorkflowTypeAndReleasedAtIsNull(
            UUID organizationId, String ledgerKey, WorkflowType workflowType);

    List<FrozenMasterAttribute> findByOrganizationIdAndWorkflowTypeAndReleasedAtIsNull(
            UUID organizationId, WorkflowType workflowType);
}
