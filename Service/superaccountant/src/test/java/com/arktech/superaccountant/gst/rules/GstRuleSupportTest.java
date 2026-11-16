package com.arktech.superaccountant.gst.rules;

import com.arktech.superaccountant.daybook.models.ParsedVoucher;
import com.arktech.superaccountant.daybook.models.ParsedVoucherEntry;
import com.arktech.superaccountant.gst.mapping.GstHead;
import com.arktech.superaccountant.gst.mapping.TaxLedgerMapping;
import com.arktech.superaccountant.gst.mapping.TaxLedgerRole;
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
    void headComesFromTheSavedMapping() {
        GstRuleContext ctx = ctx("27AAPFU0939F1ZV",
                snapshot("Input CGST", LedgerCategory.GST));
        assertEquals(GstHead.CGST, GstRuleSupport.gstHead(ctx, "Input CGST"));
        assertNull(GstRuleSupport.gstHead(ctx, "Rent Expense"));
        assertNull(GstRuleSupport.gstHead(ctx, null));
    }

    @Test
    void mappingWinsOverLedgerName() {
        GstRuleContext ctx = ctxWithTaxLedgers("27AAPFU0939F1ZV",
                Map.of("output cgst", mapping("Output CGST", TaxLedgerRole.INPUT, GstHead.CGST)),
                snapshot("Output CGST", LedgerCategory.GST));

        assertEquals(TaxLedgerRole.INPUT, ctx.role("Output CGST"));

        ParsedVoucher v = voucher("Sales", LocalDate.of(2026, 4, 10), "Acme", "27AABCU9603R1ZM");
        entry(v, "Output CGST", "900.00");
        List<com.arktech.superaccountant.gst.models.GstDaybookFinding> findings =
                new InputOutputDirectionRule().execute(ctx, List.of(v));
        assertEquals(1, findings.size());
        assertEquals(com.arktech.superaccountant.gst.models.GstFindingType.INPUT_OUTPUT_DIRECTION,
                findings.get(0).getFindingType());
    }

    @Test
    void unmappedGstCategoryLedgerIsNotATaxEntry() {
        ParsedVoucher v = voucher("Purchase", LocalDate.of(2026, 4, 10), "Acme", null);
        entry(v, "Input CGST", "-900.00");
        GstRuleContext ctx = ctxWithTaxLedgers("27AAPFU0939F1ZV", Map.of(),
                snapshot("Input CGST", LedgerCategory.GST));
        assertTrue(GstRuleSupport.gstEntries(v, ctx).isEmpty());
    }

    @Test
    void notTaxRoleIsExcluded() {
        ParsedVoucher v = voucher("Purchase", LocalDate.of(2026, 4, 10), "Acme", null);
        entry(v, "Input CGST", "-900.00");
        GstRuleContext ctx = ctxWithTaxLedgers("27AAPFU0939F1ZV",
                Map.of("input cgst", mapping("Input CGST", TaxLedgerRole.NOT_TAX, null)),
                snapshot("Input CGST", LedgerCategory.GST));
        assertTrue(GstRuleSupport.gstEntries(v, ctx).isEmpty());
        assertNull(ctx.head("Input CGST"));
        assertEquals(TaxLedgerRole.NOT_TAX, ctx.role("Input CGST"));
    }

    @Test
    void headTotalsIncludeCessButRateHeadTotalsDoNot() {
        TaxLedgerMapping cess = mapping("Compensation Cess", TaxLedgerRole.OUTPUT, GstHead.CESS);
        TaxLedgerMapping cgst = mapping("Output CGST", TaxLedgerRole.OUTPUT, GstHead.CGST);
        TaxLedgerMapping sgst = mapping("Output SGST", TaxLedgerRole.OUTPUT, GstHead.SGST);
        GstRuleContext ctx = ctxWithTaxLedgers("27AAPFU0939F1ZV",
                Map.of("compensation cess", cess, "output cgst", cgst, "output sgst", sgst),
                snapshot("Compensation Cess", LedgerCategory.GST),
                snapshot("Output CGST", LedgerCategory.GST),
                snapshot("Output SGST", LedgerCategory.GST));

        ParsedVoucher v = voucher("Sales", LocalDate.of(2026, 4, 10), "Acme", null);
        entry(v, "Output CGST", "90.00");
        entry(v, "Output SGST", "90.00");
        entry(v, "Compensation Cess", "12.00");

        Map<GstHead, BigDecimal> totals = GstRuleSupport.headTotals(ctx, GstRuleSupport.gstEntries(v, ctx));
        assertEquals(0, new BigDecimal("90").compareTo(totals.get(GstHead.CGST)));
        assertEquals(0, new BigDecimal("90").compareTo(totals.get(GstHead.SGST)));
        assertEquals(0, new BigDecimal("12").compareTo(totals.get(GstHead.CESS)));

        Map<GstHead, BigDecimal> rateTotals = GstRuleSupport.rateHeadTotals(ctx, GstRuleSupport.gstEntries(v, ctx));
        assertFalse(rateTotals.containsKey(GstHead.CESS));
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
        GstRuleContext ctx = ctx("27AAPFU0939F1ZV",
                snapshot("Input CGST", LedgerCategory.GST),
                snapshot("Input SGST", LedgerCategory.GST));
        Map<GstHead, BigDecimal> totals = GstRuleSupport.headTotals(ctx, GstRuleSupport.gstEntries(v, ctx));
        assertEquals(0, new BigDecimal("900.00").compareTo(totals.get(GstHead.CGST)));
        assertEquals(0, new BigDecimal("900.00").compareTo(totals.get(GstHead.SGST)));
        assertEquals(0, BigDecimal.ZERO.compareTo(totals.getOrDefault(GstHead.IGST, BigDecimal.ZERO)));
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
    void gstEntriesFollowMappingNotCategory() {
        ParsedVoucher v = voucher("Purchase", LocalDate.of(2026, 4, 10), "Acme", null);
        entry(v, "CGST Output", "-900.00");

        GstRuleContext ctx = ctx("27AAPFU0939F1ZV",
                snapshot("CGST Output", null)); // pre-Ga snapshot: category null

        // gstEntries is mapping-driven now; the null-category snapshot still reads as OTHER
        List<ParsedVoucherEntry> gst = GstRuleSupport.gstEntries(v, ctx);
        assertEquals(1, gst.size());
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
