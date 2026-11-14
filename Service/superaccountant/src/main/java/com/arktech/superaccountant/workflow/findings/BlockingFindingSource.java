package com.arktech.superaccountant.workflow.findings;

import com.arktech.superaccountant.workflow.models.Workflow;

/**
 * SPI for counting findings that block a workflow's Prepare/Finalize. The C2 severity rule is
 * the source's own business; the gate only sums the sources that apply. Implemented by the
 * masters source now, and by the GST, TDS and tamper sources later.
 */
public interface BlockingFindingSource {
    boolean appliesTo(Workflow w);

    long blockingCount(Workflow w);
}
