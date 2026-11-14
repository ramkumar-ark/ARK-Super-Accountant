package com.arktech.superaccountant.gst.rules;

import com.arktech.superaccountant.daybook.models.ParsedVoucher;
import com.arktech.superaccountant.gst.models.GstDaybookFinding;
import com.arktech.superaccountant.gst.models.GstFindingType;
import com.arktech.superaccountant.gst.models.VoucherSide;
import com.arktech.superaccountant.masters.models.FindingSeverity;
import com.arktech.superaccountant.masters.models.GstApplicabilityType;
import com.arktech.superaccountant.masters.models.LedgerCategory;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static com.arktech.superaccountant.gst.GstTestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class GstRcmRulesTest {

    private static final LocalDate D = LocalDate.of(2026, 4, 10);
    private final RcmMixedWithTaxableRule rule9 = new RcmMixedWithTaxableRule();
    private final RcmSelfInvoiceMissingRule rule10 = new RcmSelfInvoiceMissingRule();

    private GstRuleContext context() {
        return ctx("27AAPFU0939F1ZV",
                snapshotGst("Freight Charges", LedgerCategory.EXPENSE, true, GstApplicabilityType.RCM, null),
                snapshotGst("Job Work", LedgerCategory.EXPENSE, true, GstApplicabilityType.TAXABLE, null),
                snapshot("Input CGST", LedgerCategory.GST),
                snapshot("GTA Transport", LedgerCategory.CREDITOR));
    }
    private ParsedVoucher freight(LocalDate date, String party) {
        ParsedVoucher v = voucher("Purchase", date, party, null);
        entry(v, "Freight Charges", "20000.00");
        entry(v, party, "-20000.00").setPartyLedger(true);
        return v;
    }
    private ParsedVoucher selfInvoice(LocalDate date, String party) {
        ParsedVoucher s = voucher("Journal", date, party, null);
        s.setReverseChargeApplicable(true);
        return s;
    }

    // --- rule 10 ---
    @Test void rule10_firesWhenNoSelfInvoiceInMonth() {
        List<GstDaybookFinding> f = rule10.execute(context(), List.of(freight(D, "GTA Transport")));
        assertEquals(1, f.size());
        assertEquals(GstFindingType.RCM_SELF_INVOICE_MISSING, f.get(0).getFindingType());
        assertEquals(FindingSeverity.HIGH, f.get(0).getSeverity());
        assertEquals("Freight Charges", f.get(0).getLedgerName());
        assertEquals(0, new BigDecimal("20000.00").compareTo(f.get(0).getTaxableAmount()));
        assertEquals(VoucherSide.PURCHASE, f.get(0).getVoucherSide());
    }
    @Test void rule10_silentWhenSelfInvoiceSameMonthAndParty() {
        assertTrue(rule10.execute(context(), List.of(freight(D, "GTA Transport"),
                selfInvoice(LocalDate.of(2026, 4, 25), "GTA Transport"))).isEmpty());
    }
    @Test void rule10_firesWhenSelfInvoiceInDifferentMonth() {
        assertEquals(1, rule10.execute(context(), List.of(freight(D, "GTA Transport"),
                selfInvoice(LocalDate.of(2026, 5, 2), "GTA Transport"))).size());
    }
    @Test void rule10_cancelledSelfInvoiceDoesNotCount() {
        ParsedVoucher s = selfInvoice(LocalDate.of(2026, 4, 25), "GTA Transport");
        s.setCancelled(true);
        assertEquals(1, rule10.execute(context(), List.of(freight(D, "GTA Transport"), s)).size());
    }
    @Test void rule10_partyMatchIgnoresCaseAndWhitespace() {
        assertTrue(rule10.execute(context(), List.of(freight(D, "gta transport"),
                selfInvoice(LocalDate.of(2026, 4, 25), "GTA Transport "))).isEmpty());
    }
    @Test void rule10_silentForNonRcmLedgerAndCreditEntries() {
        ParsedVoucher taxable = voucher("Purchase", D, "Acme", null);
        entry(taxable, "Job Work", "5000.00");
        ParsedVoucher reversal = voucher("Debit Note", D, "GTA Transport", null);
        entry(reversal, "Freight Charges", "-2000.00");
        assertTrue(rule10.execute(context(), List.of(taxable, reversal)).isEmpty());
    }

    // --- rule 9 ---
    @Test void rule9_flagsMultipleTaxableEntriesOnRcmVoucher() {
        ParsedVoucher v = selfInvoice(D, "GTA Transport");
        entryWithRate(v, "Freight Charges", "20000.00", "5", "Taxable");
        entryWithRate(v, "Job Work", "5000.00", "18", "Taxable");
        entry(v, "GTA Transport", "-25000.00").setPartyLedger(true);
        List<GstDaybookFinding> f = rule9.execute(context(), List.of(v));
        assertEquals(1, f.size());
        assertEquals(GstFindingType.RCM_MIXED_WITH_TAXABLE, f.get(0).getFindingType());
        assertEquals(FindingSeverity.LOW, f.get(0).getSeverity());
        assertEquals(VoucherSide.OTHER, f.get(0).getVoucherSide());
        assertEquals("Freight Charges, Job Work", f.get(0).getActualValue());
    }
    @Test void rule9_silentWhenRepeatedLinesHitSameLedger() {
        ParsedVoucher v = selfInvoice(D, "GTA Transport");
        entryWithRate(v, "Freight Charges", "20000.00", "5", "Taxable");
        entryWithRate(v, "freight charges", "3000.00", "5", "Taxable");
        assertTrue(rule9.execute(context(), List.of(v)).isEmpty());
    }
    @Test void rule9_listsEachDistinctLedgerOnce() {
        ParsedVoucher v = selfInvoice(D, "GTA Transport");
        entryWithRate(v, "Freight Charges", "20000.00", "5", "Taxable");
        entryWithRate(v, "Job Work", "5000.00", "18", "Taxable");
        entryWithRate(v, "freight charges", "3000.00", "5", "Taxable");
        assertEquals("Freight Charges, Job Work", rule9.execute(context(), List.of(v)).get(0).getActualValue());
    }
    @Test void rule9_silentWithSingleTaxableEntryOrNonRcmVoucher() {
        ParsedVoucher single = selfInvoice(D, "GTA Transport");
        entryWithRate(single, "Freight Charges", "20000.00", "5", "Taxable");
        entry(single, "Input CGST", "500.00");
        ParsedVoucher normal = voucher("Purchase", D, "Acme", null);
        entryWithRate(normal, "Freight Charges", "20000.00", "5", "Taxable");
        entryWithRate(normal, "Job Work", "5000.00", "18", "Taxable");
        assertTrue(rule9.execute(context(), List.of(single, normal)).isEmpty());
    }
}
