package com.arktech.superaccountant.gst.mapping;

public final class TaxLedgerSuggester {

    private static final java.util.regex.Pattern CESS_TOKEN = java.util.regex.Pattern.compile("(?<![A-Z])CESS(?![A-Z])");

    private static final java.util.regex.Pattern TDS_TOKEN = java.util.regex.Pattern.compile("(?<![A-Z])TDS(?![A-Z])");

    private static final java.util.regex.Pattern ITC_TOKEN = java.util.regex.Pattern.compile("(?<![A-Z])ITC(?![A-Z])");

    private TaxLedgerSuggester() {
    }

    public record Suggestion(TaxLedgerRole role, GstHead head) {
    }

    public static Suggestion suggest(String ledgerName) {
        if (ledgerName == null || ledgerName.isBlank()) {
            return new Suggestion(null, null);
        }
        return new Suggestion(suggestRole(ledgerName), suggestHead(ledgerName));
    }

    private static GstHead suggestHead(String ledgerName) {
        String upper = ledgerName.toUpperCase();
        if (upper.contains("IGST")) {
            return GstHead.IGST;
        }
        if (upper.contains("CGST")) {
            return GstHead.CGST;
        }
        if (upper.contains("SGST") || upper.contains("UTGST")) {
            return GstHead.SGST;
        }
        if (CESS_TOKEN.matcher(upper).find()) {
            return GstHead.CESS;
        }
        return null;
    }

    private static TaxLedgerRole suggestRole(String ledgerName) {
        String upper = ledgerName.toUpperCase();
        if (upper.contains("RCM") || upper.contains("REVERSE")) {
            return upper.contains("INPUT") ? TaxLedgerRole.RCM_INPUT : TaxLedgerRole.RCM_LIABILITY;
        }
        if (upper.contains("OUTPUT")) {
            return TaxLedgerRole.OUTPUT;
        }
        if (upper.contains("CASH")) {
            return TaxLedgerRole.CASH_BALANCE;
        }
        if (upper.contains("LIABILITY")) {
            return TaxLedgerRole.LIABILITY_BALANCE;
        }
        if (ITC_TOKEN.matcher(upper).find()
                || (upper.contains("CREDIT") && (upper.contains("BALANCE") || upper.contains("INPUT")))) {
            return TaxLedgerRole.CREDIT_BALANCE;
        }
        if (TDS_TOKEN.matcher(upper).find()) {
            return TaxLedgerRole.GST_TDS;
        }
        if (upper.contains("INPUT")) {
            return TaxLedgerRole.INPUT;
        }
        return null;
    }
}
