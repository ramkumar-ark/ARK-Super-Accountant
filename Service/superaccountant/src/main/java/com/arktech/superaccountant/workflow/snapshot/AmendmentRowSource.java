package com.arktech.superaccountant.workflow.snapshot;

import java.util.Map;
import java.util.UUID;

/**
 * Seam for resolving the prepared-return row each amendment follow-up
 * produced. Implemented outside the workflow package (by gstr1) so workflow
 * never imports gstr1.
 */
public interface AmendmentRowSource {

    /**
     * Produced rows for the follow-ups of one workflow, keyed by follow-up id.
     * A follow-up may have no produced row yet (e.g. its amendment has not been
     * folded into any later return).
     */
    Map<UUID, RowRef> rowsForFollowUps(UUID workflowId);

    record RowRef(String tableCode, String rowKey, boolean lateReporting) {
    }
}
