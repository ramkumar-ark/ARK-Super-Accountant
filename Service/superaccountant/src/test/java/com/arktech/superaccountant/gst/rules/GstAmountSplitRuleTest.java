package com.arktech.superaccountant.gst.rules;

import com.arktech.superaccountant.daybook.models.ParsedVoucher;
import com.arktech.superaccountant.gst.models.GstDaybookFinding;
import com.arktech.superaccountant.gst.models.GstFindingType;
import com.arktech.superaccountant.gst.models.VoucherSide;
import com.arktech.superaccountant.masters.models.FindingSeverity;
import com.arktech.superaccountant.masters.models.LedgerCategory;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static com.arktech.superaccountant.gst.GstTestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class GstAmountSplitRuleTest {

    private final GstAmountSplitRule rule = new GstAmountSplitRule();
    private static final LocalDate D = LocalDate.of(2026, 4, 10);

    private GstRuleContext context() {
        return ctx("27AAPFU0939F1ZV",
                snapshot("Input CGST", LedgerCategory.GST),
                snapshot("Input SGST", LedgerCategory.GST),
                snapshot("Input IGST", LedgerCategory.GST));
    }

    @Test
    void firesOnMixedHeads() {
        ParsedVoucher v = voucher("Purchase", D, "Acme", null);
        entry(v, "Input IGST", "-1800.00");
        entry(v, "Input CGST", "-900.00");

        List<GstDaybookFinding> findings = rule.execute(context(), List.of(v));
        assertEquals(1, findings.size());
        assertEquals(GstFindingType.AMOUNT_SPLIT_ERROR, findings.get(0).getFindingType());
    }

    @Test
    void firesOnUnequalCgstSgst() {
        ParsedVoucher v = voucher("Purchase", D, "Acme", null);
        entry(v, "Input CGST", "-900.00");
        entry(v, "Input SGST", "-750.00");

        assertEquals(1, rule.execute(context(), List.of(v)).size());
    }

    @Test
    void silentWhenEqualWithinTolerance() {
        ParsedVoucher v = voucher("Purchase", D, "Acme", null);
        entry(v, "Input CGST", "-900.00");
        entry(v, "Input SGST", "-900.90");

        assertTrue(rule.execute(context(), List.of(v)).isEmpty());
    }

    @Test
    void silentOnCleanIgstOnly() {
        ParsedVoucher v = voucher("Purchase", D, "Acme", null);
        entry(v, "Input IGST", "-1800.00");

        assertTrue(rule.execute(context(), List.of(v)).isEmpty());
    }

    @Test
    void rule3_mixedAndUnequal_reportsBoth() {
        ParsedVoucher v = voucher("Purchase", D, "Acme", null);
        entry(v, "Input IGST", "-1800.00");
        entry(v, "Input CGST", "-900.00");
        entry(v, "Input SGST", "-750.00");
        List<GstDaybookFinding> f = rule.execute(context(), List.of(v));
        assertEquals(2, f.size());
        assertTrue(f.stream().allMatch(x -> x.getFindingType() == GstFindingType.AMOUNT_SPLIT_ERROR
                && x.getSeverity() == FindingSeverity.HIGH && x.getVoucherSide() == VoucherSide.PURCHASE));
    }

    @Test
    void rule3_gstEntryWithNullAmount_noNpe() {
        ParsedVoucher v = voucher("Purchase", D, "Acme", null);
        entry(v, "Input CGST", "-900.00").setAmount(null);
        entry(v, "Input SGST", "-900.00");
        assertTrue(rule.execute(context(), List.of(v)).isEmpty());
    }
}
