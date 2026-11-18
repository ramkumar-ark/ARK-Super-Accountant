package com.arktech.superaccountant.workflow.seam;

import com.arktech.superaccountant.workflow.models.Workflow;

import java.util.List;

/**
 * Per-type Prepare lifecycle hooks, collected by WorkflowService as an optional list
 * (same style as {@link FinalizePrecondition}). Implementations hold the rules for their
 * own workflow type, so later types add behavior without touching WorkflowService.
 */
public interface PrepareHook {

    boolean appliesTo(Workflow w);

    /** Fired after the workflow has entered PREPARE. Throwing rolls the transition back. */
    void onEnterPrepare(Workflow w);

    /** Fired after the workflow has been finalized and its snapshot frozen. */
    void onFinalize(Workflow w);

    /** Fired after the workflow has been un-finalized back into PREPARE. */
    void onUnfinalize(Workflow w);
}
