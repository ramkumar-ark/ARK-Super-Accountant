package com.arktech.superaccountant.tds.mapping;

import com.arktech.superaccountant.masters.models.LedgerCategory;
import com.arktech.superaccountant.masters.models.UploadLedgerSnapshot;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class TdsLedgerSuggester {

    private static final Pattern TDS_TOKEN = Pattern.compile("(?<![A-Z])TDS(?![A-Z])");

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
        if (isTdsType(snapshot)) {
            return true;
        }
        if (snapshot.getTaxType() != null) {
            return false;
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
        if (natureOfPayment != null && !natureOfPayment.isBlank()) {
            if (natureOfPayment.trim().equalsIgnoreCase("any")) {
                return null;
            }
            String fromNature = sectionFromNature(natureOfPayment, knownSectionCodes);
            if (fromNature != null) {
                return fromNature;
            }
        }
        return sectionFromTokens(ledgerName, knownSectionCodes);
    }

    private static String sectionFromNature(String natureOfPayment, Set<String> knownSectionCodes) {
        String direct = sectionFromTokens(natureOfPayment, knownSectionCodes);
        if (direct != null) {
            return direct;
        }
        String lower = natureOfPayment.toLowerCase(Locale.ROOT);
        String code = null;
        if (lower.contains("contractor")) {
            code = "194C";
        } else if (lower.contains("commission") || lower.contains("brokerage")) {
            code = "194H";
        } else if (lower.contains("professional")) {
            code = "194J_B";
        } else if (lower.contains("technical")) {
            code = "194J_A";
        }
        return code != null && knownSectionCodes.contains(code) ? code : null;
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
