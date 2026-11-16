package com.arktech.superaccountant.filedreturns.recon;

import com.arktech.superaccountant.filedreturns.FiledReturnSnapshotRepository;
import com.arktech.superaccountant.gst.mapping.TaxLedgerMappingGate;
import com.arktech.superaccountant.masters.models.ResolveStatus;
import com.arktech.superaccountant.workflow.models.Workflow;
import com.arktech.superaccountant.workflow.models.WorkflowStage;
import com.arktech.superaccountant.workflow.models.WorkflowType;
import com.arktech.superaccountant.workflow.repository.WorkflowRepository;
import com.arktech.superaccountant.workflow.seam.BaselineDiscrepancyCounter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Live, informational count of open material filed-return discrepancies on the active GSTR1 and
 * GSTR3B baseline rows. Returns null (not zero) when the mapping is incomplete or nothing is
 * imported, so the UI can distinguish "nothing to reconcile" from "not available".
 */
@Component
public class FiledReturnDiscrepancyCounter implements BaselineDiscrepancyCounter {

    static final Set<ResolveStatus> EXPLAINED =
            EnumSet.of(ResolveStatus.APPROVED, ResolveStatus.RESOLVED, ResolveStatus.DISCARDED);

    @Autowired
    private TaxLedgerMappingGate mappingGate;

    @Autowired
    private WorkflowRepository workflowRepository;

    @Autowired
    private FiledReturnSnapshotRepository snapshotRepository;

    @Autowired
    private FiledReturnDiscrepancyRepository discrepancyRepository;

    @Override
    public Integer openMaterialCount(UUID organizationId) {
        if (!mappingGate.isComplete(organizationId)) {
            return null;
        }
        List<Workflow> rows = workflowRepository
                .findAllByOrganizationIdAndStageAndActiveTrue(organizationId, WorkflowStage.BASELINE)
                .stream()
                .filter(w -> w.getType() == WorkflowType.GSTR1 || w.getType() == WorkflowType.GSTR3B)
                .toList();
        if (rows.isEmpty()) {
            return null;
        }
        List<UUID> rowIds = rows.stream().map(Workflow::getId).collect(Collectors.toList());
        boolean anyImported = snapshotRepository.findAllByOrganizationId(organizationId).stream()
                .anyMatch(s -> s.getSupersededAt() == null && rowIds.contains(s.getBaselineWorkflowId()));
        if (!anyImported) {
            return null;
        }
        return (int) discrepancyRepository
                .countByWorkflowIdInAndSupersededAtIsNullAndMaterialTrueAndResolveStatusNotIn(
                        rowIds, EXPLAINED);
    }
}
