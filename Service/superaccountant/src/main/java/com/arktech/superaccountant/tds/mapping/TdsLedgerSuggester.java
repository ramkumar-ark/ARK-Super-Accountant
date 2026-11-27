package com.arktech.superaccountant.tds.mapping;

import com.arktech.superaccountant.masters.models.LedgerCategory;
import com.arktech.superaccountant.masters.models.UploadLedgerSnapshot;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class TdsLedgerSuggester {

    private static final Pattern TDS_TOKEN = Pattern.compile("(?<![A-Z])TDS(?![A-Z])");

    /** Tax-head words that mark a ledger as GST whatever its Tally flags say. */
    private static final Pattern GST_TOKEN = Pattern.compile("(?<![A-Z])(?:CGST|SGST|UTGST|IGST|GST)(?![A-Z])");

    private static final Pattern CESS_TOKEN = Pattern.compile("(?<![A-Z])CESS(?![A-Z])");

    private TdsLedgerSuggester() {
    }

    public record Suggestion(TdsLedgerRole role, String section) {
    }

    public static boolean isCandidate(UploadLedgerSnapshot snapshot) {
        LedgerCategory category = snapshot.getCategory();
        if (Boolean.TRUE.equals(snapshot.getGstApplicable())
                || "GST".equalsIgnoreCase(snapshot.getTaxType())
                || category == LedgerCategory.GST
                || category == LedgerCategory.EXPENSE
                || category == LedgerCategory.PURCHASE) {
            return false;
        }
        if (looksLikeGst(snapshot)) {
            return false;
        }
        if (isTdsType(snapshot)) {
            return true;
        }
        return hasTdsToken(snapshot.getLedgerName()) || groupChainMatchesTds(snapshot.getGroupChain());
    }

    public static Suggestion suggest(UploadLedgerSnapshot snapshot, Set<String> knownSectionCodes) {
        String ledgerName = snapshot.getLedgerName();
        String groupChain = snapshot.getGroupChain();
        boolean tdsType = isTdsType(snapshot);
        if (!tdsType && !hasTdsToken(ledgerName) && !hasTdsGroup(groupChain)) {
            return new Suggestion(dutiesAndTaxes(groupChain) ? TdsLedgerRole.NOT_TDS : null, null);
        }
        return new Suggestion(suggestRole(ledgerName, groupChain),
                suggestSection(ledgerName, snapshot.getTdsNatureOfPayment(), knownSectionCodes));
    }

    /**
     * GST-side ledgers (including GST TDS, s.51 of the GST Act, and cess) are not Income-tax TDS
     * ledgers. Tally exports do not always flag them (taxtype "Others" or absent), so the name's
     * GST head words and a GST group segment decide, unless Tally itself marks the ledger TDS.
     * A bare "TDS" name token never overrides that; "cess" alone only counts when no TDS word is
     * present (education cess on TDS stays a candidate).
     */
    private static boolean looksLikeGst(UploadLedgerSnapshot snapshot) {
        if (isTdsType(snapshot)) {
            return false;
        }
        String name = snapshot.getLedgerName() == null ? "" : snapshot.getLedgerName().toUpperCase(Locale.ROOT);
        if (GST_TOKEN.matcher(name).find() || hasGstGroup(snapshot.getGroupChain())) {
            return true;
        }
        return CESS_TOKEN.matcher(name).find() && !hasTdsToken(name);
    }

    private static boolean hasGstGroup(String groupChain) {
        if (groupChain == null) {
            return false;
        }
        for (String segment : groupChain.toLowerCase(Locale.ROOT).split("\\|")) {
            if (segment.trim().equals("gst")) {
                return true;
            }
        }
        return false;
    }

    private static boolean isTdsType(UploadLedgerSnapshot snapshot) {
        return "TDS".equalsIgnoreCase(snapshot.getTaxType());
    }

    private static boolean hasTdsToken(String ledgerName) {
        return ledgerName != null && !ledgerName.isBlank()
                && TDS_TOKEN.matcher(ledgerName.toUpperCase(Locale.ROOT)).find();
    }

    private static boolean dutiesAndTaxes(String groupChain) {
        return groupChain != null && groupChain.toLowerCase(Locale.ROOT).contains("duties & taxes");
    }

    private static boolean groupChainMatchesTds(String groupChain) {
        return dutiesAndTaxes(groupChain) || hasTdsGroup(groupChain);
    }

    private static boolean hasTdsGroup(String groupChain) {
        if (groupChain == null) {
            return false;
        }
        String lower = groupChain.toLowerCase(Locale.ROOT);
        for (String segment : lower.split("\\|")) {
            if (segment.trim().equals("tds")) {
                return true;
            }
        }
        return false;
    }

    private static TdsLedgerRole suggestRole(String ledgerName, String groupChain) {
        String upper = ledgerName == null ? "" : ledgerName.toUpperCase(Locale.ROOT);
        boolean hasPayable = upper.contains("PAYABLE");
        boolean hasReceivable = upper.contains("RECEIVABLE");
        if (hasReceivable && !hasPayable) {
            return TdsLedgerRole.TDS_RECEIVABLE;
        }
        if (hasPayable && !hasReceivable) {
            return TdsLedgerRole.TDS_PAYABLE;
        }
        if (groupChain != null) {
            String lower = groupChain.toLowerCase(Locale.ROOT);
            if (lower.contains("loans & advances") || lower.contains("current assets")) {
                return TdsLedgerRole.TDS_RECEIVABLE;
            }
            if (groupChainMatchesTds(groupChain) || lower.contains("current liabilities")) {
                return TdsLedgerRole.TDS_PAYABLE;
            }
        }
        return null;
    }

    private static String suggestSection(String ledgerName, String natureOfPayment, Set<String> knownSectionCodes) {
        if (knownSectionCodes == null || knownSectionCodes.isEmpty()) {
            return null;
        }
        String fromNature = sectionFromTokens(natureOfPayment, knownSectionCodes);
        return fromNature != null ? fromNature : sectionFromTokens(ledgerName, knownSectionCodes);
    }

    private static String sectionFromTokens(String text, Set<String> knownSectionCodes) {
        if (text == null || text.isBlank()) {
            return null;
        }
        int matches = 0;
        String found = null;
        for (String rawToken : text.toUpperCase(Locale.ROOT).split("[^A-Z0-9_-]+")) {
            String token = rawToken.replace('-', '_');
            if (knownSectionCodes.contains(token)) {
                matches++;
                found = token;
            }
        }
        return matches == 1 ? found : null;
    }
}
