package com.arktech.superaccountant.workflow.snapshot;

import com.arktech.superaccountant.daybook.models.ParsedVoucher;
import com.arktech.superaccountant.daybook.models.ParsedVoucherEntry;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class VoucherKeysTest {

    private ParsedVoucher voucher(String guid, String tallyMasterId, String type, String number,
                                  LocalDate date, String contentHash) {
        ParsedVoucher v = new ParsedVoucher();
        v.setVoucherGuid(guid);
        v.setTallyMasterId(tallyMasterId);
        v.setVoucherTypeName(type);
        v.setVoucherNumber(number);
        v.setVoucherDate(date);
        v.setContentHash(contentHash);
        return v;
    }

    @Test
    void of_prefersGuidThenMasterIdThenNaturalKey() {
        assertThat(VoucherKeys.of("g-1", "77", "Sales", "SB/1", LocalDate.of(2025, 4, 15))).isEqualTo("G:g-1");
        assertThat(VoucherKeys.of(" ", "77", "Sales", "SB/1", LocalDate.of(2025, 4, 15))).isEqualTo("M:77");
        assertThat(VoucherKeys.of(null, null, "Sales", "SB/1", LocalDate.of(2025, 4, 15)))
                .isEqualTo("N:Sales|SB/1|2025-04-15");
    }

    @Test
    void keyAll_duplicateNaturalKeys_suffixedInContentHashOrder() {
        ParsedVoucher vB = voucher(null, null, "Sales", "SB/1", LocalDate.of(2025, 4, 15), "bbb");
        ParsedVoucher vA = voucher(null, null, "Sales", "SB/1", LocalDate.of(2025, 4, 15), "aaa");
        Map<String, ParsedVoucher> keyed = VoucherKeys.keyAll(List.of(vB, vA));
        assertThat(keyed.keySet()).containsExactlyInAnyOrder("N:Sales|SB/1|2025-04-15#1", "N:Sales|SB/1|2025-04-15#2");
        assertThat(keyed.get("N:Sales|SB/1|2025-04-15#1")).isSameAs(vA);
        assertThat(keyed.get("N:Sales|SB/1|2025-04-15#2")).isSameAs(vB);
    }

    @Test
    void payload_roundTripsThroughJson() {
        ParsedVoucher v = voucher("g-1", null, "Sales", "SB/1", LocalDate.of(2025, 4, 15), "hash");
        v.setPartyLedgerName("Client ABC");
        v.setPartyGstin("29ABCDE1234F1Z5");
        ParsedVoucherEntry e = new ParsedVoucherEntry();
        e.setLedgerName("Sales Account");
        e.setAmount(new BigDecimal("100.00"));
        e.setGstClass("GST");
        e.setGstRate(new BigDecimal("9.00"));
        e.setGstTaxability("Taxable");
        v.setEntries(List.of(e));

        VoucherPayload payload = VoucherPayload.of(v);
        VoucherPayload back = VoucherPayload.fromJson(payload.toJson());
        assertThat(back).isEqualTo(payload);
    }
}
