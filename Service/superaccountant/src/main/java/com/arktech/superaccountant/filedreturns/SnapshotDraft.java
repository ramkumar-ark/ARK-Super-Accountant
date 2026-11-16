package com.arktech.superaccountant.filedreturns;

import java.util.List;
import java.util.UUID;

public record SnapshotDraft(
        FiledFormType formType,
        String filingPeriod,
        String gstin,
        FiledSourceKind sourceKind,
        UUID fileId,
        String parserVersion,
        List<FiledReturnLine> lines) {
}
