package com.arktech.superaccountant.masters.payload.request;

import com.arktech.superaccountant.masters.models.FindingSeverity;
import com.arktech.superaccountant.masters.models.LedgerCategory;
import com.arktech.superaccountant.masters.models.MismatchType;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * Bulk override of OPEN findings, or bulk approval of pending (ACKNOWLEDGED) ones. Targets either the listed {@code findingIds} or, when that is empty, every OPEN
 * finding of the job matching the filter fields. At least one of the two must be given.
 */
@Data
@NoArgsConstructor
public class BulkFindingActionRequest {
    /** "ACKNOWLEDGE" (default): override OPEN findings. "APPROVE": approve pending findings (accountants only). */
    private String action = "ACKNOWLEDGE";

    private List<UUID> findingIds;

    // Filter fields, same meaning as on the findings list endpoints.
    private String ruleCode;
    private MismatchType mismatchType;
    private FindingSeverity severity;
    private LedgerCategory category;

    private String note;

    /** Explicit opt-in to act on every matching finding when no ids and no filter fields are given. */
    private boolean allMatching;

    /** Only count the findings the action would touch; changes nothing. */
    private boolean dryRun;

    public boolean hasFilter() {
        return (ruleCode != null && !ruleCode.isBlank()) || mismatchType != null || severity != null || category != null;
    }
}
