package com.arktech.superaccountant.workflow.seam;

import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Seam for the review worker (R): counts blocking findings for a workflow.
 * No-op default returns 0 until the shared review service lands.
 */
@Service
public class WorkflowGate {
    public int blockingFindingCount(UUID workflowId) {
        return 0;
    }
}
