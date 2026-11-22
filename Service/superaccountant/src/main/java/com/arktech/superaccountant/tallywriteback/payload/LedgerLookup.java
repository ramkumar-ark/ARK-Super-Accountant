package com.arktech.superaccountant.tallywriteback.payload;

import com.arktech.superaccountant.tallywriteback.models.ExistenceOutcome;

import java.time.Instant;
import java.util.UUID;

public record LedgerLookup(
        ExistenceOutcome outcome,
        String currentName,
        String tallyPan,
        String tallyGuid,
        UUID mastersJobId,
        Instant mastersUploadedAt,
        LookupReason reason,
        boolean stale) {
}
