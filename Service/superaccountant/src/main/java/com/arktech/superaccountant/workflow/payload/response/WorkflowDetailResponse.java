package com.arktech.superaccountant.workflow.payload.response;

import com.arktech.superaccountant.workflow.service.WorkflowService.DependencyStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record WorkflowDetailResponse(
        WorkflowResponse workflow,
        List<DependencyStatus> dependencies,
        int blockingFindings,
        boolean baselineConfirmed,
        UUID id,
        boolean analysisAvailable,
        LocalDate requiredCoverageFrom,
        LocalDate requiredCoverageTo) {
}
