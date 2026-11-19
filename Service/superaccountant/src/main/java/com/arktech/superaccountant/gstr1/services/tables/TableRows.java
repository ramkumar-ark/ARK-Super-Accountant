package com.arktech.superaccountant.gstr1.services.tables;

import com.arktech.superaccountant.gstr1.models.PreparedReturnRow;
import com.arktech.superaccountant.gstr1.services.VoucherView;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class TableRows {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final DateTimeFormatter DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    static final Set<String> NOTE_TYPES = Set.of("credit note", "debit note");

    private TableRows() {
    }

    static PreparedReturnRow row(UUID preparedReturnId, String tableCode, String rowKey,
                                 String sourceVoucherKey, Map<String, Object> payload) {
        PreparedReturnRow row = new PreparedReturnRow();
        row.setId(UUID.randomUUID());
        row.setPreparedReturnId(preparedReturnId);
        row.setTableCode(tableCode);
        row.setRowKey(rowKey);
        row.setSourceVoucherKey(sourceVoucherKey);
        row.setPayload(toJson(payload));
        row.setAmendment(false);
        return row;
    }

    /**
     * Amendment row (T20): isAmendment=true plus the original-return provenance
     * (period, doc key) and the follow-up the row closes.
     */
    static PreparedReturnRow amendmentRow(UUID preparedReturnId, String tableCode, String rowKey,
                                          String sourceVoucherKey, Map<String, Object> payload,
                                          String originalPeriod, String originalDocKey, UUID followUpId) {
        PreparedReturnRow row = row(preparedReturnId, tableCode, rowKey, sourceVoucherKey, payload);
        row.setAmendment(true);
        row.setOriginalPeriod(originalPeriod);
        row.setOriginalDocKey(originalDocKey);
        row.setFollowUpId(followUpId);
        return row;
    }

    static Map<String, Object> payload() {
        return new LinkedHashMap<>();
    }

    /** Replaces a row's payload with the given map (payload() returns a fresh copy). */
    static void savePayload(PreparedReturnRow row, Map<String, Object> payload) {
        row.setPayload(toJson(payload));
    }

    static String date(LocalDate value) {
        return value == null ? null : DATE.format(value);
    }

    static BigDecimal zero() {
        return BigDecimal.ZERO;
    }

    static boolean isNote(String voucherTypeName) {
        return voucherTypeName != null && NOTE_TYPES.contains(voucherTypeName.trim().toLowerCase());
    }

    static boolean hasGstin(VoucherView v) {
        return v.getPartyGstin() != null && !v.getPartyGstin().isBlank();
    }

    static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    static String lower(String s) {
        return s == null ? "" : s.trim().toLowerCase();
    }

    private static String toJson(Map<String, Object> payload) {
        try {
            return MAPPER.writeValueAsString(payload);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize row payload", e);
        }
    }
}
