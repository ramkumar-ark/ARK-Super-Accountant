package com.arktech.superaccountant.filedreturns.recon;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Document-number normalisation shared by book and filed-side matching. */
public final class DocKeys {

    private static final String SEPARATORS = "[\\s/\\-_.]";
    private static final Pattern DIGIT_RUN = Pattern.compile("\\d+");

    private DocKeys() {}

    public static String normalise(String value) {
        if (value == null) return "";
        return value.toUpperCase().replaceAll(SEPARATORS, "");
    }

    public static String loose(String value) {
        String normalised = normalise(value);
        if (normalised.isEmpty()) return "";
        Matcher matcher = DIGIT_RUN.matcher(normalised);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(result, Matcher.quoteReplacement(stripLeadingZeros(matcher.group())));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static String stripLeadingZeros(String digits) {
        return digits.replaceFirst("^0+(?!$)", "");
    }
}
