package com.arktech.superaccountant.workflow.seam;

import com.arktech.superaccountant.workflow.models.WorkflowStage;
import com.arktech.superaccountant.workflow.repository.WorkflowRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Seam (C4-BASE): the company-wide baseline confirmation gate. The company is
 * confirmed when no active BASELINE row is unconfirmed.
 */
@Service
public class BaselineGate {

    @Autowired
    private WorkflowRepository workflowRepository;

    public BaselineGate() {
    }

    public BaselineGate(WorkflowRepository workflowRepository) {
        this.workflowRepository = workflowRepository;
    }

    public boolean isConfirmed(UUID organizationId) {
        return !workflowRepository
                .existsByOrganizationIdAndStageAndActiveTrueAndBaselineConfirmedAtIsNull(
                        organizationId, WorkflowStage.BASELINE);
    }
}
