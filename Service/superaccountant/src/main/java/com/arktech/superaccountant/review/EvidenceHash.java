package com.arktech.superaccountant.review;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;

/**
 * Stable hash of the evidence a finding was computed from. Two findings with the same key and
 * the same hash are the same problem; a different hash means the evidence changed and the review
 * does not carry over.
 */
public final class EvidenceHash {

    private EvidenceHash() {
    }

    /** The identity of the voucher a finding is about: the Tally GUID when present, else type|number|date. */
    public static String voucherIdentity(String guid, String voucherType, String voucherNumber, LocalDate date) {
        if (guid != null && !guid.isBlank()) {
            return guid;
        }
        return orEmpty(voucherType) + "|" + orEmpty(voucherNumber) + "|"
                + (date == null ? "" : date.toString());
    }

    /** sha256 hex of the five fields joined by U+001F; nulls become "". */
    public static String of(String findingType, String voucherIdentity, String expected, String actual,
                            String voucherContentHash) {
        String joined = orEmpty(findingType) + '\u001F' + orEmpty(voucherIdentity) + '\u001F'
                + orEmpty(expected) + '\u001F' + orEmpty(actual) + '\u001F' + orEmpty(voucherContentHash);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(joined.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }

    private static String orEmpty(String s) {
        return s == null ? "" : s;
    }
}
