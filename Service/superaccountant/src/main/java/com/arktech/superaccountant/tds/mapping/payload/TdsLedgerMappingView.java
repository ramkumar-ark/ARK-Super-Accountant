package com.arktech.superaccountant.tds.mapping.payload;

import com.arktech.superaccountant.tds.mapping.TdsLedgerArea;
import com.arktech.superaccountant.tds.mapping.TdsLedgerRole;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TdsLedgerMappingView(
        boolean complete,
        UUID mastersJobId,
        List<String> blockers,
        List<String> receivableWarnings,
        List<Row> ledgers
) {

    public record Row(
            String ledgerName,
            String parentGroup,
            boolean saved,
            TdsLedgerRole role,
            String section,
            TdsLedgerRole suggestedRole,
            String suggestedSection,
            TdsLedgerArea area,
            String savedBy,
            Instant savedAt
    ) {
    }
}
