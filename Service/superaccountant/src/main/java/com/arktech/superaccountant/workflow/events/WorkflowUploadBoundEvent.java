package com.arktech.superaccountant.workflow.events;

import java.util.UUID;

/**
 * Published after a workflow's day-book upload is bound or replaced; the workflow is then in DATA_UPLOADED.
 * Publisher: UploadBindingService (worker B). Listener: analysis runner (worker R). Contract C-EVT.
 */
public record WorkflowUploadBoundEvent(UUID workflowId, UUID organizationId, UUID uploadJobId, String actor) {
}
