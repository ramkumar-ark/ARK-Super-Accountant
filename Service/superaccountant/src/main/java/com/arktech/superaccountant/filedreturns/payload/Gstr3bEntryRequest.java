package com.arktech.superaccountant.filedreturns.payload;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record Gstr3bEntryRequest(
        String filingPeriod,
        UUID fileId,
        String parserVersion,
        List<Row> rows) {

    public record Row(
            String rowCode,
            BigDecimal taxable,
            BigDecimal igst,
            BigDecimal cgst,
            BigDecimal sgst,
            BigDecimal cess) {
    }
}
