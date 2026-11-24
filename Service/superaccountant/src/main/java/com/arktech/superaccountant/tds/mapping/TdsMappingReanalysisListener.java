package com.arktech.superaccountant.tds.mapping;

import com.arktech.superaccountant.workflow.analysis.WorkflowAnalysisService;
import com.arktech.superaccountant.workflow.models.Workflow;
import com.arktech.superaccountant.workflow.models.WorkflowStage;
import com.arktech.superaccountant.workflow.models.WorkflowType;
import com.arktech.superaccountant.workflow.repository.WorkflowRepository;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * After a TDS-ledger mapping save, re-runs TDS analysis for every active TDS_PAYMENT/TDS_26Q
 * workflow still awaiting analysis. No-op when the mapping is still incomplete.
 */
@Component
public class TdsMappingReanalysisListener {

    private static final Logger log = LoggerFactory.getLogger(TdsMappingReanalysisListener.class);

    private final TdsLedgerMappingGate gate;
    private final WorkflowRepository workflowRepository;
    private final WorkflowAnalysisService analysisService;

    public TdsMappingReanalysisListener(TdsLedgerMappingGate gate,
                                        WorkflowRepository workflowRepository,
                                        WorkflowAnalysisService analysisService) {
        this.gate = gate;
        this.workflowRepository = workflowRepository;
        this.analysisService = analysisService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMappingSaved(TdsLedgerMappingSavedEvent event) {
        if (!gate.isComplete(event.organizationId())) {
            return;
        }
        for (WorkflowType type : List.of(WorkflowType.TDS_PAYMENT, WorkflowType.TDS_26Q)) {
            for (Workflow workflow : workflowRepository
                    .findAllByOrganizationIdAndTypeAndStageAndActiveTrue(
                            event.organizationId(), type, WorkflowStage.DATA_UPLOADED)) {
                if (workflow.getUploadJobId() == null) {
                    continue;
                }
                try {
                    analysisService.run(event.organizationId(), workflow.getId(), "system:tds-mapping");
                } catch (RuntimeException e) {
                    log.error("Re-analysis after TDS-ledger mapping save failed for workflow {}: {}",
                            workflow.getId(), e.getMessage());
                }
            }
        }
    }
}
