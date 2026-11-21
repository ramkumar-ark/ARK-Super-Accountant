package com.arktech.superaccountant.workflow.snapshot;

import java.util.List;
import java.util.UUID;

/**
 * Seam between the snapshot/tamper comparison and the Tally write-back module:
 * which PAN changes did this masters upload apply?
 */
public interface AppliedChangeLookup {

    List<AppliedPanChange> appliedPanChanges(UUID uploadJobId);
}
