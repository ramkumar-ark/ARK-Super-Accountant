package com.arktech.superaccountant.tds.tan;

import java.util.Locale;

/** The one TAN format rule for the app: four letters, five digits, one letter. */
public final class TanFormat {
    public static final String RULE_CODE = "TAN_FORMAT";
    public static final String PATTERN = "^[A-Z]{4}[0-9]{5}[A-Z]$";
    public static final String ERROR_MESSAGE =
            "Enter a valid 10-character TAN (format AAAA99999A, e.g. BLRA12345C).";

    private TanFormat() {
    }

    /** Trim and uppercase: what a user-entered TAN is stored as. Blank means none. */
    public static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.toUpperCase(Locale.ROOT);
    }

    public static boolean isValid(String value) {
        String normalized = normalize(value);
        return normalized != null && normalized.matches(PATTERN);
    }
}
