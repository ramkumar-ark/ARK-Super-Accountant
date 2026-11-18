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
    void registrationTypeChange_doesNotChangeHash() {
        Voucher a = voucher(entry("Test Purchases", "-100", null, "18"));
        Voucher b = voucher(entry("Test Purchases", "-100", null, "18"));
        a.setGstregistrationtype("Regular");
        b.setGstregistrationtype("Unregistered");
        assertThat(VoucherCanonicalizer.contentHash(a))
                .isEqualTo(VoucherCanonicalizer.contentHash(b));
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
    void goldenHash_multiTypeFixture_hashesUnchanged() throws Exception {
        com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
        om.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        com.arktech.superaccountant.tally.models.TallyMessage tm = om.readValue(
                getClass().getResourceAsStream("/fixtures/daybook-multi-type.json"),
                com.arktech.superaccountant.tally.models.TallyMessage.class);

        String[] golden = {
                "0e75438e8ee60341b7c681658100a14c72140b8e8e55bab6ed7d8387ae8722fa",
                "bb26e25e28cc9864d2e400f37a6774b85994952d874e6cd2280404a3deb9d287",
                "bf8324922b2cb98c2e7dd57aafaee54aa2501e8794cb6786f155cbf99cf8458e",
                "e32c56950d8c6bc096b694597221dca3622fffe2c2e0de4b25ff59a0f847b500",
                "2276deebd6ce20b0d4d5d27d84f55fbd24ad7faa6aded9e878d1dc977da75705",
                "6f394b1abd84b79e9a16655164e6cfe111cc339e01e49734ba5035e7c67257c8",
                "bffcb9ff663ea0388c0362b0cc71e670eac1be3443bd6da675896313648033bc"
        };
        for (int i = 0; i < tm.getTallymessage().size(); i++) {
            assertThat(VoucherCanonicalizer.contentHash(tm.getTallymessage().get(i))).isEqualTo(golden[i]);
        }
    }

    @Test
    void parseDate_handlesYyyymmddAndRejectsGarbage() {
        assertThat(VoucherCanonicalizer.parseDate("20250401")).isEqualTo(LocalDate.of(2025, 4, 1));
        assertThat(VoucherCanonicalizer.parseDate(null)).isNull();
        assertThat(VoucherCanonicalizer.parseDate("")).isNull();
        assertThat(VoucherCanonicalizer.parseDate("2025-04-01x")).isNull();
    }
}
