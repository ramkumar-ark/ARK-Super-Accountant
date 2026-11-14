package com.arktech.superaccountant.gst.rules;

import com.arktech.superaccountant.daybook.models.ParsedVoucher;
import com.arktech.superaccountant.daybook.models.ParsedVoucherEntry;
import com.arktech.superaccountant.gst.models.GstFindingType;
import com.arktech.superaccountant.gst.models.GstDaybookFinding;
import com.arktech.superaccountant.gst.models.VoucherSide;
import com.arktech.superaccountant.masters.models.FindingSeverity;
import com.arktech.superaccountant.masters.models.LedgerCategory;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static com.arktech.superaccountant.gst.GstTestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class GstRuleSupportTest {

    @Test
    void classifiesGstHeadFromLedgerName() {
        assertEquals("CGST", GstRuleSupport.gstHead("Input CGST 9%"));
        assertEquals("SGST", GstRuleSupport.gstHead("output sgst"));
        assertEquals("IGST", GstRuleSupport.gstHead("IGST Payable"));
        assertNull(GstRuleSupport.gstHead("Rent Expense"));
        assertNull(GstRuleSupport.gstHead(null));
    }

    @Test
    void validatesGstinFormat() {
        assertTrue(GstRuleSupport.isValidGstin("27AAPFU0939F1ZV"));
        assertFalse(GstRuleSupport.isValidGstin("27AAPFU0939F1Z"));  // 14 chars
        assertFalse(GstRuleSupport.isValidGstin(null));
        assertEquals("27", GstRuleSupport.stateCode("27AAPFU0939F1ZV"));
        assertNull(GstRuleSupport.stateCode("bad"));
    }

    @Test
    void voucherSideDetection() {
        assertTrue(GstRuleSupport.isPurchaseSide("Purchase"));
        assertTrue(GstRuleSupport.isPurchaseSide("Debit Note"));
        assertTrue(GstRuleSupport.isSalesSide("Sales"));
        assertTrue(GstRuleSupport.isSalesSide("Credit Note"));
        assertFalse(GstRuleSupport.isPurchaseSide("Journal"));
        assertEquals(VoucherSide.SALES, GstRuleSupport.side(voucher("Sales", LocalDate.of(2026, 4, 1), "A", null)));
        assertEquals(VoucherSide.PURCHASE, GstRuleSupport.side(voucher("Debit Note", LocalDate.of(2026, 4, 1), "A", null)));
        assertEquals(VoucherSide.OTHER, GstRuleSupport.side(voucher("Journal", LocalDate.of(2026, 4, 1), "A", null)));
    }

    @Test
    void gstEntriesRequireGstCategorySnapshotAndHead() {
        ParsedVoucher v = voucher("Purchase", LocalDate.of(2026, 4, 10), "Acme", null);
        entry(v, "Input CGST", "-900.00");
        entry(v, "Freight CGST Reimb", "-50.00"); // name matches head but snapshot category OTHER
        entry(v, "Rent", "10000.00");

        GstRuleContext ctx = ctx("27AAPFU0939F1ZV",
                snapshot("Input CGST", LedgerCategory.GST),
                snapshot("Freight CGST Reimb", LedgerCategory.OTHER),
                snapshot("Rent", LedgerCategory.EXPENSE));

        List<ParsedVoucherEntry> gst = GstRuleSupport.gstEntries(v, ctx);
        assertEquals(1, gst.size());
        assertEquals("Input CGST", gst.get(0).getLedgerName());
    }

    @Test
    void headTotalsSumAbsoluteAmountsPerHead() {
        ParsedVoucher v = voucher("Purchase", LocalDate.of(2026, 4, 10), "Acme", null);
        ParsedVoucherEntry cgst = entry(v, "Input CGST", "-900.00");
        ParsedVoucherEntry sgst = entry(v, "Input SGST", "-900.00");
        Map<String, BigDecimal> totals = GstRuleSupport.headTotals(List.of(cgst, sgst));
        assertEquals(0, new BigDecimal("900.00").compareTo(totals.get("CGST")));
        assertEquals(0, new BigDecimal("900.00").compareTo(totals.get("SGST")));
        assertEquals(0, BigDecimal.ZERO.compareTo(totals.getOrDefault("IGST", BigDecimal.ZERO)));
    }

    @Test
    void interStateFromPartyVsOrgStateCode() {
        GstRuleContext ctx = ctx("27AAPFU0939F1ZV");
        ParsedVoucher intra = voucher("Purchase", LocalDate.of(2026, 4, 1), "A", "27AABCU9603R1ZM");
        ParsedVoucher inter = voucher("Purchase", LocalDate.of(2026, 4, 1), "B", "29AABCU9603R1ZM");
        ParsedVoucher unknown = voucher("Purchase", LocalDate.of(2026, 4, 1), "C", null);
        assertEquals(Boolean.FALSE, GstRuleSupport.interState(ctx, intra));
        assertEquals(Boolean.TRUE, GstRuleSupport.interState(ctx, inter));
        assertNull(GstRuleSupport.interState(ctx, unknown));
    }

    @Test
    void gstEntriesTreatNullCategoryAsOther() {
        ParsedVoucher v = voucher("Purchase", LocalDate.of(2026, 4, 10), "Acme", null);
        entry(v, "CGST Output", "-900.00");

        GstRuleContext ctx = ctx("27AAPFU0939F1ZV",
                snapshot("CGST Output", null)); // pre-Ga snapshot: category null

        List<ParsedVoucherEntry> gst = GstRuleSupport.gstEntries(v, ctx);
        assertEquals(0, gst.size());
        assertEquals(LedgerCategory.OTHER, ctx.category("CGST Output"));
    }

    @Test
    void snapshotLookupTrimsAndIgnoresCase() {
        GstRuleContext ctx = ctx("27AAPFU0939F1ZV",
                snapshot("cgst input", LedgerCategory.GST));
        assertTrue(ctx.snapshot("  CGST Input ").isPresent());
    }

    @Test
    void interStateNullWhenOrgGstinMissing() {
        GstRuleContext ctx = ctx(null);
        ParsedVoucher v = voucher("Purchase", LocalDate.of(2026, 4, 1), "A", "27AABCU9603R1ZM");
        assertNull(GstRuleSupport.interState(ctx, v));
    }

    @Test
    void findingCarriesWorkflowUploadAndSide() {
        GstRuleContext ctx = ctx("27AAPFU0939F1ZV");
        ParsedVoucher v = voucher("Sales", LocalDate.of(2026, 4, 1), "Acme", null);

        GstDaybookFinding f = GstRuleSupport.finding(ctx, v, GstFindingType.GST_NOT_CHARGED,
                FindingSeverity.HIGH, "Rent", "expected", "actual", "message");

        assertEquals(ctx.workflowId(), f.getWorkflowId());
        assertEquals(ctx.uploadJobId(), f.getUploadJobId());
        assertEquals(VoucherSide.SALES, f.getVoucherSide());
        assertEquals(ctx.organizationId(), f.getOrganizationId());
    }
}
