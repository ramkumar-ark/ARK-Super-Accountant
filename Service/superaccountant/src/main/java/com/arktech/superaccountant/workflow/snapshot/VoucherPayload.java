package com.arktech.superaccountant.workflow.snapshot;

import com.arktech.superaccountant.daybook.models.ParsedVoucher;
import com.arktech.superaccountant.daybook.models.ParsedVoucherEntry;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record VoucherPayload(String type, String number, LocalDate date, String party, String partyGstin,
                             List<Entry> entries) {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public record Entry(String ledger, BigDecimal amount, String gstClass, BigDecimal gstRate, String taxability) {
    }

    private record Json(String type, String number, String date, String party, String partyGstin,
                        List<EntryJson> entries) {
        private record EntryJson(String ledger, String amount, String gstClass, String gstRate, String taxability) {
        }
    }

    public static VoucherPayload of(ParsedVoucher v) {
        List<Entry> entries = v.getEntries() == null ? List.of() : v.getEntries().stream()
                .map(VoucherPayload::entryOf)
                .toList();
        return new VoucherPayload(v.getVoucherTypeName(), v.getVoucherNumber(), v.getVoucherDate(),
                v.getPartyLedgerName(), v.getPartyGstin(), entries);
    }

    private static Entry entryOf(ParsedVoucherEntry e) {
        return new Entry(e.getLedgerName(), e.getAmount(), e.getGstClass(), e.getGstRate(), e.getGstTaxability());
    }

    public String toJson() {
        Json json = new Json(type, number, date == null ? null : date.toString(), party, partyGstin,
                entries == null ? List.of() : entries.stream()
                        .map(e -> new Json.EntryJson(e.ledger(), str(e.amount()), e.gstClass(), str(e.gstRate()),
                                e.taxability()))
                        .toList());
        try {
            return MAPPER.writeValueAsString(json);
        } catch (Exception e) {
            throw new IllegalStateException("Could not serialize voucher payload", e);
        }
    }

    private static String str(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }

    public static VoucherPayload fromJson(String json) {
        try {
            Json parsed = MAPPER.readValue(json, Json.class);
            List<Entry> entries = parsed.entries() == null ? List.of() : parsed.entries().stream()
                    .map(e -> new Entry(e.ledger(), e.amount() == null ? null : new BigDecimal(e.amount()),
                            e.gstClass(), e.gstRate() == null ? null : new BigDecimal(e.gstRate()), e.taxability()))
                    .toList();
            return new VoucherPayload(parsed.type(), parsed.number(),
                    parsed.date() == null ? null : LocalDate.parse(parsed.date()),
                    parsed.party(), parsed.partyGstin(), entries);
        } catch (Exception e) {
            throw new IllegalStateException("Could not deserialize voucher payload", e);
        }
    }
}
