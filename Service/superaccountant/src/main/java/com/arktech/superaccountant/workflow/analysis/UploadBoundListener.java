package com.arktech.superaccountant.workflow.analysis;

import com.arktech.superaccountant.workflow.events.WorkflowUploadBoundEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Runs the analysis after the binding transaction commits, so the run never sees an uncommitted
 * workflow. Analysis must never surface on the binding request, so every runtime failure is
 * caught and logged: a failed run stays in DATA_UPLOADED and can be retried via the endpoint.
 */
@Component
public class UploadBoundListener {

    private static final Logger log = LoggerFactory.getLogger(UploadBoundListener.class);

    @Autowired
    private WorkflowAnalysisService analysisService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUploadBound(WorkflowUploadBoundEvent event) {
        try {
            analysisService.run(event.organizationId(), event.workflowId(), event.actor());
        } catch (RuntimeException e) {
            log.error("Analysis after upload binding failed for workflow {}", event.workflowId(), e);
        }
    }
}
