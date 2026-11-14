package com.arktech.superaccountant.tds.engine;

import com.arktech.superaccountant.daybook.models.ParsedVoucher;
import com.arktech.superaccountant.masters.models.FindingSeverity;
import com.arktech.superaccountant.masters.models.LedgerCategory;
import com.arktech.superaccountant.masters.models.UploadLedgerSnapshot;
import com.arktech.superaccountant.tds.models.TdsFinding;
import com.arktech.superaccountant.tds.models.TdsFindingType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.arktech.superaccountant.tds.engine.TdsEngineFixtures.input;
import static com.arktech.superaccountant.tds.engine.TdsEngineFixtures.inputQ1;
import static com.arktech.superaccountant.tds.engine.TdsEngineFixtures.snap;
import static com.arktech.superaccountant.tds.engine.TdsEngineFixtures.stdSnaps;
import static com.arktech.superaccountant.tds.engine.TdsEngineFixtures.voucher;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TdsEnginePass2Test {

    private final TdsEngine engine = new TdsEngine();
    private static final String PAN = "ABCPE1234F";

    private static Map<String, UploadLedgerSnapshot> snapsWith(String expenseLedger, String creditorLedger,
                                                               String pan) {
        Map<String, UploadLedgerSnapshot> m = new LinkedHashMap<>(stdSnaps(pan));
        UploadLedgerSnapshot exp = snap(expenseLedger, LedgerCategory.EXPENSE, true, "194J_B", null);
        UploadLedgerSnapshot cred = snap(creditorLedger, LedgerCategory.CREDITOR, null, null, pan);
        UploadLedgerSnapshot tds = snap("TDS Payable 194J_B", LedgerCategory.TDS, true, "194J_B", null);
        m.put(TdsEngine.key(expenseLedger), exp);
        m.put(TdsEngine.key(creditorLedger), cred);
        m.put(TdsEngine.key(tds.getLedgerName()), tds);
        return m;
    }

    private static ParsedVoucher bill(String no, LocalDate date, String expenseLedger, String creditorLedger,
                                      String amount, boolean deducted) {
        List<Object[]> rows = new ArrayList<>();
        rows.add(new Object[]{expenseLedger, amount});
        rows.add(new Object[]{creditorLedger, "-" + amount});
        if (deducted) {
            BigDecimal tds = new BigDecimal(amount).multiply(new BigDecimal("0.10"));
            rows.add(new Object[]{"TDS Payable 194J_B", "-" + tds.toPlainString()});
        }
        return voucher(no, date, creditorLedger, rows.toArray(Object[][]::new));
    }

    private static ParsedVoucher bill194c(String no, LocalDate date, String amount, boolean deducted) {
        List<Object[]> rows = new ArrayList<>();
        rows.add(new Object[]{"Subcontractor Charges", amount});
        rows.add(new Object[]{"ABC Contractors", "-" + amount});
        if (deducted) {
            BigDecimal tds = new BigDecimal(amount).multiply(new BigDecimal("0.01"));
            rows.add(new Object[]{"TDS Payable 194C", "-" + tds.toPlainString()});
        }
        return voucher(no, date, "ABC Contractors", rows.toArray(Object[][]::new));
    }

    private List<TdsFinding> findingsOf(TdsAnalysisResult result) {
        return result.findings();
    }

    @Test
    void perTransactionLimit_notDeducted() {
        var v = bill194c("V1", LocalDate.of(2026, 5, 10), "35000", false);
        var result = engine.analyze(inputQ1(List.of(v), stdSnaps(PAN)));
        assertEquals(1, findingsOf(result).size());
        TdsFinding f = findingsOf(result).get(0);
        assertEquals(TdsFindingType.NOT_DEDUCTED, f.getFindingType());
        assertEquals(new BigDecimal("350.00"), f.getExpectedAmount());
    }

    @Test
    void belowBothLimits_noFinding() {
        var v = bill194c("V1", LocalDate.of(2026, 5, 10), "20000", false);
        var result = engine.analyze(inputQ1(List.of(v), stdSnaps(PAN)));
        assertTrue(findingsOf(result).isEmpty());
    }

    @Test
    void annualCrossing_oneRetroFinding() {
        Map<String, UploadLedgerSnapshot> snaps = snapsWith("Professional Fees", "ABC Contractors", PAN);
        var may = bill("V1", LocalDate.of(2026, 5, 10), "Professional Fees", "ABC Contractors", "12000", false);
        var jun = bill("V2", LocalDate.of(2026, 6, 10), "Professional Fees", "ABC Contractors", "10000", false);
        var jul = bill("V3", LocalDate.of(2026, 7, 10), "Professional Fees", "ABC Contractors", "15000", false);
        var result = engine.analyze(input(List.of(may, jun, jul), snaps,
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31)));
        assertEquals(1, findingsOf(result).size());
        TdsFinding f = findingsOf(result).get(0);
        assertEquals(TdsFindingType.THRESHOLD_CROSSED, f.getFindingType());
        assertEquals(FindingSeverity.HIGH, f.getSeverity());
        assertEquals(jul.getId(), f.getVoucherId());
        assertTrue(f.getRelatedVoucherIds().contains(may.getId().toString()));
        assertTrue(f.getRelatedVoucherIds().contains(jun.getId().toString()));
        assertEquals(new BigDecimal("3700.00"), f.getExpectedAmount());
        assertEquals(BigDecimal.ZERO.setScale(2), f.getActualAmount());
    }

    @Test
    void afterCrossing_individualNotDeducted() {
        Map<String, UploadLedgerSnapshot> snaps = snapsWith("Professional Fees", "ABC Contractors", PAN);
        var jul = bill("V1", LocalDate.of(2026, 7, 10), "Professional Fees", "ABC Contractors", "20000", false);
        var aug = bill("V2", LocalDate.of(2026, 8, 10), "Professional Fees", "ABC Contractors", "15000", false);
        var sep = bill("V3", LocalDate.of(2026, 9, 10), "Professional Fees", "ABC Contractors", "5000", false);
        var result = engine.analyze(input(List.of(jul, aug, sep), snaps,
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 9, 30)));
        assertEquals(2, findingsOf(result).size());
        TdsFinding crossed = findingsOf(result).stream()
                .filter(f -> f.getFindingType() == TdsFindingType.THRESHOLD_CROSSED).findFirst().orElseThrow();
        assertEquals(aug.getId(), crossed.getVoucherId());
        assertTrue(crossed.getRelatedVoucherIds().contains(jul.getId().toString()));
        TdsFinding notDeducted = findingsOf(result).stream()
                .filter(f -> f.getFindingType() == TdsFindingType.NOT_DEDUCTED).findFirst().orElseThrow();
        assertEquals(sep.getId(), notDeducted.getVoucherId());
        assertEquals(new BigDecimal("500.00"), notDeducted.getExpectedAmount());
    }

    @Test
    void deductedVouchers_noThresholdFinding() {
        var a = bill194c("V1", LocalDate.of(2026, 5, 10), "40000", true);
        var b = bill194c("V2", LocalDate.of(2026, 6, 10), "70000", true);
        var result = engine.analyze(inputQ1(List.of(a, b), stdSnaps(PAN)));
        assertTrue(findingsOf(result).isEmpty());
    }

    @Test
    void crossingInEarlierPeriod_onlyPostCrossingInPeriodNotDeducted() {
        Map<String, UploadLedgerSnapshot> snaps = snapsWith("Professional Fees", "ABC Contractors", PAN);
        var may = bill("V1", LocalDate.of(2026, 5, 10), "Professional Fees", "ABC Contractors", "20000", false);
        var jun = bill("V2", LocalDate.of(2026, 6, 10), "Professional Fees", "ABC Contractors", "15000", false);
        var aug = bill("V3", LocalDate.of(2026, 8, 10), "Professional Fees", "ABC Contractors", "5000", false);
        var result = engine.analyze(input(List.of(may, jun, aug), snaps,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31)));
        assertEquals(1, findingsOf(result).size());
        TdsFinding f = findingsOf(result).get(0);
        assertEquals(TdsFindingType.NOT_DEDUCTED, f.getFindingType());
        assertEquals(aug.getId(), f.getVoucherId());
        assertEquals(new BigDecimal("500.00"), f.getExpectedAmount());
    }

    @Test
    void groupsByPartyAndSectionIndependently() {
        Map<String, UploadLedgerSnapshot> snaps = snapsWith("Professional Fees", "ABC Contractors", PAN);
        snaps.put(TdsEngine.key("XYZ Traders"),
                snap("XYZ Traders", LedgerCategory.CREDITOR, null, null, PAN));
        var xJul = bill("V1", LocalDate.of(2026, 7, 10), "Professional Fees", "ABC Contractors", "20000", false);
        var xAug = bill("V2", LocalDate.of(2026, 8, 10), "Professional Fees", "ABC Contractors", "15000", false);
        var yAug = bill("V3", LocalDate.of(2026, 8, 10), "Professional Fees", "XYZ Traders", "25000", false);
        var result = engine.analyze(input(List.of(xJul, xAug, yAug), snaps,
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 9, 30)));
        assertEquals(1, findingsOf(result).stream()
                .filter(f -> f.getFindingType() == TdsFindingType.THRESHOLD_CROSSED).count());
        assertTrue(findingsOf(result).stream()
                .noneMatch(f -> f.getPartyLedgerName() != null && f.getPartyLedgerName().equals("XYZ Traders")));
    }
}
