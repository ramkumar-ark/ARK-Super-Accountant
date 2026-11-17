package com.arktech.superaccountant.workflow.analysis;

import com.arktech.superaccountant.workflow.models.WorkflowType;

import java.util.Set;

/**
 * SPI for per-module analysis of a bound upload. The GST (Gb) and TDS (Tb) analyzers are the
 * production implementations; a workflow type with no matching analyzer counts as done.
 */
public interface WorkflowAnalyzer {
    /** Stable id stored on the run, e.g. "GST_DAYBOOK". */
    String code();

    /** The workflow types this analyzer runs for. */
    Set<WorkflowType> types();

    /**
     * Writes findings for the analyzed upload; throws to fail the run. Runs in its own transaction,
     * so a thrown analysis is recorded as FAILED without rolling back the workflow.
     */
    void analyze(AnalysisContext ctx);
}
