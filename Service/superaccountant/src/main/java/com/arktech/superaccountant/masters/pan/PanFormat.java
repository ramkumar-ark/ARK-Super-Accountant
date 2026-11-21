package com.arktech.superaccountant.masters.pan;

import java.util.Locale;

/** The one PAN format rule for the app: ten characters, five letters, four digits, one letter. */
public final class PanFormat {
    public static final String RULE_CODE = "PAN_FORMAT";
    public static final String PATTERN = "[A-Z]{5}[0-9]{4}[A-Z]";
    public static final String ERROR_MESSAGE = "Enter a valid PAN (format AAAAA9999A).";

    private PanFormat() {
    }

    /** Trim only: TDS treats lowercase and over-length values found in Tally as invalid. */
    public static boolean isValid(String value) {
        return value != null && !value.trim().isEmpty() && value.trim().matches(PATTERN);
    }

    /** Trim and uppercase: what a user-entered correction is stored as. */
    public static String normalize(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }
}
