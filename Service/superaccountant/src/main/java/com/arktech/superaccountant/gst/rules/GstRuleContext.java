package com.arktech.superaccountant.gst.rules;

import com.arktech.superaccountant.masters.models.LedgerCategory;
import com.arktech.superaccountant.masters.models.UploadLedgerSnapshot;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Immutable rule input. snapshotsByName keys are ledgerName.trim().toLowerCase().
 */
public record GstRuleContext(
        UUID organizationId,
        UUID workflowId,
        UUID uploadJobId,
        String orgGstin,
        Map<String, UploadLedgerSnapshot> snapshotsByName
) {
    public Optional<UploadLedgerSnapshot> snapshot(String ledgerName) {
        if (ledgerName == null) return Optional.empty();
        return Optional.ofNullable(snapshotsByName.get(ledgerName.trim().toLowerCase()));
    }

    /** Classified category of the ledger; OTHER when unknown or predating the category column. */
    public LedgerCategory category(String ledgerName) {
        return snapshot(ledgerName)
                .map(s -> s.getCategory() == null ? LedgerCategory.OTHER : s.getCategory())
                .orElse(LedgerCategory.OTHER);
    }

    /** First two characters of the org GSTIN, or null when the org has no GSTIN. */
    public String orgStateCode() {
        return GstRuleSupport.stateCode(orgGstin);
    }
}
