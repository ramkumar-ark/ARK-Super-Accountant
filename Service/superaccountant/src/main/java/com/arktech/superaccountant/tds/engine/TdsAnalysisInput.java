package com.arktech.superaccountant.tds.engine;

import com.arktech.superaccountant.daybook.models.ParsedVoucher;
import com.arktech.superaccountant.masters.models.UploadLedgerSnapshot;
import com.arktech.superaccountant.tds.models.TdsSectionConfig;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record TdsAnalysisInput(
        UUID organizationId,
        UUID workflowId,
        UUID uploadJobId,
        List<ParsedVoucher> vouchers,
        Map<String, UploadLedgerSnapshot> snapshotsByName,
        Map<String, TdsSectionConfig> configsBySection,
        LocalDate fyStart,
        LocalDate periodStart,
        LocalDate periodEnd) {

    public TdsAnalysisInput {
        Objects.requireNonNull(organizationId, "organizationId");
        Objects.requireNonNull(workflowId, "workflowId");
        Objects.requireNonNull(uploadJobId, "uploadJobId");
        Objects.requireNonNull(vouchers, "vouchers");
        Objects.requireNonNull(snapshotsByName, "snapshotsByName");
        Objects.requireNonNull(configsBySection, "configsBySection");
        Objects.requireNonNull(fyStart, "fyStart");
        Objects.requireNonNull(periodStart, "periodStart");
        Objects.requireNonNull(periodEnd, "periodEnd");
        if (fyStart.isAfter(periodStart) || periodStart.isAfter(periodEnd)
                || !periodEnd.isBefore(fyStart.plusYears(1))) {
            throw new IllegalArgumentException(
                    "period must satisfy fyStart <= periodStart <= periodEnd < fyStart + 1 year");
        }
    }
}
