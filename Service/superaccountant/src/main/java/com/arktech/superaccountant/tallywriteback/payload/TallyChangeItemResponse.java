package com.arktech.superaccountant.tallywriteback.payload;

import com.arktech.superaccountant.tallywriteback.models.ExistenceOutcome;
import com.arktech.superaccountant.tallywriteback.models.TallyChangeSourceType;
import com.arktech.superaccountant.tallywriteback.models.TallyChangeStatus;
import com.arktech.superaccountant.tallywriteback.models.TallyChangeType;
import com.arktech.superaccountant.tallywriteback.models.TallyFailureReason;
import com.arktech.superaccountant.tallywriteback.models.TallyField;

import java.time.Instant;
import java.util.UUID;

public record TallyChangeItemResponse(
        UUID id,
        String ledgerKey,
        String ledgerName,
        TallyChangeType changeType,
        TallyField field,
        String baselineValue,
        String newValue,
        TallyChangeStatus status,
        TallyChangeSourceType sourceType,
        String approvedBy,
        Instant approvedAt,
        String observedValue,
        TallyFailureReason failureReason,
        ExistenceOutcome lastCheckOutcome,
        Instant lastCheckedAt,
        Instant createdAt,
        Instant resolvedAt,
        ExistenceOutcome checkOutcome,
        LookupReason checkReason,
        String currentTallyName,
        String currentTallyPan) {
}
