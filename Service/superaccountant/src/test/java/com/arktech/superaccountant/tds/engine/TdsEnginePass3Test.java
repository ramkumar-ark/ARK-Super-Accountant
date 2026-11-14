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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.arktech.superaccountant.tds.engine.TdsEngineFixtures.inputQ1;
import static com.arktech.superaccountant.tds.engine.TdsEngineFixtures.snap;
import static com.arktech.superaccountant.tds.engine.TdsEngineFixtures.stdSnaps;
import static com.arktech.superaccountant.tds.engine.TdsEngineFixtures.voucher;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TdsEnginePass3Test {

    private final TdsEngine engine = new TdsEngine();
    private static final String PAN = "ABCPE1234F";

    private static Map<String, UploadLedgerSnapshot> snapsWith(BigDecimal opening) {
        Map<String, UploadLedgerSnapshot> m = new LinkedHashMap<>(stdSnaps(PAN));
        m.put(TdsEngine.key("Bank"), snap("Bank", LedgerCategory.OTHER, null, null, null));
        if (opening != null) {
            m.put(TdsEngine.key("ABC Contractors"),
                    snap("ABC Contractors", LedgerCategory.CREDITOR, null, null, PAN, opening));
        }
        return m;
    }

    private static ParsedVoucher bill(String no, LocalDate date, String amount) {
        return voucher(no, date, "ABC Contractors", new Object[][]{
                {"Subcontractor Charges", amount}, {"ABC Contractors", "-" + amount}});
    }

    private static ParsedVoucher payment(String no, LocalDate date, String amount, boolean withTds) {
        BigDecimal amt = new BigDecimal(amount);
        if (withTds) {
            return voucher(no, date, "ABC Contractors", new Object[][]{
                    {"ABC Contractors", amount}, {"Bank", "-" + amt.subtract(new BigDecimal("1000"))},
                    {"TDS Payable 194C", "-1000"}});
        }
        return voucher(no, date, "ABC Contractors", new Object[][]{
                {"ABC Contractors", amount}, {"Bank", "-" + amount}});
    }

    private static List<TdsFinding> advances(TdsAnalysisResult r) {
        return r.findings().stream()
                .filter(f -> f.getFindingType() == TdsFindingType.ADVANCE_PAYMENT).toList();
    }

    @Test
    void advanceAboveThreshold_flaggedWithInferredSection() {
        var apr = bill("V1", LocalDate.of(2026, 4, 10), "50000");
        var may = payment("V2", LocalDate.of(2026, 5, 10), "90000", false);
        var result = engine.analyze(inputQ1(List.of(apr, may), snapsWith(null)));
        assertEquals(1, advances(result).size());
        TdsFinding f = advances(result).get(0);
        assertEquals(FindingSeverity.MEDIUM, f.getSeverity());
        assertEquals("194C", f.getSection());
        assertEquals(may.getId(), f.getVoucherId());
        assertEquals(new BigDecimal("400.00"), f.getExpectedAmount());
    }

    @Test
    void advanceBelowThresholds_notFlagged() {
        var apr = bill("V1", LocalDate.of(2026, 4, 10), "50000");
        var may = payment("V2", LocalDate.of(2026, 5, 10), "60000", false);
        var result = engine.analyze(inputQ1(List.of(apr, may), snapsWith(null)));
        assertTrue(advances(result).isEmpty());
    }

    @Test
    void openingCreditBalance_suppressesFalseAdvance() {
        var may = payment("V1", LocalDate.of(2026, 5, 10), "40000", false);
        var result = engine.analyze(inputQ1(List.of(may), snapsWith(new BigDecimal("-50000"))));
        assertTrue(advances(result).isEmpty());
    }

    @Test
    void noBillHistory_askUserFinding() {
        var may = payment("V1", LocalDate.of(2026, 5, 10), "60000", false);
        var result = engine.analyze(inputQ1(List.of(may), snapsWith(null)));
        assertEquals(1, advances(result).size());
        TdsFinding f = advances(result).get(0);
        assertNull(f.getSection());
        assertNull(f.getExpectedAmount());
    }

    @Test
    void advanceOpenAtAprilEndSettledInMay_stillFlagged() {
        var apr = payment("V1", LocalDate.of(2026, 4, 10), "40000", false);
        var may = bill("V2", LocalDate.of(2026, 5, 10), "40000");
        var result = engine.analyze(inputQ1(List.of(apr, may), snapsWith(null)));
        assertEquals(1, advances(result).size());
        assertEquals(apr.getId(), advances(result).get(0).getVoucherId());
    }

    @Test
    void advanceWithTdsEntry_notFlagged() {
        var apr = bill("V1", LocalDate.of(2026, 4, 10), "50000");
        var may = payment("V2", LocalDate.of(2026, 5, 10), "90000", true);
        var result = engine.analyze(inputQ1(List.of(apr, may), snapsWith(null)));
        assertTrue(advances(result).isEmpty());
    }

    @Test
    void earlierPeriodPayment_notReflagged() {
        var apr = payment("V1", LocalDate.of(2026, 4, 10), "40000", false);
        var result = engine.analyze(TdsEngineFixtures.input(List.of(apr), snapsWith(null),
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30)));
        assertTrue(advances(result).isEmpty());
    }

    @Test
    void paymentAfterPeriodEnd_notCountedInBalance() {
        var jun = bill("V1", LocalDate.of(2026, 6, 10), "50000");
        var jul = payment("V2", LocalDate.of(2026, 7, 10), "90000", false);
        var result = engine.analyze(inputQ1(List.of(jun, jul), snapsWith(null)));
        assertTrue(advances(result).isEmpty());
    }
}
