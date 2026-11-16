package com.arktech.superaccountant.filedreturns.parse;

import com.arktech.superaccountant.filedreturns.payload.Gstr3bEntryRequest;

import java.util.List;

public record Gstr3bParsed(
        String gstin,
        String filingPeriod,
        List<Gstr3bEntryRequest.Row> rows,
        List<String> warnings) {
}
