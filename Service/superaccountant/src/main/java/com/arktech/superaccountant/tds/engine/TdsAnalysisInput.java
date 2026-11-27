package com.arktech.superaccountant.tds.engine;

import com.arktech.superaccountant.daybook.models.ParsedVoucher;
import com.arktech.superaccountant.masters.models.UploadLedgerSnapshot;
import com.arktech.superaccountant.tds.mapping.TdsLedgerMapping;
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
        Map<String, TdsLedgerMapping> mappingsByKey,
        Map<String, String> debtorTansByName,
        Map<String, TdsSectionConfig> configsBySection,
        LocalDate fyStart,
        LocalDate periodStart,
        LocalDate periodEnd,
        Map<String, String> newActRefsByCode) {

    public TdsAnalysisInput {
        Objects.requireNonNull(organizationId, "organizationId");
        Objects.requireNonNull(workflowId, "workflowId");
        Objects.requireNonNull(uploadJobId, "uploadJobId");
        Objects.requireNonNull(vouchers, "vouchers");
        Objects.requireNonNull(snapshotsByName, "snapshotsByName");
        Objects.requireNonNull(mappingsByKey, "mappingsByKey");
        Objects.requireNonNull(debtorTansByName, "debtorTansByName");
        Objects.requireNonNull(configsBySection, "configsBySection");
        Objects.requireNonNull(fyStart, "fyStart");
        Objects.requireNonNull(periodStart, "periodStart");
        Objects.requireNonNull(periodEnd, "periodEnd");
        Objects.requireNonNull(newActRefsByCode, "newActRefsByCode");
        if (fyStart.isAfter(periodStart) || periodStart.isAfter(periodEnd)
                || !periodEnd.isBefore(fyStart.plusYears(1))) {
            throw new IllegalArgumentException(
                    "period must satisfy fyStart <= periodStart <= periodEnd < fyStart + 1 year");
        }
    }

    public String displayRef(String code, LocalDate date) {
        if (code == null) {
            return null;
        }
        String ref = newActRefsByCode.get(code);
        return ref != null && date != null && TdsSectionConfig.newActApplies(date) ? ref : code;
    }
}
