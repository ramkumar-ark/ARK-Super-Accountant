package com.arktech.superaccountant.gst.rules;

import com.arktech.superaccountant.daybook.models.ParsedVoucher;
import com.arktech.superaccountant.gst.models.GstDaybookFinding;
import com.arktech.superaccountant.gst.models.GstFindingType;
import com.arktech.superaccountant.gst.models.VoucherSide;
import com.arktech.superaccountant.masters.models.FindingSeverity;
import com.arktech.superaccountant.masters.models.LedgerCategory;
import com.arktech.superaccountant.masters.models.UploadLedgerSnapshot;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static com.arktech.superaccountant.gst.GstTestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class PartyGstinIssueRuleTest {

    private static final String M = "27AABCU9603R1ZM";
    private static final LocalDate D = LocalDate.of(2026, 4, 10);
    private final PartyGstinIssueRule rule = new PartyGstinIssueRule();

    private static UploadLedgerSnapshot party(String gstin, String regType) {
        UploadLedgerSnapshot s = snapshotGst("Acme", LedgerCategory.CREDITOR, null, null, gstin);
        s.setGstRegistrationType(regType);
        return s;
    }

    private List<GstDaybookFinding> run(UploadLedgerSnapshot p, ParsedVoucher v) {
        return rule.execute(ctx("27AAPFU0939F1ZV", p), List.of(v));
    }

    @Test
    void registered_blankVoucherGstin_fires() {
        List<GstDaybookFinding> f = run(party(M, "Regular"), voucher("Purchase", D, "Acme", null));
        assertEquals(1, f.size());
        assertEquals(GstFindingType.PARTY_GSTIN_ISSUE, f.get(0).getFindingType());
        assertEquals(FindingSeverity.MEDIUM, f.get(0).getSeverity());
        assertEquals(VoucherSide.PURCHASE, f.get(0).getVoucherSide());
        assertEquals("Acme", f.get(0).getLedgerName());
        assertEquals(M, f.get(0).getExpectedValue());
    }

    @Test
    void registered_invalidVoucherGstin_fires() {
        List<GstDaybookFinding> f = run(party(M, "Regular"), voucher("Purchase", D, "Acme", "BADGSTIN"));
        assertEquals(1, f.size());
        assertEquals("BADGSTIN", f.get(0).getActualValue());
    }

    @Test
    void registered_voucherMastersMismatch_fires() {
        List<GstDaybookFinding> f = run(party(M, "Regular"), voucher("Purchase", D, "Acme", "29AABCU9603R1ZM"));
        assertEquals(1, f.size());
        assertEquals(M, f.get(0).getExpectedValue());
        assertEquals("29AABCU9603R1ZM", f.get(0).getActualValue());
    }

    @Test
    void registered_gstinDiffersOnlyByCaseAndSpace_silent() {
        assertTrue(run(party(M, "Regular"), voucher("Purchase", D, "Acme", "27aabcu9603r1zm ")).isEmpty());
    }

    @Test
    void registered_validVoucherGstin_mastersBlank_silent() {
        assertTrue(run(party(null, "Regular"), voucher("Purchase", D, "Acme", M)).isEmpty());
    }

    @Test
    void notRegistered_noGstin_silent() {
        assertTrue(run(party(null, "Unregistered"), voucher("Purchase", D, "Acme", null)).isEmpty());
    }

    @Test
    void typeBlankOnVoucherAndLedger_silent() {
        assertTrue(run(party(M, null), voucher("Purchase", D, "Acme", null)).isEmpty());
        assertTrue(run(party(M, "  "), voucher("Purchase", D, "Acme", "BADGSTIN")).isEmpty());
    }

    @Test
    void voucherTypeTakesPrecedenceOverLedgerType() {
        ParsedVoucher unregOnVoucher = voucher("Purchase", D, "Acme", null);
        unregOnVoucher.setPartyGstRegistrationType("Unregistered");
        assertTrue(run(party(M, "Regular"), unregOnVoucher).isEmpty());

        ParsedVoucher regOnVoucher = voucher("Purchase", D, "Acme", null);
        regOnVoucher.setPartyGstRegistrationType("Regular");
        assertEquals(1, run(party(null, "Unregistered"), regOnVoucher).size());
    }

    @Test
    void voucherTypeBlank_fallsBackToLedgerType() {
        ParsedVoucher v = voucher("Purchase", D, "Acme", null);
        v.setPartyGstRegistrationType("");
        assertEquals(1, run(party(M, "Regular"), v).size());
    }

    @Test
    void salesSideAndCancelled_silent() {
        ParsedVoucher sales = voucher("Sales", D, "Acme", null);
        ParsedVoucher cancelled = voucher("Purchase", D, "Acme", null);
        cancelled.setCancelled(true);
        assertTrue(rule.execute(ctx("27AAPFU0939F1ZV", party(M, "Regular")), List.of(sales, cancelled)).isEmpty());
    }
}
