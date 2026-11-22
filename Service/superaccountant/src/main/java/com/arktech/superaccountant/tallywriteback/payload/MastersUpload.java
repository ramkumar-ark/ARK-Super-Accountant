package com.arktech.superaccountant.tallywriteback.payload;

import java.time.Instant;
import java.util.UUID;

public record MastersUpload(UUID jobId, Instant uploadedAt, boolean stale) {
}
