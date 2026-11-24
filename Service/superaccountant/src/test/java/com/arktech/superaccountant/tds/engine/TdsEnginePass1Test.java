package com.arktech.superaccountant.tds.engine;

import com.arktech.superaccountant.masters.models.LedgerCategory;
import com.arktech.superaccountant.tds.mapping.TdsLedgerRole;
import com.arktech.superaccountant.tds.models.TdsDeducteeEntry;
import com.arktech.superaccountant.tds.models.TdsDeducteeType;
import com.arktech.superaccountant.tds.models.TdsFinding;
import com.arktech.superaccountant.tds.models.TdsFindingType;
import com.arktech.superaccountant.masters.models.FindingSeverity;
import com.arktech.superaccountant.masters.models.ResolveStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static com.arktech.superaccountant.tds.engine.TdsEngineFixtures.FY_START;
import static com.arktech.superaccountant.tds.engine.TdsEngineFixtures.JOB;
import static com.arktech.superaccountant.tds.engine.TdsEngineFixtures.ORG;
import static com.arktech.superaccountant.tds.engine.TdsEngineFixtures.WF;
import static com.arktech.superaccountant.tds.engine.TdsEngineFixtures.input;
import static com.arktech.superaccountant.tds.engine.TdsEngineFixtures.inputQ1;
import static com.arktech.superaccountant.tds.engine.TdsEngineFixtures.mapping;
import static com.arktech.superaccountant.tds.engine.TdsEngineFixtures.mappings;
import static com.arktech.superaccountant.tds.engine.TdsEngineFixtures.snap;
import static com.arktech.superaccountant.tds.engine.TdsEngineFixtures.stdMappings;
import static com.arktech.superaccountant.tds.engine.TdsEngineFixtures.stdSnaps;
import static com.arktech.superaccountant.tds.engine.TdsEngineFixtures.voucher;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TdsEnginePass1Test {

    private final TdsEngine engine = new TdsEngine();

    private TdsAnalysisResult run(List<com.arktech.superaccountant.daybook.models.ParsedVoucher> vouchers,
                                   Map<String, com.arktech.superaccountant.masters.models.UploadLedgerSnapshot> snaps,
                                   Map<String, com.arktech.superaccountant.tds.mapping.TdsLedgerMapping> mappingsBy,
                                   LocalDate periodStart, LocalDate periodEnd) {
        return engine.analyze(input(vouchers, snaps, mappingsBy, periodStart, periodEnd));
    }

    @Test
    void correctDeduction_entryNoFindings() {
        var v = voucher("V1", LocalDate.of(2026, 5, 10), "ABC Contractors", new Object[][]{
                {"Subcontractor Charges", "100000"}, {"ABC Contractors", "-99000"},
                {"TDS Payable 194C", "-1000"}});
        var result = engine.analyze(inputQ1(List.of(v), stdSnaps("ABCPE1234F"), stdMappings()));
        assertTrue(result.findings().isEmpty());
        assertEquals(1, result.entries().size());
        TdsDeducteeEntry e = result.entries().get(0);
        assertEquals("194C", e.getSection());
        assertEquals(new BigDecimal("100000.00"), e.getAmountPaid());
        assertEquals(new BigDecimal("1000.00"), e.getTdsDeducted());
        assertEquals(TdsDeducteeType.NON_COMPANY, e.getDeducteeType());
        assertEquals(WF, e.getWorkflowId());
        assertTrue(e.getSourceVoucherIds().contains(v.getId().toString()));
        assertNull(e.getTdsChallanId());
    }

    @Test
    void shortDeduction_flaggedHighWithIds() {
        var v = voucher("V1", LocalDate.of(2026, 5, 10), "ABC Contractors", new Object[][]{
                {"Subcontractor Charges", "100000"}, {"ABC Contractors", "-99500"},
                {"TDS Payable 194C", "-500"}});
        var result = engine.analyze(inputQ1(List.of(v), stdSnaps("ABCPE1234F"), stdMappings()));
        assertEquals(1, result.findings().size());
        TdsFinding f = result.findings().get(0);
        assertEquals(TdsFindingType.SHORT_DEDUCTED, f.getFindingType());
        assertEquals(FindingSeverity.HIGH, f.getSeverity());
        assertEquals(new BigDecimal("1000.00"), f.getExpectedAmount());
        assertEquals(new BigDecimal("500.00"), f.getActualAmount());
        assertEquals(ResolveStatus.OPEN, f.getResolveStatus());
        assertEquals(ORG, f.getOrganizationId());
        assertEquals(WF, f.getWorkflowId());
        assertEquals(JOB, f.getUploadJobId());
        assertNull(f.getEvidenceHash());
        assertNull(f.getId());
    }

    @Test
    void withinOneRupeeTolerance_noFinding() {
        var v = voucher("V1", LocalDate.of(2026, 5, 10), "ABC Contractors", new Object[][]{
                {"Subcontractor Charges", "100000"}, {"ABC Contractors", "-99000.50"},
                {"TDS Payable 194C", "-999.50"}});
        var result = engine.analyze(inputQ1(List.of(v), stdSnaps("ABCPE1234F"), stdMappings()));
        assertTrue(result.findings().isEmpty());
    }

    @Test
    void companyPan_usesCompanyRate() {
        var v = voucher("V1", LocalDate.of(2026, 5, 10), "ABC Contractors", new Object[][]{
                {"Subcontractor Charges", "100000"}, {"ABC Contractors", "-98000"},
                {"TDS Payable 194C", "-2000"}});
        var result = engine.analyze(inputQ1(List.of(v), stdSnaps("ABCCE1234F"), stdMappings()));
        assertTrue(result.findings().isEmpty());
        assertEquals(1, result.entries().size());
        assertEquals(TdsDeducteeType.COMPANY, result.entries().get(0).getDeducteeType());
    }

    @Test
    void missingPan_panMissingAndHigherRateNotApplied() {
        var v = voucher("V1", LocalDate.of(2026, 5, 10), "ABC Contractors", new Object[][]{
                {"Subcontractor Charges", "100000"}, {"ABC Contractors", "-99000"},
                {"TDS Payable 194C", "-1000"}});
        var result = engine.analyze(inputQ1(List.of(v), stdSnaps(null), stdMappings()));
        assertEquals(2, result.findings().size());
        assertTrue(result.findings().stream().anyMatch(f -> f.getFindingType() == TdsFindingType.PAN_MISSING));
        assertTrue(result.findings().stream()
                .anyMatch(f -> f.getFindingType() == TdsFindingType.HIGHER_RATE_NOT_APPLIED));
        assertTrue(result.findings().stream()
                .noneMatch(f -> f.getFindingType() == TdsFindingType.SHORT_DEDUCTED));
    }

    @Test
    void blankPan_panMissingNeverPanInvalid() {
        var v = voucher("V1", LocalDate.of(2026, 5, 10), "ABC Contractors", new Object[][]{
                {"Subcontractor Charges", "100000"}, {"ABC Contractors", "-99000"},
                {"TDS Payable 194C", "-1000"}});
        var result = engine.analyze(inputQ1(List.of(v), stdSnaps("   "), stdMappings()));
        assertTrue(result.findings().stream().anyMatch(f -> f.getFindingType() == TdsFindingType.PAN_MISSING));
        assertTrue(result.findings().stream().noneMatch(f -> f.getFindingType() == TdsFindingType.PAN_INVALID));
    }

    @Test
    void invalidPan_panInvalidAndEntryHigherRate() {
        var v = voucher("V1", LocalDate.of(2026, 5, 10), "ABC Contractors", new Object[][]{
                {"Subcontractor Charges", "100000"}, {"ABC Contractors", "-80000"},
                {"TDS Payable 194C", "-20000"}});
        var result = engine.analyze(inputQ1(List.of(v), stdSnaps("BADPAN123"), stdMappings()));
        assertTrue(result.findings().stream().anyMatch(f -> f.getFindingType() == TdsFindingType.PAN_INVALID));
        assertFalse(result.findings().isEmpty());
        TdsDeducteeEntry e = result.entries().get(0);
        assertTrue(e.isHigherRateApplied());
        assertFalse(e.isPanValid());
        assertNull(e.getDeducteePan());
    }

    @Test
    void panFindingDeduplicatedPerPartySection() {
        var v1 = voucher("V1", LocalDate.of(2026, 5, 10), "ABC Contractors", new Object[][]{
                {"Subcontractor Charges", "100000"}, {"ABC Contractors", "-99000"},
                {"TDS Payable 194C", "-1000"}});
        var v2 = voucher("V2", LocalDate.of(2026, 6, 10), "ABC Contractors", new Object[][]{
                {"Subcontractor Charges", "50000"}, {"ABC Contractors", "-49500"},
                {"TDS Payable 194C", "-500"}});
        var result = engine.analyze(inputQ1(List.of(v1, v2), stdSnaps(null), stdMappings()));
        assertEquals(1, result.findings().stream()
                .filter(f -> f.getFindingType() == TdsFindingType.PAN_MISSING).count());
    }

    @Test
    void wrongSection_flagged() {
        var v = voucher("V1", LocalDate.of(2026, 5, 10), "ABC Contractors", new Object[][]{
                {"Subcontractor Charges", "100000"}, {"ABC Contractors", "-99000"},
                {"TDS Payable 194J", "-1000"}});
        Map<String, com.arktech.superaccountant.masters.models.UploadLedgerSnapshot> snaps = stdSnaps("ABCPE1234F");
        snaps.put(TdsEngine.key("TDS Payable 194J"),
                snap("TDS Payable 194J", LedgerCategory.TDS, true, "194J_B", null));
        var result = engine.analyze(inputQ1(List.of(v), snaps, mappings(
                mapping("TDS Payable 194C", TdsLedgerRole.TDS_PAYABLE, "194C"),
                mapping("TDS Payable 194J", TdsLedgerRole.TDS_PAYABLE, "194J_B"))));
        assertTrue(result.findings().stream().anyMatch(f -> f.getFindingType() == TdsFindingType.WRONG_SECTION));
    }

    @Test
    void unmappedPayableLedger_notRecognized() {
        var v = voucher("V1", LocalDate.of(2026, 5, 10), "ABC Contractors", new Object[][]{
                {"Subcontractor Charges", "20000"}, {"ABC Contractors", "-19800"},
                {"TDS Payable 194C", "-200"}});
        var result = engine.analyze(inputQ1(List.of(v), stdSnaps("ABCPE1234F"), mappings()));
        assertTrue(result.findings().isEmpty());
        assertTrue(result.entries().isEmpty());
    }

    @Test
    void notTdsMapping_ignored() {
        var v = voucher("V1", LocalDate.of(2026, 5, 10), "ABC Contractors", new Object[][]{
                {"Subcontractor Charges", "20000"}, {"ABC Contractors", "-19800"},
                {"TDS Payable 194C", "-200"}});
        var result = engine.analyze(inputQ1(List.of(v), stdSnaps("ABCPE1234F"),
                mappings(mapping("TDS Payable 194C", TdsLedgerRole.NOT_TDS, null))));
        assertTrue(result.findings().isEmpty());
        assertTrue(result.entries().isEmpty());
    }

    @Test
    void combinedPayable_neverWrongSection() {
        Map<String, com.arktech.superaccountant.masters.models.UploadLedgerSnapshot> snaps =
                stdSnaps("ABCPE1234F");
        snaps.put(TdsEngine.key("Site Expenses"),
                snap("Site Expenses", LedgerCategory.EXPENSE, true, "194C", null));
        snaps.put(TdsEngine.key("TDS Payable"),
                snap("TDS Payable", LedgerCategory.TDS, true, null, null));
        var v = voucher("V1", LocalDate.of(2026, 5, 10), "ABC Contractors", new Object[][]{
                {"Subcontractor Charges", "60000"}, {"Site Expenses", "40000"},
                {"ABC Contractors", "-99000"},
                {"TDS Payable", "-1000"}});
        var result = engine.analyze(inputQ1(List.of(v), snaps,
                mappings(mapping("TDS Payable", TdsLedgerRole.TDS_PAYABLE, null))));
        assertTrue(result.findings().stream()
                .noneMatch(f -> f.getFindingType() == TdsFindingType.WRONG_SECTION));
    }

    @Test
    void fixedSection_wrongSectionStillFires() {
        Map<String, com.arktech.superaccountant.masters.models.UploadLedgerSnapshot> snaps =
                new java.util.LinkedHashMap<>();
        snaps.put(TdsEngine.key("Professional Fees"),
                snap("Professional Fees", LedgerCategory.EXPENSE, true, "194J_B", null));
        snaps.put(TdsEngine.key("ABC Contractors"),
                snap("ABC Contractors", LedgerCategory.CREDITOR, null, null, "ABCPE1234F"));
        snaps.put(TdsEngine.key("TDS Payable 194C"),
                snap("TDS Payable 194C", LedgerCategory.TDS, true, null, null));
        var v = voucher("V1", LocalDate.of(2026, 5, 10), "ABC Contractors", new Object[][]{
                {"Professional Fees", "25000"}, {"ABC Contractors", "-24750"},
                {"TDS Payable 194C", "-250"}});
        var result = engine.analyze(inputQ1(List.of(v), snaps, stdMappings()));
        assertEquals(1, result.findings().size());
        assertEquals(TdsFindingType.WRONG_SECTION, result.findings().get(0).getFindingType());
    }

    @Test
    void cancelledAndNonTdsVouchers_ignored() {
        var cancelled = voucher("V1", LocalDate.of(2026, 5, 10), "ABC Contractors", new Object[][]{
                {"Subcontractor Charges", "100000"}, {"ABC Contractors", "-99000"},
                {"TDS Payable 194C", "-500"}});
        cancelled.setCancelled(true);
        var plain = voucher("V2", LocalDate.of(2026, 5, 11), "ABC Contractors", new Object[][]{
                {"Subcontractor Charges", "100000"}, {"ABC Contractors", "-100000"}});
        Map<String, com.arktech.superaccountant.masters.models.UploadLedgerSnapshot> snaps =
                new java.util.LinkedHashMap<>(stdSnaps("ABCPE1234F"));
        snaps.put(TdsEngine.key("Subcontractor Charges"),
                snap("Subcontractor Charges", LedgerCategory.EXPENSE, false, "194C", null));
        var result = engine.analyze(inputQ1(List.of(cancelled, plain), snaps, stdMappings()));
        assertTrue(result.findings().isEmpty());
        assertTrue(result.entries().isEmpty());
    }

    @Test
    void earlierPeriodVoucher_noFindingNoEntry() {
        var v = voucher("V1", LocalDate.of(2026, 4, 15), "ABC Contractors", new Object[][]{
                {"Subcontractor Charges", "100000"}, {"ABC Contractors", "-99500"},
                {"TDS Payable 194C", "-500"}});
        var result = run(List.of(v), stdSnaps("ABCPE1234F"), stdMappings(),
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30));
        assertTrue(result.findings().isEmpty());
        assertTrue(result.entries().isEmpty());
    }

    @Test
    void voucherAfterPeriodEnd_ignoredEverywhere() {
        var v = voucher("V1", LocalDate.of(2026, 7, 5), "ABC Contractors", new Object[][]{
                {"Subcontractor Charges", "100000"}, {"ABC Contractors", "-99500"},
                {"TDS Payable 194C", "-500"}});
        var result = engine.analyze(inputQ1(List.of(v), stdSnaps("ABCPE1234F"), stdMappings()));
        assertTrue(result.findings().isEmpty());
        assertTrue(result.entries().isEmpty());
    }

    @Test
    void ledgerNameCaseAndWhitespace_matches() {
        var v = voucher("V1", LocalDate.of(2026, 5, 10), "ABC Contractors", new Object[][]{
                {" subcontractor CHARGES ", "100000"}, {"ABC Contractors", "-99000"},
                {"TDS Payable 194C", "-1000"}});
        var result = engine.analyze(inputQ1(List.of(v), stdSnaps("ABCPE1234F"), stdMappings()));
        assertTrue(result.findings().isEmpty());
        assertEquals(1, result.entries().size());
        assertEquals(new BigDecimal("1000.00"), result.entries().get(0).getTdsDeducted());
    }

    @Test
    void nullCategoryNullEntriesNullDate_skippedSafely() {
        Map<String, com.arktech.superaccountant.masters.models.UploadLedgerSnapshot> snaps = stdSnaps("ABCPE1234F");
        snaps.put(TdsEngine.key("Subcontractor Charges"),
                snap("Subcontractor Charges", null, true, "194C", null));
        var nullCat = voucher("V1", LocalDate.of(2026, 5, 10), "ABC Contractors", new Object[][]{
                {"Subcontractor Charges", "100000"}, {"ABC Contractors", "-99000"},
                {"TDS Payable 194C", "-1000"}});
        var nullEntries = voucher("V2", LocalDate.of(2026, 5, 11), "ABC Contractors", new Object[][]{});
        nullEntries.setEntries(null);
        var nullDate = voucher("V3", null, "ABC Contractors", new Object[][]{
                {"Subcontractor Charges", "100000"}, {"ABC Contractors", "-99000"},
                {"TDS Payable 194C", "-1000"}});
        var result = engine.analyze(inputQ1(List.of(nullCat, nullEntries, nullDate), snaps, stdMappings()));
        assertTrue(result.findings().isEmpty());
        assertTrue(result.entries().isEmpty());
    }

    @Test
    void journalWithoutParty_usesCreditorEntryAsParty() {
        var v = voucher("V1", LocalDate.of(2026, 5, 10), null, new Object[][]{
                {"Subcontractor Charges", "100000"}, {"ABC Contractors", "-99000"},
                {"TDS Payable 194C", "-1000"}});
        var result = engine.analyze(inputQ1(List.of(v), stdSnaps("ABCPE1234F"), stdMappings()));
        assertTrue(result.findings().stream()
                .noneMatch(f -> f.getFindingType() == TdsFindingType.PAN_MISSING));
        assertEquals(1, result.entries().size());
        assertEquals("ABC Contractors", result.entries().get(0).getDeducteeName());
    }

    @Test
    void inputRejectsPeriodOutsideFy() {
        var v = voucher("V1", LocalDate.of(2026, 5, 10), "ABC Contractors", new Object[][]{
                {"Subcontractor Charges", "100000"}});
        assertThrows(IllegalArgumentException.class,
                () -> input(List.of(v), stdSnaps("ABCPE1234F"), stdMappings(),
                        FY_START, LocalDate.of(2027, 4, 1)));
    }
}
