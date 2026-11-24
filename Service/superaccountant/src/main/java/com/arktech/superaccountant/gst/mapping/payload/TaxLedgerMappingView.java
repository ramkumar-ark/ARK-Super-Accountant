package com.arktech.superaccountant.gst.mapping.payload;

import com.arktech.superaccountant.gst.mapping.GstHead;
import com.arktech.superaccountant.gst.mapping.TaxLedgerRole;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TaxLedgerMappingView(
        boolean complete,
        UUID mastersJobId,
        List<String> blockers,
        List<Row> ledgers,
        boolean gstApplicable
) {

    public record Row(
            String ledgerName,
            String parentGroup,
            boolean saved,
            TaxLedgerRole role,
            GstHead head,
            TaxLedgerRole suggestedRole,
            GstHead suggestedHead,
            String savedBy,
            Instant savedAt
    ) {
    }
}
