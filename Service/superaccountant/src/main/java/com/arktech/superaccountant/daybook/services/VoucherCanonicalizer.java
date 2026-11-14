package com.arktech.superaccountant.daybook.services;

import com.arktech.superaccountant.tally.models.GstRateDetail;
import com.arktech.superaccountant.tally.models.LedgerEntry;
import com.arktech.superaccountant.tally.models.Voucher;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Canonical form and content hash of a Tally voucher.
 *
 * The canonical form is fixed here because the analysis evidence hash (worker R)
 * depends on it: a narration-only edit must NOT change the hash, while any change
 * to the header identity fields, entry amounts or GST rates must.
 */
public final class VoucherCanonicalizer {

    private VoucherCanonicalizer() {
    }

    /**
     * Parses a Tally YYYYMMDD date string to LocalDate.
     * Returns null if the input is null, blank or unparseable.
     */
    public static LocalDate parseDate(String tallyDate) {
        if (tallyDate == null || tallyDate.isBlank() || tallyDate.length() != 8) return null;
        try {
            return LocalDate.parse(tallyDate, DateTimeFormatter.BASIC_ISO_DATE);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * SHA-256 hex (64 lowercase chars) of the canonical voucher form.
     * Excludes narration, so a narration-only edit does not change the hash.
     */
    public static String contentHash(Voucher v) {
        String header = String.join("|",
                nullToEmpty(v.getVouchertypename()),
                nullToEmpty(v.getVouchernumber()),
                isoOrNullToEmpty(parseDate(v.getDate())),
                nullToEmpty(v.getPartyledgername()),
                nullToEmpty(v.getPartygstin()),
                String.valueOf(v.isIscancelled()),
                String.valueOf(v.isIsinvoice()),
                String.valueOf(v.isIsreversechargeapplicable()));

        List<String> entries = new ArrayList<>();
        for (LedgerEntry le : v.getAllLedgerEntriesCombined()) {
            entries.add(entryLine(le));
        }
        entries.sort(Comparator.naturalOrder());

        String canonical = header + "\n" + String.join("\n", entries);
        return sha256Hex(canonical.getBytes(StandardCharsets.UTF_8));
    }

    private static String entryLine(LedgerEntry le) {
        BigDecimal rateSum = null;
        if (le.getRatedetails() != null) {
            for (GstRateDetail detail : le.getRatedetails()) {
                BigDecimal rate = parseAmount(detail == null ? null : detail.getGstrate());
                if (rateSum == null) {
                    rateSum = rate;
                } else {
                    rateSum = rateSum.add(rate);
                }
            }
        }
        return String.join("|",
                nullToEmpty(le.getLedgername()),
                parseAmount(le.getAmount()).setScale(2, RoundingMode.HALF_UP).toPlainString(),
                nullToEmpty(le.getGstclass()),
                rateSum != null ? rateSum.setScale(2, RoundingMode.HALF_UP).toPlainString() : "");
    }

    /**
     * Same parsing rules as DayBookParserService.parseAmount.
     */
    static BigDecimal parseAmount(String amount) {
        if (amount == null || amount.isBlank()) return BigDecimal.ZERO;
        try {
            return new BigDecimal(amount.trim().replace(",", ""));
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String isoOrNullToEmpty(LocalDate d) {
        return d == null ? "" : d.toString();
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
