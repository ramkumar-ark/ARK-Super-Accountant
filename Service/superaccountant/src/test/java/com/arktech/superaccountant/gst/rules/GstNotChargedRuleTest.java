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

class GstNotChargedRuleTest {

    private final GstNotChargedRule rule = new GstNotChargedRule();
    private static final LocalDate D = LocalDate.of(2026, 4, 10);

    private GstRuleContext context() {
        return ctx("27AAPFU0939F1ZV",
                snapshotGst("Job Work Charges", LedgerCategory.EXPENSE, true, GstApplicabilityType.TAXABLE, null),
                snapshotGst("Acme Traders", LedgerCategory.CREDITOR, null, null, "27AABCU9603R1ZM"),
                snapshot("Input CGST", LedgerCategory.GST),
                snapshot("Input SGST", LedgerCategory.GST));
    }

    @Test
    void firesWhenTaxableExpenseRegisteredPartyAndNoGstEntries() {
        ParsedVoucher v = voucher("Purchase", D, "Acme Traders", null);
        entry(v, "Job Work Charges", "10000.00");
        entry(v, "Acme Traders", "-10000.00");

        List<GstDaybookFinding> findings = rule.execute(context(), List.of(v));

        assertEquals(1, findings.size());
        assertEquals(GstFindingType.GST_NOT_CHARGED, findings.get(0).getFindingType());
        assertEquals(FindingSeverity.HIGH, findings.get(0).getSeverity());
        assertEquals("Job Work Charges", findings.get(0).getLedgerName());
        assertEquals(VoucherSide.PURCHASE, findings.get(0).getVoucherSide());
        assertEquals(FindingSeverity.HIGH, findings.get(0).getSeverity());
    }

    @Test
    void silentWhenGstEntriesPresent() {
        ParsedVoucher v = voucher("Purchase", D, "Acme Traders", null);
        entry(v, "Job Work Charges", "10000.00");
        entry(v, "Input CGST", "-900.00");
        entry(v, "Input SGST", "-900.00");
        entry(v, "Acme Traders", "-11800.00");

        assertTrue(rule.execute(context(), List.of(v)).isEmpty());
    }

    @Test
    void silentWhenPartyUnregistered() {
        GstRuleContext ctx = ctx("27AAPFU0939F1ZV",
                snapshotGst("Job Work Charges", LedgerCategory.EXPENSE, true, GstApplicabilityType.TAXABLE, null),
                snapshotGst("Local Vendor", LedgerCategory.CREDITOR, null, null, null));
        ParsedVoucher v = voucher("Purchase", D, "Local Vendor", null);
        entry(v, "Job Work Charges", "5000.00");
        entry(v, "Local Vendor", "-5000.00");

        assertTrue(rule.execute(ctx, List.of(v)).isEmpty());
    }

    @Test
    void silentWhenLedgerNotTaxable() {
        GstRuleContext ctx = ctx("27AAPFU0939F1ZV",
                snapshotGst("Salaries", LedgerCategory.EXPENSE, false, GstApplicabilityType.NOT_APPLICABLE, null),
                snapshotGst("Acme Traders", LedgerCategory.CREDITOR, null, null, "27AABCU9603R1ZM"));
        ParsedVoucher v = voucher("Purchase", D, "Acme Traders", null);
        entry(v, "Salaries", "50000.00");
        entry(v, "Acme Traders", "-50000.00");

        assertTrue(rule.execute(ctx, List.of(v)).isEmpty());
    }

    @Test
    void silentForCancelledVoucher() {
        ParsedVoucher v = voucher("Purchase", D, "Acme Traders", null);
        v.setCancelled(true);
        entry(v, "Job Work Charges", "10000.00");

        assertTrue(rule.execute(context(), List.of(v)).isEmpty());
    }

    @Test
    void rule1_silentOnSalesVoucher() {
        ParsedVoucher v = voucher("Sales", D, "Acme Traders", null);
        entry(v, "Job Work Charges", "10000.00");
        entry(v, "Acme Traders", "-10000.00");
        assertTrue(rule.execute(context(), List.of(v)).isEmpty());
    }

    @Test
    void rule1_firesOnDebitNoteSideWithTaxableDebit() {
        ParsedVoucher v = voucher("Debit Note", D, "Acme Traders", null);
        entry(v, "Job Work Charges", "10000.00");
        entry(v, "Acme Traders", "-10000.00");
        assertEquals(1, rule.execute(context(), List.of(v)).size());
    }

    @Test
    void rule1_nullEntries_silent() {
        ParsedVoucher v = voucher("Purchase", D, "Acme Traders", null);
        v.setEntries(null);
        assertTrue(rule.execute(context(), List.of(v)).isEmpty());
    }
}
