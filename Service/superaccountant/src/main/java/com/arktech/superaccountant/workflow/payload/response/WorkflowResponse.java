package com.arktech.superaccountant.workflow.payload.response;

import com.arktech.superaccountant.workflow.models.Workflow;
import com.arktech.superaccountant.workflow.models.WorkflowStage;
import com.arktech.superaccountant.workflow.models.WorkflowType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record WorkflowResponse(
        UUID id,
        UUID organizationId,
        WorkflowType type,
        LocalDate periodStart,
        LocalDate periodEnd,
        String periodKey,
        int financialYear,
        int revision,
        UUID supersedesWorkflowId,
        WorkflowStage stage,
        boolean active,
        UUID uploadJobId,
        UUID mastersJobId,
        Instant baselineConfirmedAt,
        String baselineConfirmedBy,
        Instant finalizedAt,
        String finalizedBy,
        LocalDate frozenThrough,
        Instant filedAt,
        String filedBy,
        String filingRef,
        LocalDate filingRefDate,
        String createdBy,
        Instant createdAt) {

    public static WorkflowResponse from(Workflow w) {
        return new WorkflowResponse(
                w.getId(), w.getOrganizationId(), w.getType(), w.getPeriodStart(),
                w.getPeriodEnd(), w.getPeriodKey(), w.getFinancialYear(), w.getRevision(),
                w.getSupersedesWorkflowId(), w.getStage(), w.isActive(), w.getUploadJobId(),
                w.getMastersJobId(), w.getBaselineConfirmedAt(), w.getBaselineConfirmedBy(),
                w.getFinalizedAt(), w.getFinalizedBy(), w.getFrozenThrough(), w.getFiledAt(),
                w.getFiledBy(), w.getFilingRef(), w.getFilingRefDate(), w.getCreatedBy(),
                w.getCreatedAt());
    }
}
