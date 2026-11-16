package com.arktech.superaccountant.filedreturns.recon;

import java.util.List;
import java.util.Map;

/** Everything the books say for one baseline period: sales documents and the 3B totals. */
public record BooksGst(List<BooksDocument> salesDocuments, Map<String, HeadAmounts> gstr3bLines) {

    public HeadAmounts line(String rowCode) {
        return gstr3bLines.getOrDefault(rowCode, HeadAmounts.ZERO);
    }
}
