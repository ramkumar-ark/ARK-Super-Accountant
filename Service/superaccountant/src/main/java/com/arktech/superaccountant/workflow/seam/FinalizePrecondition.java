package com.arktech.superaccountant.workflow.seam;

import com.arktech.superaccountant.workflow.models.Workflow;

import java.util.List;

/**
 * Per-type Finalize preconditions, collected by WorkflowService.finalize as an optional list.
 * Implementations hold the rules for their own workflow type, so later types add rules without
 * touching WorkflowService.
 */
public interface FinalizePrecondition {

    boolean appliesTo(Workflow w);

    /** Human-readable unmet conditions; empty when Finalize may proceed. */
    List<String> unmet(Workflow w);
}
