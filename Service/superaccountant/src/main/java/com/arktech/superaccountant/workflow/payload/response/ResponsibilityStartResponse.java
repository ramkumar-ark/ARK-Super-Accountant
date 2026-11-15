package com.arktech.superaccountant.workflow.payload.response;

import java.time.Instant;
import java.time.LocalDate;

public record ResponsibilityStartResponse(
        String type,
        LocalDate firstPeriodStart,
        String setBy,
        Instant setAt,
        boolean locked,
        int baselineRowCount) {
}
