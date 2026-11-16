package com.arktech.superaccountant.filedreturns.payload;

import java.util.List;
import java.util.UUID;

public record Gstr3bParseResponse(
        UUID fileId,
        String gstin,
        String filingPeriod,
        List<Gstr3bEntryRequest.Row> rows,
        List<String> warnings,
        String parserVersion) {
}
