package com.arktech.superaccountant.workflow.snapshot;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Supplies engine-detected retro-active findings for a completed day-book upload.
 * Implemented outside this package so the comparison never depends on a feature module.
 */
public interface RetroFlagSource {

    List<Flag> retroFlags(UUID organizationId, UUID uploadJobId);

    record Flag(String voucherKey, LocalDate voucherDate, String section, String deducteeLedger,
                BigDecimal shortfall) {
    }
}
