package com.arktech.superaccountant.masters.util;

import java.util.List;

/**
 * Single source of truth for the standard GST tax ledgers (22) applied when a GST-registered
 * organization onboards a predefined template. Names are exactly the ones the captain
 * specified; do not rename without updating consumers that match on them.
 */
public final class StandardGstLedgers {

    public static final String DUTIES_AND_TAXES = "Duties & Taxes";
    public static final String CURRENT_ASSETS = "Current Assets";

    private static final List<String> TAX_HEADS = List.of("SGST", "CGST", "IGST", "Cess");

    public record StandardGstLedger(String ledgerName, String parentGroup) {
    }

    private StandardGstLedgers() {
    }

    public static List<StandardGstLedger> ledgers() {
        var ledgers = new java.util.ArrayList<StandardGstLedger>();
        for (String head : TAX_HEADS) {
            ledgers.add(new StandardGstLedger("Input " + head + " A/c", DUTIES_AND_TAXES));
            ledgers.add(new StandardGstLedger("Output " + head + " A/c", DUTIES_AND_TAXES));
            ledgers.add(new StandardGstLedger(head + " Payable A/c", DUTIES_AND_TAXES));
            ledgers.add(new StandardGstLedger(head + " Cash Balance A/c", CURRENT_ASSETS));
        }
        for (String head : List.of("SGST", "CGST", "IGST")) {
            ledgers.add(new StandardGstLedger("TDS " + head + " Receivable A/c", DUTIES_AND_TAXES));
            ledgers.add(new StandardGstLedger(head + " Credit Balance A/c", CURRENT_ASSETS));
        }
        return List.copyOf(ledgers);
    }
}
