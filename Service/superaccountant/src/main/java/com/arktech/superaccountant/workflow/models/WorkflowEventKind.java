package com.arktech.superaccountant.workflow.models;

public enum WorkflowEventKind {
    STARTED,
    UPLOAD_BOUND,
    ANALYSIS_DONE,
    ADVANCED,
    FINALIZED,
    UNFINALIZED,
    FILED,
    BASELINE_CONFIRMED,
    AMENDMENT_RECORDED
}
