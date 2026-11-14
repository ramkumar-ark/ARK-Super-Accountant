package com.arktech.superaccountant.daybook;

import com.arktech.superaccountant.daybook.services.VoucherCanonicalizer;
import com.arktech.superaccountant.tally.models.GstRateDetail;
import com.arktech.superaccountant.tally.models.LedgerEntry;
import com.arktech.superaccountant.tally.models.Voucher;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class VoucherCanonicalizerTest {

    private Voucher voucher(LedgerEntry... entries) {
        Voucher v = new Voucher();
        v.setVouchertypename("Purchase");
        v.setVouchernumber("101");
        v.setDate("20250401");
        v.setPartyledgername("Cement Supplier");
        v.setPartygstin("29ABCDE1234F1Z5");
        v.setIsinvoice(true);
        v.setIsreversechargeapplicable(false);
        v.setIscancelled(false);
        v.setAllLedgerEntries(List.of(entries));
        v.setLedgerEntries(List.of());
        return v;
    }

    private LedgerEntry entry(String ledger, String amount, String gstClass, String... rates) {
        LedgerEntry le = new LedgerEntry();
        le.setLedgername(ledger);
        le.setAmount(amount);
        le.setGstclass(gstClass);
        java.util.List<GstRateDetail> ratedetails = new java.util.ArrayList<>();
        for (String rate : rates) {
            GstRateDetail d = new GstRateDetail();
            d.setGstrate(rate);
            ratedetails.add(d);
        }
        le.setRatedetails(ratedetails);
        return le;
    }

    @Test
    void sameVoucherWithEntriesReordered_hasSameHash() {
        Voucher a = voucher(entry("Input CGST @9%", "-900", null, "9"),
                entry("Test Purchases", "-10000", null, "9"));
        Voucher b = voucher(entry("Test Purchases", "-10000", null, "9"),
                entry("Input CGST @9%", "-900", null, "9"));
        assertThat(VoucherCanonicalizer.contentHash(a))
                .isEqualTo(VoucherCanonicalizer.contentHash(b));
    }

    @Test
    void entryAmountChange_changesHash() {
        Voucher a = voucher(entry("Test Purchases", "-100.00", null, "9"));
        Voucher b = voucher(entry("Test Purchases", "-100.50", null, "9"));
        assertThat(VoucherCanonicalizer.contentHash(a))
                .isNotEqualTo(VoucherCanonicalizer.contentHash(b));
    }

    @Test
    void narrationChange_doesNotChangeHash() {
        Voucher a = voucher(entry("Test Purchases", "-100.00", null, "9"));
        Voucher b = voucher(entry("Test Purchases", "-100.00", null, "9"));
        a.setNarration("original narration");
        b.setNarration("edited narration");
        assertThat(VoucherCanonicalizer.contentHash(a))
                .isEqualTo(VoucherCanonicalizer.contentHash(b));
    }

    @Test
    void amountScaleIsNormalized() {
        Voucher a = voucher(entry("Test Purchases", "100", null, "9"));
        Voucher b = voucher(entry("Test Purchases", "100.00", null, "9"));
        assertThat(VoucherCanonicalizer.contentHash(a))
                .isEqualTo(VoucherCanonicalizer.contentHash(b));
    }

    @Test
    void hashIs64LowercaseHex() {
        String hash = VoucherCanonicalizer.contentHash(voucher(entry("Test Purchases", "-100", null, "9")));
        assertThat(hash).matches("[0-9a-f]{64}");
    }

    @Test
    void parseDate_handlesYyyymmddAndRejectsGarbage() {
        assertThat(VoucherCanonicalizer.parseDate("20250401")).isEqualTo(LocalDate.of(2025, 4, 1));
        assertThat(VoucherCanonicalizer.parseDate(null)).isNull();
        assertThat(VoucherCanonicalizer.parseDate("")).isNull();
        assertThat(VoucherCanonicalizer.parseDate("2025-04-01x")).isNull();
    }
}
