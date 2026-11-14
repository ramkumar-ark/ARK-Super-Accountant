package com.arktech.superaccountant.workflow.snapshot;

import com.arktech.superaccountant.workflow.models.Workflow;
import com.arktech.superaccountant.workflow.models.WorkflowStage;
import com.arktech.superaccountant.workflow.repository.WorkflowRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Company frozen-through date = the max periodEnd over active workflows whose
 * stage is FINALIZED or FILED, or whose stage is BASELINE with
 * baselineConfirmedAt set (N9(a)).
 */
@Component
public class FrozenThrough {

    @Autowired
    private WorkflowRepository workflowRepository;

    public Optional<LocalDate> of(UUID organizationId) {
        List<Workflow> workflows = workflowRepository.findAllByOrganizationIdOrderByCreatedAtDesc(organizationId);
        return workflows.stream()
                .filter(w -> w.isActive())
                .filter(w -> w.getStage() == WorkflowStage.FINALIZED || w.getStage() == WorkflowStage.FILED
                        || (w.getStage() == WorkflowStage.BASELINE && w.getBaselineConfirmedAt() != null))
                .map(Workflow::getPeriodEnd)
                .max(Comparator.naturalOrder());
    }
}
