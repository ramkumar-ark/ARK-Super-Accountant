package com.arktech.superaccountant.workflow.payload.response;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

import java.util.List;

/**
 * Upload response: the workflow fields stay top-level and the intake warnings are
 * added next to them.
 */
public record WorkflowUploadResponse(
        @JsonUnwrapped WorkflowResponse workflow,
        List<String> warnings) {
}
