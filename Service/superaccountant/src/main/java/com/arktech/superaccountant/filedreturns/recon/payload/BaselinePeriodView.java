package com.arktech.superaccountant.filedreturns.recon.payload;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.arktech.superaccountant.filedreturns.payload.FiledReturnSnapshotSummary;

public record BaselinePeriodView(UUID workflowId, String type, String periodKey, LocalDate periodStart,
                                 LocalDate periodEnd, boolean confirmed, String status,
                                 List<FiledReturnSnapshotSummary> snapshots, int materialOpen,
                                 int materialExplained, int info, Map<String, HeadVariance> variance) {

    public record HeadVariance(BigDecimal filed, BigDecimal books, BigDecimal diff) {
    }
}
