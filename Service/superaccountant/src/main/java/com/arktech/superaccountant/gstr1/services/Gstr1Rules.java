package com.arktech.superaccountant.gstr1.services;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * GSTR-1 reporting rule constants. B2CL threshold is keyed by period so future
 * threshold changes can be applied without touching the classifier logic.
 */
public final class Gstr1Rules {

    public static final BigDecimal DEFAULT_B2CL_THRESHOLD = new BigDecimal("250000.00");

    private static final Map<YearMonth, BigDecimal> B2CL_THRESHOLDS = Map.of();

    /** Valid GST tax rates (percent), including nil/exempt and compensation-cess-era rates. */
    public static final Set<BigDecimal> ALLOWED_RATES = Set.of(
            new BigDecimal("0"),
            new BigDecimal("0.1"),
            new BigDecimal("0.25"),
            new BigDecimal("1"),
            new BigDecimal("1.5"),
            new BigDecimal("3"),
            new BigDecimal("5"),
            new BigDecimal("7.5"),
            new BigDecimal("12"),
            new BigDecimal("18"),
            new BigDecimal("28"),
            new BigDecimal("40"));

    /** State codes 01..38 (incl. 97 other territory). */
    public static final Set<String> STATE_CODES = Set.of(
            "01", "02", "03", "04", "05", "06", "07", "08", "09", "10",
            "11", "12", "13", "14", "15", "16", "17", "18", "19", "20",
            "21", "22", "23", "24", "25", "26", "27", "28", "29", "30",
            "31", "32", "33", "34", "35", "36", "37", "38", "97");

    /** Invoice-number shape accepted for GSTR-1 reporting. */
    public static final Pattern INVOICE_NUMBER_PATTERN =
            Pattern.compile("^[A-Za-z0-9/-]{1,16}$");

    private static final Map<String, String> STATE_NAME_TO_CODE = Map.ofEntries(
            Map.entry("jammu and kashmir", "01"),
            Map.entry("himachal pradesh", "02"),
            Map.entry("punjab", "03"),
            Map.entry("chandigarh", "04"),
            Map.entry("uttarakhand", "05"),
            Map.entry("haryana", "06"),
            Map.entry("delhi", "07"),
            Map.entry("nct of delhi", "07"),
            Map.entry("rajasthan", "08"),
            Map.entry("uttar pradesh", "09"),
            Map.entry("bihar", "10"),
            Map.entry("sikkim", "11"),
            Map.entry("arunachal pradesh", "12"),
            Map.entry("nagaland", "13"),
            Map.entry("manipur", "14"),
            Map.entry("mizoram", "15"),
            Map.entry("tripura", "16"),
            Map.entry("meghalaya", "17"),
            Map.entry("assam", "18"),
            Map.entry("west bengal", "19"),
            Map.entry("jharkhand", "20"),
            Map.entry("odisha", "21"),
            Map.entry("orissa", "21"),
            Map.entry("chhattisgarh", "22"),
            Map.entry("madhya pradesh", "23"),
            Map.entry("gujarat", "24"),
            Map.entry("dadra and nagar haveli and daman and diu", "26"),
            Map.entry("daman and diu", "26"),
            Map.entry("dadra and nagar haveli", "26"),
            Map.entry("maharashtra", "27"),
            Map.entry("karnataka", "29"),
            Map.entry("goa", "30"),
            Map.entry("lakshadweep", "31"),
            Map.entry("kerala", "32"),
            Map.entry("tamil nadu", "33"),
            Map.entry("puducherry", "34"),
            Map.entry("pondicherry", "34"),
            Map.entry("andaman and nicobar islands", "35"),
            Map.entry("telangana", "36"),
            Map.entry("andhra pradesh", "37"),
            Map.entry("ladakh", "38"),
            Map.entry("other territory", "97"),
            Map.entry("other countries", "97"));

    /**
     * Normalizes a captured place of supply into a GSTR-1 state code. Accepts
     * an already-valid code ("29"), a Tally state name ("Karnataka") or a
     * code-prefixed value ("29-Karnataka"). Returns null when unresolvable.
     */
    public static String normalizeStateCode(String pos) {
        if (pos == null || pos.isBlank()) {
            return null;
        }
        String trimmed = pos.trim();
        if (STATE_CODES.contains(trimmed)) {
            return trimmed;
        }
        String key = trimmed.toLowerCase(Locale.ROOT);
        String code = STATE_NAME_TO_CODE.get(key);
        if (code != null) {
            return code;
        }
        int dash = trimmed.indexOf('-');
        if (dash == 2) {
            String prefix = trimmed.substring(0, 2);
            if (STATE_CODES.contains(prefix)) {
                return prefix;
            }
        }
        return null;
    }

    private Gstr1Rules() {
    }

    public static BigDecimal b2clThreshold(YearMonth period) {
        return B2CL_THRESHOLDS.getOrDefault(period, DEFAULT_B2CL_THRESHOLD);
    }

    public static boolean isAllowedRate(BigDecimal rate) {
        if (rate == null) {
            return false;
        }
        // numeric equality regardless of scale: 18.0 must equal 18
        return ALLOWED_RATES.stream().anyMatch(allowed -> allowed.compareTo(rate) == 0);
    }
}
