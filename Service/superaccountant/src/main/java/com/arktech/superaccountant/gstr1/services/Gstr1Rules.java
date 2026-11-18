package com.arktech.superaccountant.gstr1.services;

import java.math.BigDecimal;
import java.time.YearMonth;
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

    private Gstr1Rules() {
    }

    public static BigDecimal b2clThreshold(YearMonth period) {
        return B2CL_THRESHOLDS.getOrDefault(period, DEFAULT_B2CL_THRESHOLD);
    }

    public static boolean isAllowedRate(BigDecimal rate) {
        return rate != null && ALLOWED_RATES.contains(rate);
    }
}
