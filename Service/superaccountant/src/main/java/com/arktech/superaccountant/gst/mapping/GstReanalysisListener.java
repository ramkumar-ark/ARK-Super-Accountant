package com.arktech.superaccountant.gst.mapping;

import com.arktech.superaccountant.workflow.models.Workflow;
import com.arktech.superaccountant.workflow.models.WorkflowStage;
import com.arktech.superaccountant.workflow.models.WorkflowType;
import com.arktech.superaccountant.workflow.repository.WorkflowRepository;
import com.arktech.superaccountant.workflow.analysis.WorkflowAnalysisService;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * After a tax-ledger mapping save, re-runs GST analysis for every active GSTR-1/GSTR-3B
 * workflow still awaiting analysis. No-op when the mapping is still incomplete.
 */
@Component
public class GstReanalysisListener {

    private static final Logger log = LoggerFactory.getLogger(GstReanalysisListener.class);

    private final TaxLedgerMappingGate gate;
    private final WorkflowRepository workflowRepository;
    private final WorkflowAnalysisService analysisService;

    public GstReanalysisListener(TaxLedgerMappingGate gate,
                                 WorkflowRepository workflowRepository,
                                 WorkflowAnalysisService analysisService) {
        this.gate = gate;
        this.workflowRepository = workflowRepository;
        this.analysisService = analysisService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMappingSaved(TaxLedgerMappingSavedEvent event) {
        if (!gate.isComplete(event.organizationId())) {
            return;
        }
        for (WorkflowType type : List.of(WorkflowType.GSTR1, WorkflowType.GSTR3B, WorkflowType.IFF)) {
            for (Workflow workflow : workflowRepository
                    .findAllByOrganizationIdAndTypeAndStageAndActiveTrue(
                            event.organizationId(), type, WorkflowStage.DATA_UPLOADED)) {
                if (workflow.getUploadJobId() == null) {
                    continue;
                }
                try {
                    analysisService.run(event.organizationId(), workflow.getId(), "system:tax-mapping");
                } catch (RuntimeException e) {
                    log.error("Re-analysis after tax-ledger mapping save failed for workflow {}: {}",
                            workflow.getId(), e.getMessage());
                }
            }
        }
    }
}
