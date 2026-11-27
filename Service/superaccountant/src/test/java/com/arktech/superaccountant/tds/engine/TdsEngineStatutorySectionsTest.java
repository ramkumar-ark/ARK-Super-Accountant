package com.arktech.superaccountant.tds.engine;

import com.arktech.superaccountant.daybook.models.ParsedVoucher;
import com.arktech.superaccountant.masters.models.LedgerCategory;
import com.arktech.superaccountant.masters.models.UploadLedgerSnapshot;
import com.arktech.superaccountant.tds.mapping.TdsLedgerMapping;
import com.arktech.superaccountant.tds.mapping.TdsLedgerRole;
import com.arktech.superaccountant.tds.models.TdsFinding;
import com.arktech.superaccountant.tds.models.TdsFindingType;
import com.arktech.superaccountant.tds.models.TdsSectionConfig;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.arktech.superaccountant.tds.engine.TdsEngineFixtures.FY_START;
import static com.arktech.superaccountant.tds.engine.TdsEngineFixtures.JOB;
import static com.arktech.superaccountant.tds.engine.TdsEngineFixtures.ORG;
import static com.arktech.superaccountant.tds.engine.TdsEngineFixtures.WF;
import static com.arktech.superaccountant.tds.engine.TdsEngineFixtures.mapping;
import static com.arktech.superaccountant.tds.engine.TdsEngineFixtures.mappings;
import static com.arktech.superaccountant.tds.engine.TdsEngineFixtures.snap;
import static com.arktech.superaccountant.tds.engine.TdsEngineFixtures.voucher;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Engine behaviour for the PR4 statutory sections: the payee-split 194C rate (firms at 2%),
 * the rent monthly threshold, 194Q's excess-only deduction, the newly seeded 194D / 193 / 194T
 * and the unmapped-section warning. Configs are built the way TdsAnalysisInputFactory builds
 * them from the statutory rule rows.
 */
class TdsEngineStatutorySectionsTest {

    private final TdsEngine engine = new TdsEngine();

    private static final LocalDate Q1_START = LocalDate.of(2026, 4, 1);
    private static final LocalDate Q1_END = LocalDate.of(2026, 6, 30);

    private static TdsSectionConfig cfg(String code, String rateCompany, String rateOther, String annual) {
        TdsSectionConfig c = TdsEngineFixtures.config(code, rateCompany, rateOther, null, annual);
        return c;
    }

    /** One expense ledger, one creditor with the given PAN and one TDS payable ledger, all for {@code section}. */
    private static Map<String, UploadLedgerSnapshot> snaps(String section, String pan) {
        Map<String, UploadLedgerSnapshot> m = new LinkedHashMap<>();
        UploadLedgerSnapshot exp = snap("Expense " + section, LedgerCategory.EXPENSE, true, section, null);
        UploadLedgerSnapshot cred = snap("Party", LedgerCategory.CREDITOR, null, null, pan);
        UploadLedgerSnapshot tds = snap("TDS Payable " + section, LedgerCategory.TDS, true, section, null);
        m.put(TdsEngine.key(exp.getLedgerName()), exp);
        m.put(TdsEngine.key(cred.getLedgerName()), cred);
        m.put(TdsEngine.key(tds.getLedgerName()), tds);
        return m;
    }

    private static Map<String, TdsLedgerMapping> maps(String section) {
        return mappings(mapping("TDS Payable " + section, TdsLedgerRole.TDS_PAYABLE, section));
    }

    private static ParsedVoucher bill(String no, LocalDate date, String section, String gross, String tds) {
        BigDecimal g = new BigDecimal(gross);
        BigDecimal t = new BigDecimal(tds);
        Object[][] rows = t.signum() == 0
                ? new Object[][]{{"Expense " + section, gross}, {"Party", g.negate().toPlainString()}}
                : new Object[][]{{"Expense " + section, gross},
                        {"Party", g.subtract(t).negate().toPlainString()},
                        {"TDS Payable " + section, t.negate().toPlainString()}};
        return voucher(no, date, "Party", rows);
    }

    private TdsAnalysisResult run(Map<String, TdsSectionConfig> configs, String section, String pan,
                                  List<ParsedVoucher> vouchers) {
        return engine.analyze(new TdsAnalysisInput(ORG, WF, JOB, vouchers, snaps(section, pan), maps(section),
                Map.of(), configs, FY_START, Q1_START, Q1_END, Map.of()));
    }

    private static List<TdsFindingType> types(TdsAnalysisResult r) {
        return r.findings().stream().map(TdsFinding::getFindingType).toList();
    }

    // ------------------------------------------------------------------ 194C payee split

    @Test
    void s194C_firmPanIsDeductedAtTwoPercent() {
        // PAN 4th character F = firm: not an individual/HUF, so the 2% rate applies, not 1%.
        var configs = TdsEngineFixtures.CONFIGS;
        var ok = run(configs, "194C", "ABCFE1234F", List.of(bill("V1", LocalDate.of(2026, 5, 10), "194C", "100000", "2000")));
        assertTrue(ok.findings().isEmpty(), () -> "unexpected " + types(ok));

        var short1 = run(configs, "194C", "ABCFE1234F", List.of(bill("V1", LocalDate.of(2026, 5, 10), "194C", "100000", "1000")));
        assertEquals(List.of(TdsFindingType.SHORT_DEDUCTED), types(short1));
        assertEquals(new BigDecimal("2000.00"), short1.findings().get(0).getExpectedAmount());
    }

    @Test
    void s194C_llpAopAndTrustPansAreAlsoTwoPercent() {
        for (String pan : List.of("ABCLE1234F", "ABCAE1234F", "ABCTE1234F")) {
            var r = run(TdsEngineFixtures.CONFIGS, "194C", pan,
                    List.of(bill("V1", LocalDate.of(2026, 5, 10), "194C", "100000", "2000")));
            assertTrue(r.findings().isEmpty(), () -> pan + " -> " + types(r));
        }
    }

    @Test
    void s194C_individualAndHufStayAtOnePercent() {
        for (String pan : List.of("ABCPE1234F", "ABCHE1234F")) {
            var r = run(TdsEngineFixtures.CONFIGS, "194C", pan,
                    List.of(bill("V1", LocalDate.of(2026, 5, 10), "194C", "100000", "1000")));
            assertTrue(r.findings().isEmpty(), () -> pan + " -> " + types(r));
        }
    }

    // ------------------------------------------------------------------ rent: monthly threshold

    private static Map<String, TdsSectionConfig> rentConfigs() {
        TdsSectionConfig rent = cfg("194I_B", "10.00", "10.00", "240000.00");
        rent.setThresholdAnnual(null);
        rent.setThresholdMonthly(new BigDecimal("50000.00"));
        return Map.of("194I_B", rent);
    }

    @Test
    void rent_sixtyThousandInOneMonthCrossesTheMonthlyThreshold() {
        var r = run(rentConfigs(), "194I_B", "ABCPE1234F",
                List.of(bill("R1", LocalDate.of(2026, 5, 5), "194I_B", "60000", "0")));
        assertEquals(List.of(TdsFindingType.THRESHOLD_CROSSED), types(r));
        assertEquals(new BigDecimal("6000.00"), r.findings().get(0).getExpectedAmount());
    }

    @Test
    void rent_fiftyThousandInOneMonthIsNotLiable() {
        var r = run(rentConfigs(), "194I_B", "ABCPE1234F",
                List.of(bill("R1", LocalDate.of(2026, 5, 5), "194I_B", "50000", "0")));
        assertTrue(r.findings().isEmpty(), () -> "unexpected " + types(r));
    }

    @Test
    void rent_fiftyThousandEveryMonthNeverCrossesEvenThoughTheYearDoes() {
        var r = run(rentConfigs(), "194I_B", "ABCPE1234F", List.of(
                bill("R1", LocalDate.of(2026, 4, 5), "194I_B", "50000", "0"),
                bill("R2", LocalDate.of(2026, 5, 5), "194I_B", "50000", "0"),
                bill("R3", LocalDate.of(2026, 6, 5), "194I_B", "50000", "0")));
        assertTrue(r.findings().isEmpty(), () -> "unexpected " + types(r));
    }

    @Test
    void rent_twoPaymentsInTheSameMonthAreAddedTogether() {
        var r = run(rentConfigs(), "194I_B", "ABCPE1234F", List.of(
                bill("R1", LocalDate.of(2026, 5, 2), "194I_B", "30000", "0"),
                bill("R2", LocalDate.of(2026, 5, 20), "194I_B", "30000", "0")));
        assertEquals(List.of(TdsFindingType.THRESHOLD_CROSSED), types(r));
        assertTrue(r.findings().get(0).getMessage().contains("monthly threshold"));
    }

    // ------------------------------------------------------------------ 194Q: excess only

    private static Map<String, TdsSectionConfig> purchaseConfigs() {
        TdsSectionConfig c = cfg("194Q", "0.10", "0.10", "5000000.00");
        c.setThresholdExcessOnly(true);
        return Map.of("194Q", c);
    }

    @Test
    void s194Q_deductsOnlyOnThePortionAboveFiftyLakh() {
        var ok = run(purchaseConfigs(), "194Q", "ABCCE1234F", List.of(
                bill("P1", LocalDate.of(2026, 4, 10), "194Q", "4000000", "0"),
                bill("P2", LocalDate.of(2026, 5, 10), "194Q", "2000000", "1000")));
        assertTrue(ok.findings().isEmpty(), () -> "unexpected " + types(ok));
    }

    @Test
    void s194Q_deductingOnTheWholeCrossingPurchaseIsAnExcess() {
        var r = run(purchaseConfigs(), "194Q", "ABCCE1234F", List.of(
                bill("P1", LocalDate.of(2026, 4, 10), "194Q", "4000000", "0"),
                bill("P2", LocalDate.of(2026, 5, 10), "194Q", "2000000", "2000")));
        assertEquals(List.of(TdsFindingType.EXCESS_DEDUCTED), types(r));
        assertEquals(new BigDecimal("1000.00"), r.findings().get(0).getExpectedAmount());
    }

    @Test
    void s194Q_crossingPurchaseWithNoDeductionIsFlaggedForTheExcessOnly() {
        var r = run(purchaseConfigs(), "194Q", "ABCCE1234F", List.of(
                bill("P1", LocalDate.of(2026, 4, 10), "194Q", "4000000", "0"),
                bill("P2", LocalDate.of(2026, 5, 10), "194Q", "2000000", "0")));
        assertEquals(List.of(TdsFindingType.THRESHOLD_CROSSED), types(r));
        assertEquals(new BigDecimal("1000.00"), r.findings().get(0).getExpectedAmount());
    }

    @Test
    void s194Q_laterPurchasesAboveTheThresholdAreTaxedInFull() {
        var r = run(purchaseConfigs(), "194Q", "ABCCE1234F", List.of(
                bill("P1", LocalDate.of(2026, 4, 10), "194Q", "6000000", "1000"),
                bill("P2", LocalDate.of(2026, 5, 10), "194Q", "1000000", "0")));
        assertEquals(List.of(TdsFindingType.NOT_DEDUCTED), types(r));
        assertEquals(new BigDecimal("1000.00"), r.findings().get(0).getExpectedAmount());
    }

    @Test
    void s194Q_belowTheThresholdNothingIsExpected() {
        var r = run(purchaseConfigs(), "194Q", "ABCCE1234F",
                List.of(bill("P1", LocalDate.of(2026, 4, 10), "194Q", "4000000", "0")));
        assertTrue(r.findings().isEmpty(), () -> "unexpected " + types(r));
    }

    // ------------------------------------------------------------------ 194D / 193 / 194T

    @Test
    void s194D_usesTenPercentForCompaniesAndTwoPercentForOthers() {
        var configs = Map.of("194D", cfg("194D", "10.00", "2.00", "20000.00"));
        var company = run(configs, "194D", "ABCCE1234F",
                List.of(bill("C1", LocalDate.of(2026, 5, 10), "194D", "50000", "5000")));
        assertTrue(company.findings().isEmpty(), () -> "unexpected " + types(company));
        var other = run(configs, "194D", "ABCPE1234F",
                List.of(bill("C1", LocalDate.of(2026, 5, 10), "194D", "50000", "1000")));
        assertTrue(other.findings().isEmpty(), () -> "unexpected " + types(other));
        var notDeducted = run(configs, "194D", "ABCPE1234F",
                List.of(bill("C1", LocalDate.of(2026, 5, 10), "194D", "50000", "0")));
        assertEquals(List.of(TdsFindingType.THRESHOLD_CROSSED), types(notDeducted));
        assertEquals(new BigDecimal("1000.00"), notDeducted.findings().get(0).getExpectedAmount());
    }

    @Test
    void s193_tenPercentOnceTenThousandIsCrossed() {
        var configs = Map.of("193", cfg("193", "10.00", "10.00", "10000.00"));
        var below = run(configs, "193", "ABCPE1234F",
                List.of(bill("I1", LocalDate.of(2026, 5, 10), "193", "9000", "0")));
        assertTrue(below.findings().isEmpty(), () -> "unexpected " + types(below));
        var over = run(configs, "193", "ABCPE1234F",
                List.of(bill("I1", LocalDate.of(2026, 5, 10), "193", "25000", "0")));
        assertEquals(List.of(TdsFindingType.THRESHOLD_CROSSED), types(over));
        assertEquals(new BigDecimal("2500.00"), over.findings().get(0).getExpectedAmount());
    }

    @Test
    void s194T_tenPercentOnPartnerPaymentsAboveTwentyThousand() {
        var configs = Map.of("194T", cfg("194T", "10.00", "10.00", "20000.00"));
        var below = run(configs, "194T", "ABCPE1234F",
                List.of(bill("T1", LocalDate.of(2026, 5, 10), "194T", "20000", "0")));
        assertTrue(below.findings().isEmpty(), () -> "unexpected " + types(below));
        var ok = run(configs, "194T", "ABCPE1234F",
                List.of(bill("T1", LocalDate.of(2026, 5, 10), "194T", "100000", "10000")));
        assertTrue(ok.findings().isEmpty(), () -> "unexpected " + types(ok));
        var short1 = run(configs, "194T", "ABCPE1234F",
                List.of(bill("T1", LocalDate.of(2026, 5, 10), "194T", "100000", "5000")));
        assertEquals(List.of(TdsFindingType.SHORT_DEDUCTED), types(short1));
    }

    // ------------------------------------------------------------------ unmapped section

    @Test
    void sectionWithoutConfig_warnsOncePerSectionInsteadOfSkippingSilently() {
        var r = run(Map.of(), "194ZZ", "ABCPE1234F", List.of(
                bill("X1", LocalDate.of(2026, 5, 10), "194ZZ", "100000", "0"),
                bill("X2", LocalDate.of(2026, 5, 12), "194ZZ", "100000", "0")));
        assertEquals(List.of(TdsFindingType.SECTION_NOT_CONFIGURED), types(r));
        assertTrue(r.findings().get(0).getMessage().contains("194ZZ"));
    }
}
