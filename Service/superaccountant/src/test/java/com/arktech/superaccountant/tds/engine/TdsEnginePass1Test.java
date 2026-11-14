package com.arktech.superaccountant.tds.engine;

import com.arktech.superaccountant.masters.models.LedgerCategory;
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
import static com.arktech.superaccountant.tds.engine.TdsEngineFixtures.snap;
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
                                  LocalDate periodStart, LocalDate periodEnd) {
        return engine.analyze(input(vouchers, snaps, periodStart, periodEnd));
    }

    @Test
    void correctDeduction_entryNoFindings() {
        var v = voucher("V1", LocalDate.of(2026, 5, 10), "ABC Contractors", new Object[][]{
                {"Subcontractor Charges", "100000"}, {"ABC Contractors", "-99000"},
                {"TDS Payable 194C", "-1000"}});
        var result = engine.analyze(inputQ1(List.of(v), stdSnaps("ABCPE1234F")));
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
        var result = engine.analyze(inputQ1(List.of(v), stdSnaps("ABCPE1234F")));
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
        var result = engine.analyze(inputQ1(List.of(v), stdSnaps("ABCPE1234F")));
        assertTrue(result.findings().isEmpty());
    }

    @Test
    void companyPan_usesCompanyRate() {
        var v = voucher("V1", LocalDate.of(2026, 5, 10), "ABC Contractors", new Object[][]{
                {"Subcontractor Charges", "100000"}, {"ABC Contractors", "-98000"},
                {"TDS Payable 194C", "-2000"}});
        var result = engine.analyze(inputQ1(List.of(v), stdSnaps("ABCCE1234F")));
        assertTrue(result.findings().isEmpty());
        assertEquals(1, result.entries().size());
        assertEquals(TdsDeducteeType.COMPANY, result.entries().get(0).getDeducteeType());
    }

    @Test
    void missingPan_panMissingAndHigherRateNotApplied() {
        var v = voucher("V1", LocalDate.of(2026, 5, 10), "ABC Contractors", new Object[][]{
                {"Subcontractor Charges", "100000"}, {"ABC Contractors", "-99000"},
                {"TDS Payable 194C", "-1000"}});
        var result = engine.analyze(inputQ1(List.of(v), stdSnaps(null)));
        assertEquals(2, result.findings().size());
        assertTrue(result.findings().stream().anyMatch(f -> f.getFindingType() == TdsFindingType.PAN_MISSING));
        assertTrue(result.findings().stream()
                .anyMatch(f -> f.getFindingType() == TdsFindingType.HIGHER_RATE_NOT_APPLIED));
        assertTrue(result.findings().stream()
                .noneMatch(f -> f.getFindingType() == TdsFindingType.SHORT_DEDUCTED));
    }

    @Test
    void invalidPan_panInvalidAndEntryHigherRate() {
        var v = voucher("V1", LocalDate.of(2026, 5, 10), "ABC Contractors", new Object[][]{
                {"Subcontractor Charges", "100000"}, {"ABC Contractors", "-80000"},
                {"TDS Payable 194C", "-20000"}});
        var result = engine.analyze(inputQ1(List.of(v), stdSnaps("BADPAN123")));
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
        var result = engine.analyze(inputQ1(List.of(v1, v2), stdSnaps(null)));
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
        var result = engine.analyze(inputQ1(List.of(v), snaps));
        assertTrue(result.findings().stream().anyMatch(f -> f.getFindingType() == TdsFindingType.WRONG_SECTION));
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
        var result = engine.analyze(inputQ1(List.of(cancelled, plain), snaps));
        assertTrue(result.findings().isEmpty());
        assertTrue(result.entries().isEmpty());
    }

    @Test
    void earlierPeriodVoucher_noFindingNoEntry() {
        var v = voucher("V1", LocalDate.of(2026, 4, 15), "ABC Contractors", new Object[][]{
                {"Subcontractor Charges", "100000"}, {"ABC Contractors", "-99500"},
                {"TDS Payable 194C", "-500"}});
        var result = run(List.of(v), stdSnaps("ABCPE1234F"),
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30));
        assertTrue(result.findings().isEmpty());
        assertTrue(result.entries().isEmpty());
    }

    @Test
    void voucherAfterPeriodEnd_ignoredEverywhere() {
        var v = voucher("V1", LocalDate.of(2026, 7, 5), "ABC Contractors", new Object[][]{
                {"Subcontractor Charges", "100000"}, {"ABC Contractors", "-99500"},
                {"TDS Payable 194C", "-500"}});
        var result = engine.analyze(inputQ1(List.of(v), stdSnaps("ABCPE1234F")));
        assertTrue(result.findings().isEmpty());
        assertTrue(result.entries().isEmpty());
    }

    @Test
    void ledgerNameCaseAndWhitespace_matches() {
        var v = voucher("V1", LocalDate.of(2026, 5, 10), "ABC Contractors", new Object[][]{
                {" subcontractor CHARGES ", "100000"}, {"ABC Contractors", "-99000"},
                {"TDS Payable 194C", "-1000"}});
        var result = engine.analyze(inputQ1(List.of(v), stdSnaps("ABCPE1234F")));
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
        var result = engine.analyze(inputQ1(List.of(nullCat, nullEntries, nullDate), snaps));
        assertTrue(result.findings().isEmpty());
        assertTrue(result.entries().isEmpty());
    }

    @Test
    void journalWithoutParty_usesCreditorEntryAsParty() {
        var v = voucher("V1", LocalDate.of(2026, 5, 10), null, new Object[][]{
                {"Subcontractor Charges", "100000"}, {"ABC Contractors", "-99000"},
                {"TDS Payable 194C", "-1000"}});
        var result = engine.analyze(inputQ1(List.of(v), stdSnaps("ABCPE1234F")));
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
                () -> input(List.of(v), stdSnaps("ABCPE1234F"), FY_START, LocalDate.of(2027, 4, 1)));
    }
}
