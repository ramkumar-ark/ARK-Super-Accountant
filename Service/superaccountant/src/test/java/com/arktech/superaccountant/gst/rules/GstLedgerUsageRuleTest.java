package com.arktech.superaccountant.gst.rules;

import com.arktech.superaccountant.daybook.models.ParsedVoucher;
import com.arktech.superaccountant.gst.models.GstDaybookFinding;
import com.arktech.superaccountant.gst.models.GstFindingType;
import com.arktech.superaccountant.gst.models.VoucherSide;
import com.arktech.superaccountant.masters.models.LedgerCategory;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static com.arktech.superaccountant.gst.GstTestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class GstLedgerUsageRuleTest {

    private final GstLedgerUsageRule rule = new GstLedgerUsageRule();
    private static final LocalDate D = LocalDate.of(2026, 4, 10);

    private GstRuleContext context() {
        return ctx("27AAPFU0939F1ZV",
                snapshot("Input CGST", LedgerCategory.GST),
                snapshot("Input SGST", LedgerCategory.GST),
                snapshot("Input IGST", LedgerCategory.GST));
    }

    private GstRuleContext contextWithOutput() {
        return ctx("27AAPFU0939F1ZV",
                snapshot("Input CGST", LedgerCategory.GST),
                snapshot("Input SGST", LedgerCategory.GST),
                snapshot("Input IGST", LedgerCategory.GST),
                snapshot("Output IGST", LedgerCategory.GST));
    }

    @Test
    void firesOnIgstForIntraState() {
        ParsedVoucher v = voucher("Purchase", D, "Acme", "27AABCU9603R1ZM"); // same state 27
        entry(v, "Input IGST", "-1800.00");

        List<GstDaybookFinding> findings = rule.execute(context(), List.of(v));
        assertEquals(1, findings.size());
        assertEquals(GstFindingType.WRONG_GST_LEDGER_STATE, findings.get(0).getFindingType());
    }

    @Test
    void firesOnCgstSgstForInterState() {
        ParsedVoucher v = voucher("Purchase", D, "Acme", "29AABCU9603R1ZM"); // 29 vs org 27
        entry(v, "Input CGST", "-900.00");
        entry(v, "Input SGST", "-900.00");

        assertEquals(1, rule.execute(context(), List.of(v)).size());
    }

    @Test
    void skipsWhenPartyGstinAbsent() {
        ParsedVoucher v = voucher("Purchase", D, "Acme", null);
        entry(v, "Input IGST", "-1800.00");

        assertTrue(rule.execute(context(), List.of(v)).isEmpty());
    }

    @Test
    void silentOnCorrectUsage() {
        ParsedVoucher intra = voucher("Purchase", D, "Acme", "27AABCU9603R1ZM");
        entry(intra, "Input CGST", "-900.00");
        entry(intra, "Input SGST", "-900.00");
        ParsedVoucher inter = voucher("Purchase", D, "Zeta", "29AABCU9603R1ZM");
        entry(inter, "Input IGST", "-1800.00");

        assertTrue(rule.execute(context(), List.of(intra, inter)).isEmpty());
    }

    @Test
    void rule4_creditNoteTaggedSales() {
        ParsedVoucher v = voucher("Credit Note", D, "Acme", "27AABCU9603R1ZM");
        entry(v, "Output IGST", "1800.00");
        // context additionally holds snapshot("Output IGST", LedgerCategory.GST)
        List<GstDaybookFinding> f = rule.execute(contextWithOutput(), List.of(v));
        assertEquals(1, f.size());
        assertEquals(VoucherSide.SALES, f.get(0).getVoucherSide());
    }

    @Test
    void rule4_orgWithoutGstin_skips() {
        GstRuleContext noOrg = ctx(null, snapshot("Input IGST", LedgerCategory.GST));
        ParsedVoucher v = voucher("Purchase", D, "Acme", "27AABCU9603R1ZM");
        entry(v, "Input IGST", "-1800.00");
        assertTrue(rule.execute(noOrg, List.of(v)).isEmpty());
    }
}
