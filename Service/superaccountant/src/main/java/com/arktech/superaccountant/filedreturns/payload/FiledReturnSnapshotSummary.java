package com.arktech.superaccountant.filedreturns.payload;

import com.arktech.superaccountant.filedreturns.FiledFormType;
import com.arktech.superaccountant.filedreturns.FiledReturnSnapshot;
import com.arktech.superaccountant.filedreturns.FiledSourceKind;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record FiledReturnSnapshotSummary(
        UUID id,
        FiledFormType formType,
        String filingPeriod,
        FiledSourceKind sourceKind,
        String importedBy,
        Instant importedAt,
        boolean superseded,
        long lineCount) {

    public static FiledReturnSnapshotSummary from(FiledReturnSnapshot s, long lineCount) {
        return new FiledReturnSnapshotSummary(
                s.getId(), s.getFormType(), s.getFilingPeriod(), s.getSourceKind(),
                s.getImportedBy(), s.getImportedAt(), s.getSupersededAt() != null, lineCount);
    }
}
