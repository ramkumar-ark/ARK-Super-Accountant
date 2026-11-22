package com.arktech.superaccountant.tds.payload.response;

import java.time.Instant;
import java.util.UUID;

public record DebtorTanResponse(String ledgerName, String ledgerKey, String tallyGuid, String tan,
                                UUID sourceFindingId, String updatedBy, Instant updatedAt,
                                boolean inLatestUpload) {
}
