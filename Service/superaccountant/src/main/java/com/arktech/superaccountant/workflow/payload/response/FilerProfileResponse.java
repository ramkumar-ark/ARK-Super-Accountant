package com.arktech.superaccountant.workflow.payload.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record FilerProfileResponse(
        UUID id,
        String frequency,
        boolean iffOptIn,
        boolean einvoiceEnabled,
        Integer hsnDigits,
        LocalDate effectiveFrom,
        String createdBy,
        Instant createdAt) {
}
