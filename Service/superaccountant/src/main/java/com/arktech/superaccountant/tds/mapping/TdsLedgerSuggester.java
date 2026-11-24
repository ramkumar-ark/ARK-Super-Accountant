package com.arktech.superaccountant.tds.mapping;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class TdsLedgerSuggester {

    private static final Pattern TDS_TOKEN = Pattern.compile("(?<![A-Z])TDS(?![A-Z])");

    private TdsLedgerSuggester() {
    }

    public record Suggestion(TdsLedgerRole role, String section) {
    }

    public static boolean isCandidate(String ledgerName, String parentGroup, String groupChain, Boolean tdsApplicable) {
        if (hasTdsToken(ledgerName)) {
            return true;
        }
        if (groupChainMatchesTds(groupChain)) {
            return true;
        }
        if (parentGroupMatchesTds(parentGroup)) {
            return true;
        }
        return Boolean.TRUE.equals(tdsApplicable);
    }

    public static Suggestion suggest(String ledgerName, String parentGroup, String groupChain,
                                     Boolean tdsApplicable, Set<String> knownSectionCodes) {
        if ((ledgerName == null || ledgerName.isBlank())
                && !groupChainMatchesTds(groupChain)
                && !parentGroupMatchesTds(parentGroup)
                && !Boolean.TRUE.equals(tdsApplicable)) {
            return new Suggestion(null, null);
        }
        return new Suggestion(suggestRole(ledgerName, parentGroup, groupChain, tdsApplicable),
                suggestSection(ledgerName, knownSectionCodes));
    }

    private static boolean hasTdsToken(String ledgerName) {
        return ledgerName != null && !ledgerName.isBlank()
                && TDS_TOKEN.matcher(ledgerName.toUpperCase(Locale.ROOT)).find();
    }

    private static boolean groupChainMatchesTds(String groupChain) {
        if (groupChain == null) {
            return false;
        }
        String lower = groupChain.toLowerCase(Locale.ROOT);
        if (lower.contains("duties & taxes")) {
            return true;
        }
        for (String segment : lower.split("\\|")) {
            if (segment.trim().equals("tds")) {
                return true;
            }
        }
        return false;
    }

    private static boolean parentGroupMatchesTds(String parentGroup) {
        return parentGroup != null && groupChainMatchesTds(parentGroup);
    }

    private static TdsLedgerRole suggestRole(String ledgerName, String parentGroup, String groupChain,
                                             Boolean tdsApplicable) {
        String upper = ledgerName == null ? "" : ledgerName.toUpperCase(Locale.ROOT);
        boolean hasPayable = upper.contains("PAYABLE");
        boolean hasReceivable = upper.contains("RECEIVABLE");
        if (hasPayable && hasReceivable) {
            return null;
        }
        if (hasReceivable) {
            return TdsLedgerRole.TDS_RECEIVABLE;
        }
        if (hasPayable) {
            return TdsLedgerRole.TDS_PAYABLE;
        }
        if (groupChain != null) {
            String lower = groupChain.toLowerCase(Locale.ROOT);
            if (lower.contains("loans & advances") || lower.contains("current assets")) {
                return TdsLedgerRole.TDS_RECEIVABLE;
            }
        }
        if (groupChainMatchesTds(groupChain) || parentGroupMatchesTds(parentGroup)) {
            return TdsLedgerRole.TDS_PAYABLE;
        }
        if (Boolean.TRUE.equals(tdsApplicable)) {
            return TdsLedgerRole.TDS_PAYABLE;
        }
        return null;
    }

    private static String suggestSection(String ledgerName, Set<String> knownSectionCodes) {
        if (ledgerName == null || ledgerName.isBlank() || knownSectionCodes == null || knownSectionCodes.isEmpty()) {
            return null;
        }
        String upper = ledgerName.toUpperCase(Locale.ROOT);
        int[] matches = {0};
        String[] found = {null};
        for (String rawToken : upper.split("[ /]+")) {
            String token = rawToken.replace('-', '_');
            if (knownSectionCodes.contains(token)) {
                matches[0]++;
                found[0] = token;
            }
        }
        return matches[0] == 1 ? found[0] : null;
    }
}
