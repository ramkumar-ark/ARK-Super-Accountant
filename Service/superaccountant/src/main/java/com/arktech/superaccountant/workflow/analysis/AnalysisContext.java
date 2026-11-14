package com.arktech.superaccountant.workflow.analysis;

import com.arktech.superaccountant.workflow.models.WorkflowType;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Everything an analyzer needs to read the bound upload and write findings.
 */
public record AnalysisContext(UUID workflowId, UUID organizationId, WorkflowType type,
                              LocalDate periodStart, LocalDate periodEnd, UUID uploadJobId,
                              UUID mastersJobId, UUID analysisRunId) {
}
