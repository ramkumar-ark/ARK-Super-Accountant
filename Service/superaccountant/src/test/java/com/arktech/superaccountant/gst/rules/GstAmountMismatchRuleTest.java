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

class GstAmountMismatchRuleTest {

    private final GstAmountMismatchRule rule = new GstAmountMismatchRule();
    private static final LocalDate D = LocalDate.of(2026, 4, 10);

    private GstRuleContext context() {
        return ctx("27AAPFU0939F1ZV",
                snapshot("Job Work Charges", LedgerCategory.EXPENSE),
                snapshot("Input CGST", LedgerCategory.GST),
                snapshot("Input SGST", LedgerCategory.GST),
                snapshot("Input IGST", LedgerCategory.GST),
                snapshot("Output CGST", LedgerCategory.GST),
                snapshot("Output SGST", LedgerCategory.GST));
    }

    @Test
    void firesOnIntraStateShortDeduction() {
        // 10000 @ 18% => CGST 900 + SGST 900; booked 700 + 700
        ParsedVoucher v = voucher("Purchase", D, "Acme", "27AABCU9603R1ZM");
        entryWithRate(v, "Job Work Charges", "10000.00", "18", "Taxable");
        entry(v, "Input CGST", "-700.00");
        entry(v, "Input SGST", "-700.00");

        List<GstDaybookFinding> findings = rule.execute(context(), List.of(v));
        assertEquals(1, findings.size());
        assertEquals(GstFindingType.GST_AMOUNT_MISMATCH, findings.get(0).getFindingType());
    }

    @Test
    void silentWithinToleranceIntraState() {
        // 10000 @ 18% => 900 each; booked 900.50 + 899.60 — within ₹1 per head
        ParsedVoucher v = voucher("Purchase", D, "Acme", "27AABCU9603R1ZM");
        entryWithRate(v, "Job Work Charges", "10000.00", "18", "Taxable");
        entry(v, "Input CGST", "-900.50");
        entry(v, "Input SGST", "-899.60");

        assertTrue(rule.execute(context(), List.of(v)).isEmpty());
    }

    @Test
    void firesOnInterStateIgstMismatch() {
        // 10000 @ 18% => IGST 1800; booked 1500
        ParsedVoucher v = voucher("Purchase", D, "Acme", "29AABCU9603R1ZM");
        entryWithRate(v, "Job Work Charges", "10000.00", "18", "Taxable");
        entry(v, "Input IGST", "-1500.00");

        assertEquals(1, rule.execute(context(), List.of(v)).size());
    }

    @Test
    void comparesTotalWhenPartyGstinAbsent() {
        // undeterminable state: total actual 1799.50 vs expected 1800 — within tolerance
        ParsedVoucher v = voucher("Purchase", D, "Acme", null);
        entryWithRate(v, "Job Work Charges", "10000.00", "18", "Taxable");
        entry(v, "Input CGST", "-899.50");
        entry(v, "Input SGST", "-900.00");

        assertTrue(rule.execute(context(), List.of(v)).isEmpty());
    }

    @Test
    void silentWhenNoRateData() {
        ParsedVoucher v = voucher("Purchase", D, "Acme", "27AABCU9603R1ZM");
        entryWithRate(v, "Job Work Charges", "10000.00", null, "Taxable");
        entry(v, "Input CGST", "-700.00");
        entry(v, "Input SGST", "-700.00");

        assertTrue(rule.execute(context(), List.of(v)).isEmpty());
    }

    @Test
    void silentWhenNoGstEntries() {
        ParsedVoucher v = voucher("Purchase", D, "Acme", "27AABCU9603R1ZM");
        entryWithRate(v, "Job Work Charges", "10000.00", "18", "Taxable");

        assertTrue(rule.execute(context(), List.of(v)).isEmpty()); // rule 1 territory
    }

    @Test
    void rule2_salesSideMismatch_taggedSales() {
        ParsedVoucher v = voucher("Sales", D, "Acme", "27AABCU9603R1ZM");
        entryWithRate(v, "Job Work Charges", "-10000.00", "18", "Taxable");
        entry(v, "Output CGST", "-700.00");
        entry(v, "Output SGST", "-700.00");
        List<GstDaybookFinding> f = rule.execute(context(), List.of(v));
        assertEquals(1, f.size());
        assertEquals(VoucherSide.SALES, f.get(0).getVoucherSide());
        assertEquals("1800.00", f.get(0).getExpectedValue());
        assertEquals("1400.00", f.get(0).getActualValue());
    }

    @Test
    void rule2_toleranceBoundary_exactlyOneRupeeSilent() {
        // 10000 @ 18% => 900 each; booked 901.00 + 899.00 -> |diff| == 1.00, not > TOLERANCE
        ParsedVoucher v = voucher("Purchase", D, "Acme", "27AABCU9603R1ZM");
        entryWithRate(v, "Job Work Charges", "10000.00", "18", "Taxable");
        entry(v, "Input CGST", "-901.00");
        entry(v, "Input SGST", "-899.00");
        assertTrue(rule.execute(context(), List.of(v)).isEmpty());
    }
}
