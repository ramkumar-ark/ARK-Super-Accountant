package com.arktech.superaccountant.gst.mapping;

public final class TaxLedgerSuggester {

    private static final java.util.regex.Pattern CESS_TOKEN = java.util.regex.Pattern.compile("(?<![A-Z])CESS(?![A-Z])");

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
        if (upper.contains("INPUT")) {
            return TaxLedgerRole.INPUT;
        }
        return null;
    }
}
