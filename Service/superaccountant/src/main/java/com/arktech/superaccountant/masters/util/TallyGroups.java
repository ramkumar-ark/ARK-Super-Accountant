package com.arktech.superaccountant.masters.util;

import java.util.Locale;

/**
 * Helpers for the primary (top-level) Tally group of a snapshot's stored group chain. The chain is
 * nearest group first, lower-case and '|'-joined, so its last segment is the primary group.
 */
public final class TallyGroups {

    private TallyGroups() {}

    /** The primary group of a stored chain, lower-cased; null when the chain is missing or blank. */
    public static String topLevel(String groupChain) {
        if (groupChain == null) return null;
        String[] parts = groupChain.split("\\|");
        for (int i = parts.length - 1; i >= 0; i--) {
            String part = parts[i].trim().toLowerCase(Locale.ROOT);
            if (!part.isEmpty()) return part;
        }
        return null;
    }

    /**
     * A readable label for a primary group. The uploaded parent group's original casing is used when it is
     * the primary group itself; otherwise the stored lower-case name is title-cased.
     */
    public static String displayName(String code, String parentGroup) {
        if (parentGroup != null) {
            String parent = parentGroup.trim();
            if (parent.toLowerCase(Locale.ROOT).equals(code)) return parent;
        }
        return titleCase(code);
    }

    private static String titleCase(String value) {
        StringBuilder out = new StringBuilder(value.length());
        boolean atWordStart = true;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isLetter(c)) {
                out.append(atWordStart ? Character.toUpperCase(c) : c);
                atWordStart = false;
            } else {
                out.append(c);
                atWordStart = true;
            }
        }
        return out.toString();
    }
}
