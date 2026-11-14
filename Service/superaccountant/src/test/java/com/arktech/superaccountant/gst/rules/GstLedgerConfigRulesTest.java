package com.arktech.superaccountant.gst.rules;

import com.arktech.superaccountant.daybook.models.ParsedVoucher;
import com.arktech.superaccountant.gst.models.GstDaybookFinding;
import com.arktech.superaccountant.gst.models.GstFindingType;
import com.arktech.superaccountant.gst.models.VoucherSide;
import com.arktech.superaccountant.masters.models.FindingSeverity;
import com.arktech.superaccountant.masters.models.GstApplicabilityType;
import com.arktech.superaccountant.masters.models.LedgerCategory;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static com.arktech.superaccountant.gst.GstTestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class GstLedgerConfigRulesTest {

    private static final LocalDate D = LocalDate.of(2026, 4, 10);
    private static final String ORG = "27AAPFU0939F1ZV";
    private final GstOnNonGstLedgerRule rule5 = new GstOnNonGstLedgerRule();
    private final GstWithoutGstConfigRule rule6 = new GstWithoutGstConfigRule();
    private final InputOutputDirectionRule rule7 = new InputOutputDirectionRule();

    // --- rule 5 ---
    @Test void rule5_gstAmountParkedInNonGstLedger() {
        GstRuleContext ctx = ctx(ORG, snapshot("CGST Reimbursement", LedgerCategory.OTHER));
        ParsedVoucher v = voucher("Journal", D, null, null);
        entry(v, "CGST Reimbursement", "-500.00");
        List<GstDaybookFinding> f = rule5.execute(ctx, List.of(v));
        assertEquals(1, f.size());
        assertEquals(GstFindingType.GST_ON_NON_GST_LEDGER, f.get(0).getFindingType());
        assertEquals(FindingSeverity.MEDIUM, f.get(0).getSeverity());
        assertEquals("CGST Reimbursement", f.get(0).getLedgerName());
        assertEquals(VoucherSide.OTHER, f.get(0).getVoucherSide());
    }
    @Test void rule5_silentWhenCategoryIsGst() {
        GstRuleContext ctx = ctx(ORG, snapshot("Input CGST", LedgerCategory.GST));
        ParsedVoucher v = voucher("Purchase", D, "Acme", null);
        entry(v, "Input CGST", "-900.00");
        assertTrue(rule5.execute(ctx, List.of(v)).isEmpty());
    }
    @Test void rule5_silentOnZeroAmount() {
        GstRuleContext ctx = ctx(ORG, snapshot("IGST Refund", LedgerCategory.OTHER));
        ParsedVoucher v = voucher("Journal", D, null, null);
        entry(v, "IGST Refund", "0.00");
        assertTrue(rule5.execute(ctx, List.of(v)).isEmpty());
    }
    @Test void rule5_silentWhenLedgerMissingFromMastersOrCancelled() {
        ParsedVoucher missing = voucher("Journal", D, null, null);
        entry(missing, "SGST Adjustment", "-100.00");
        ParsedVoucher cancelled = voucher("Journal", D, null, null);
        cancelled.setCancelled(true);
        entry(cancelled, "CGST Reimbursement", "-100.00");
        GstRuleContext ctx = ctx(ORG, snapshot("CGST Reimbursement", LedgerCategory.OTHER));
        assertTrue(rule5.execute(ctx, List.of(missing, cancelled)).isEmpty());
    }

    // --- rule 6 ---
    private GstRuleContext rule6Ctx(Boolean flag, GstApplicabilityType type) {
        return ctx(ORG, snapshotGst("Misc Expense", LedgerCategory.EXPENSE, flag, type, null),
                snapshot("Input CGST", LedgerCategory.GST), snapshot("Input SGST", LedgerCategory.GST),
                snapshot("Acme", LedgerCategory.CREDITOR));
    }
    private ParsedVoucher gstPurchase() {
        ParsedVoucher v = voucher("Purchase", D, "Acme", null);
        entry(v, "Misc Expense", "10000.00");
        entry(v, "Input CGST", "900.00");
        entry(v, "Input SGST", "900.00");
        entry(v, "Acme", "-11800.00").setPartyLedger(true);
        return v;
    }
    @Test void rule6_firesWhenFlagNullOrFalse() {
        assertEquals(1, rule6.execute(rule6Ctx(null, null), List.of(gstPurchase())).size());
        GstDaybookFinding f = rule6.execute(rule6Ctx(false, GstApplicabilityType.TAXABLE), List.of(gstPurchase())).get(0);
        assertEquals(GstFindingType.GST_WITHOUT_GST_CONFIG, f.getFindingType());
        assertEquals(FindingSeverity.MEDIUM, f.getSeverity());
        assertEquals("Misc Expense", f.getLedgerName());
        assertEquals(VoucherSide.PURCHASE, f.getVoucherSide());
    }
    @Test void rule6_firesForExemptNonGstNotApplicable() {
        for (GstApplicabilityType t : List.of(GstApplicabilityType.EXEMPT, GstApplicabilityType.NON_GST,
                GstApplicabilityType.NOT_APPLICABLE)) {
            assertEquals(1, rule6.execute(rule6Ctx(true, t), List.of(gstPurchase())).size(), t.name());
        }
    }
    @Test void rule6_silentWhenTaxableConfigured() {
        assertTrue(rule6.execute(rule6Ctx(true, GstApplicabilityType.TAXABLE), List.of(gstPurchase())).isEmpty());
    }
    @Test void rule6_silentForRcmConfiguredExpense() {
        assertTrue(rule6.execute(rule6Ctx(true, GstApplicabilityType.RCM), List.of(gstPurchase())).isEmpty());
    }
    @Test void rule6_silentWithoutNonZeroGstEntries() {
        ParsedVoucher v = voucher("Purchase", D, "Acme", null);
        entry(v, "Misc Expense", "10000.00");
        entry(v, "Input CGST", "0.00");
        assertTrue(rule6.execute(rule6Ctx(null, null), List.of(v)).isEmpty());
    }
    @Test void rule6_oneFindingPerLedgerPerVoucher() {
        ParsedVoucher v = gstPurchase();
        entry(v, "Misc Expense", "500.00");
        assertEquals(1, rule6.execute(rule6Ctx(null, null), List.of(v)).size());
    }

    // --- rule 7 ---
    @Test void rule7_outputLedgerOnPurchaseAndDebitNote() {
        GstRuleContext ctx = ctx(ORG, snapshot("Output CGST", LedgerCategory.GST));
        ParsedVoucher p = voucher("Purchase", D, "Acme", null);
        entry(p, "Output CGST", "900.00");
        ParsedVoucher dn = voucher("Debit Note", D, "Acme", null);
        entry(dn, "Output CGST", "-90.00");
        List<GstDaybookFinding> f = rule7.execute(ctx, List.of(p, dn));
        assertEquals(2, f.size());
        assertTrue(f.stream().allMatch(x -> x.getFindingType() == GstFindingType.INPUT_OUTPUT_DIRECTION
                && x.getSeverity() == FindingSeverity.MEDIUM && x.getVoucherSide() == VoucherSide.PURCHASE));
    }
    @Test void rule7_inputLedgerOnSales() {
        GstRuleContext ctx = ctx(ORG, snapshot("Input CGST", LedgerCategory.GST));
        ParsedVoucher v = voucher("Sales", D, "Customer", null);
        entry(v, "Input CGST", "-900.00");
        List<GstDaybookFinding> f = rule7.execute(ctx, List.of(v));
        assertEquals(1, f.size());
        assertEquals(VoucherSide.SALES, f.get(0).getVoucherSide());
    }
    @Test void rule7_silentOnCorrectDirectionAndOnJournal() {
        GstRuleContext ctx = ctx(ORG, snapshot("Input CGST", LedgerCategory.GST),
                snapshot("Output CGST", LedgerCategory.GST));
        ParsedVoucher p = voucher("Purchase", D, "Acme", null);
        entry(p, "Input CGST", "900.00");
        ParsedVoucher j = voucher("Journal", D, null, null);
        entry(j, "Output CGST", "900.00");
        assertTrue(rule7.execute(ctx, List.of(p, j)).isEmpty());
    }
}
