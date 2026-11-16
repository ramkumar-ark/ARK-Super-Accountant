package com.arktech.superaccountant.gst.rules;

import com.arktech.superaccountant.gst.mapping.GstHead;
import com.arktech.superaccountant.gst.mapping.TaxLedgerMapping;
import com.arktech.superaccountant.gst.mapping.TaxLedgerRole;
import com.arktech.superaccountant.masters.models.LedgerCategory;
import com.arktech.superaccountant.masters.models.UploadLedgerSnapshot;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Immutable rule input. snapshotsByName and taxLedgers keys are ledgerName.trim().toLowerCase().
 */
public record GstRuleContext(
        UUID organizationId,
        UUID workflowId,
        UUID uploadJobId,
        String orgGstin,
        Map<String, UploadLedgerSnapshot> snapshotsByName,
        Map<String, TaxLedgerMapping> taxLedgers
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

    /** Saved mapping of the ledger, or empty when unmapped. */
    public Optional<TaxLedgerMapping> taxLedger(String ledgerName) {
        if (ledgerName == null) return Optional.empty();
        return Optional.ofNullable(taxLedgers.get(ledgerName.trim().toLowerCase()));
    }

    /** Mapped head of the ledger; null when unmapped or NOT_TAX. */
    public GstHead head(String ledgerName) {
        return taxLedger(ledgerName)
                .filter(m -> m.getRole() != TaxLedgerRole.NOT_TAX)
                .map(TaxLedgerMapping::getHead)
                .orElse(null);
    }

    /** Mapped role of the ledger; null when unmapped. */
    public TaxLedgerRole role(String ledgerName) {
        return taxLedger(ledgerName).map(TaxLedgerMapping::getRole).orElse(null);
    }

    /** First two characters of the org GSTIN, or null when the org has no GSTIN. */
    public String orgStateCode() {
        return GstRuleSupport.stateCode(orgGstin);
    }
}
