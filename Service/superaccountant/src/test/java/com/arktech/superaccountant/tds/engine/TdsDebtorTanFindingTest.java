package com.arktech.superaccountant.tds.engine;

import com.arktech.superaccountant.daybook.models.ParsedVoucher;
import com.arktech.superaccountant.masters.models.FindingSeverity;
import com.arktech.superaccountant.masters.models.LedgerCategory;
import com.arktech.superaccountant.masters.models.ResolveStatus;
import com.arktech.superaccountant.masters.models.UploadLedgerSnapshot;
import com.arktech.superaccountant.tds.mapping.TdsLedgerMapping;
import com.arktech.superaccountant.tds.mapping.TdsLedgerRole;
import com.arktech.superaccountant.tds.models.TdsFinding;
import com.arktech.superaccountant.tds.models.TdsFindingType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TdsDebtorTanFindingTest {

    private final TdsEngine engine = new TdsEngine();
    private final LocalDate date = LocalDate.of(2026, 5, 10);

    @Test
    void flagsDebtorWithTdsReceivableEntryAndNoStoredTan() {
        TdsAnalysisResult result = engine.analyze(input(Map.of(),
                List.of(voucher("J-1", new String[][]{
                        {"TDS Receivable 194J", "1000.00"},
                        {"Acme Corp", "-1000.00"}}))));

        List<TdsFinding> findings = debtorTanFindings(result);
        assertThat(findings).hasSize(1);
        TdsFinding finding = findings.get(0);
        assertThat(finding.getFindingType())
                .isEqualTo(TdsFindingType.DEBTOR_TDS_RECEIVABLE_MISSING_TAN);
        assertThat(finding.getSeverity()).isEqualTo(FindingSeverity.LOW);
        assertThat(finding.getPartyLedgerName()).isEqualTo("Acme Corp");
        assertThat(finding.getActualAmount()).isEqualByComparingTo("1000.00");
        assertThat(finding.getResolveStatus()).isEqualTo(ResolveStatus.OPEN);
        assertThat(finding.getMessage()).isEqualTo(
                "Customer 'Acme Corp' has TDS deduction of ₹1000.00 debited to "
                        + "'TDS Receivable 194J' in voucher J-1, but no TAN is stored.");
        assertThat(finding.getSuggestedFix()).isEqualTo(
                "Enter the debtor's 10-character TAN (format AAAA99999A) on this finding "
                        + "or configure it in the Debtor TAN panel.");
    }

    @Test
    void staysSilentWhenTheDebtorHasAStoredTan() {
        TdsAnalysisResult result = engine.analyze(input(Map.of("acme corp", "BLRA12345C"),
                List.of(voucher("J-1", new String[][]{
                        {"TDS Receivable 194J", "1000.00"},
                        {"Acme Corp", "-1000.00"}}))));

        assertThat(debtorTanFindings(result)).isEmpty();
    }

    @Test
    void ignoresCounterpartiesThatAreNotSundryDebtors() {
        TdsAnalysisResult result = engine.analyze(input(Map.of(),
                List.of(voucher("J-1", new String[][]{
                        {"TDS Receivable 194J", "1000.00"},
                        {"Acme Corp", "-1000.00"}})), "Sundry Creditors"));

        assertThat(debtorTanFindings(result)).isEmpty();
    }

    @Test
    void ignoresVouchersWithoutATdsReceivableEntry() {
        TdsAnalysisResult result = engine.analyze(input(Map.of(),
                List.of(voucher("J-1", new String[][]{
                        {"Bank", "1000.00"},
                        {"Acme Corp", "-1000.00"}}))));

        assertThat(debtorTanFindings(result)).isEmpty();
    }

    @Test
    void deduplicatesOneFindingPerVoucherAndDebtor() {
        TdsAnalysisResult result = engine.analyze(input(Map.of(),
                List.of(voucher("J-1", new String[][]{
                        {"TDS Receivable 194J", "1000.00"},
                        {"TDS Receivable 194C", "500.00"},
                        {"Acme Corp", "-1500.00"}}))));

        assertThat(debtorTanFindings(result)).hasSize(1);
    }

    @Test
    void staysSilentWhenTheReceivableLedgerHasNoMappingRow() {
        TdsAnalysisResult result = engine.analyze(input(Map.of(),
                List.of(voucher("J-1", new String[][]{
                        {"TDS Receivable 194J", "1000.00"},
                        {"Acme Corp", "-1000.00"}})),
                "Sundry Debtors|Current Assets", Map.of()));

        assertThat(debtorTanFindings(result)).isEmpty();
    }

    private static List<TdsFinding> debtorTanFindings(TdsAnalysisResult result) {
        return result.findings().stream()
                .filter(f -> f.getFindingType() == TdsFindingType.DEBTOR_TDS_RECEIVABLE_MISSING_TAN)
                .toList();
    }

    private ParsedVoucher voucher(String number, String[][] rows) {
        Object[][] converted = new Object[rows.length][];
        for (int i = 0; i < rows.length; i++) {
            converted[i] = new Object[]{rows[i][0], rows[i][1]};
        }
        return TdsEngineFixtures.voucher(number, date, "Acme Corp", converted);
    }

    private TdsAnalysisInput input(Map<String, String> debtorTans, List<ParsedVoucher> vouchers) {
        return input(debtorTans, vouchers, "Sundry Debtors|Current Assets");
    }

    private TdsAnalysisInput input(Map<String, String> debtorTans, List<ParsedVoucher> vouchers,
                                   String debtorGroupChain) {
        return input(debtorTans, vouchers, debtorGroupChain, receivableMappings());
    }

    private TdsAnalysisInput input(Map<String, String> debtorTans, List<ParsedVoucher> vouchers,
                                   String debtorGroupChain, Map<String, TdsLedgerMapping> mappingsByKey) {
        Map<String, UploadLedgerSnapshot> snaps = new LinkedHashMap<>();
        UploadLedgerSnapshot receivable = TdsEngineFixtures.snap(
                "TDS Receivable 194J", LedgerCategory.OTHER, null, null, null);
        snaps.put(TdsEngine.key(receivable.getLedgerName()), receivable);
        UploadLedgerSnapshot debtor = TdsEngineFixtures.snap(
                "Acme Corp", LedgerCategory.OTHER, null, null, null);
        debtor.setGroupChain(debtorGroupChain);
        snaps.put(TdsEngine.key(debtor.getLedgerName()), debtor);
        return new TdsAnalysisInput(TdsEngineFixtures.ORG, TdsEngineFixtures.WF, TdsEngineFixtures.JOB,
                vouchers, snaps, mappingsByKey, debtorTans, TdsEngineFixtures.CONFIGS, TdsEngineFixtures.FY_START,
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 6, 30));
    }

    private static Map<String, TdsLedgerMapping> receivableMappings() {
        return TdsEngineFixtures.mappings(
                TdsEngineFixtures.mapping("TDS Receivable 194J", TdsLedgerRole.TDS_RECEIVABLE, null),
                TdsEngineFixtures.mapping("TDS Receivable 194C", TdsLedgerRole.TDS_RECEIVABLE, null));
    }
}
