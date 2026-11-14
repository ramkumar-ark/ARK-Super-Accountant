package com.arktech.superaccountant.workflow.findings;

import com.arktech.superaccountant.review.FindingApplier;
import com.arktech.superaccountant.workflow.models.Workflow;
import com.arktech.superaccountant.review.ReviewableFinding;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * SPI for a module that owns workflow findings: listing, row-locking, applying review actions
 * and saving. Implemented by the GST (Gd) and TDS (Tc) finding sources later.
 */
public interface WorkflowFindingSource extends BlockingFindingSource {
    String sourceCode();

    List<WorkflowFindingView> list(Workflow w);

    /** Row-locks the finding; empty when the finding is not this source's. */
    Optional<? extends ReviewableFinding> lockForUpdate(Workflow w, UUID findingId);

    FindingApplier<ReviewableFinding> applier(Workflow w);

    void save(ReviewableFinding f);

    WorkflowFindingView view(ReviewableFinding f);
}
