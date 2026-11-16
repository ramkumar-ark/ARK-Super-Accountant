package com.arktech.superaccountant.tds.payload.response;

import com.arktech.superaccountant.tds.models.TdsChallan;
import com.arktech.superaccountant.workflow.models.WorkflowStage;

import java.util.List;
import java.util.UUID;

public record QuarterChallansResponse(UUID paymentWorkflowId, String periodKey,
                                      WorkflowStage stage, List<TdsChallan> challans) {
}
