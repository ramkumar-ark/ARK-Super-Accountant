package com.arktech.superaccountant.tds.engine;

import com.arktech.superaccountant.daybook.models.ParsedVoucher;
import com.arktech.superaccountant.masters.models.LedgerCategory;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DebtorTdsReceivableScannerTest {

    private final LocalDate date = LocalDate.of(2026, 5, 10);

    @Test
    void flagsDebtorWithReceivableAndNoTan() {
        ParsedVoucher voucher = TdsEngineFixtures.voucher("J-1", date, "Acme Corp", new Object[][]{
                {"TDS Receivable 194J", "1000.00"},
                {"Acme Corp", "-1000.00"}});

        List<DebtorTdsReceivableScanner.DebtorTanGap> gaps = DebtorTdsReceivableScanner.scan(
                List.of(voucher), Map.of("acme corp", LedgerCategory.OTHER),
                Set.of("acme corp"), Map.of());

        assertThat(gaps).hasSize(1);
        DebtorTdsReceivableScanner.DebtorTanGap gap = gaps.get(0);
        assertThat(gap.debtorLedgerName()).isEqualTo("Acme Corp");
        assertThat(gap.receivableLedgerName()).isEqualTo("TDS Receivable 194J");
        assertThat(gap.voucherId()).isEqualTo(voucher.getId());
        assertThat(gap.voucherNumber()).isEqualTo("J-1");
        assertThat(gap.voucherDate()).isEqualTo(date);
        assertThat(gap.deducted()).isEqualByComparingTo("1000.00");
    }

    @Test
    void skipsDebtorWithStoredTan() {
        ParsedVoucher voucher = TdsEngineFixtures.voucher("J-1", date, "Acme Corp", new Object[][]{
                {"TDS Receivable 194J", "1000.00"},
                {"Acme Corp", "-1000.00"}});

        List<DebtorTdsReceivableScanner.DebtorTanGap> gaps = DebtorTdsReceivableScanner.scan(
                List.of(voucher), Map.of("acme corp", LedgerCategory.OTHER),
                Set.of("acme corp"), Map.of("acme corp", "BLRA12345C"));

        assertThat(gaps).isEmpty();
    }

    @Test
    void oneGapPerVoucherAndDebtor() {
        ParsedVoucher voucher = TdsEngineFixtures.voucher("J-1", date, "Acme Corp", new Object[][]{
                {"TDS Receivable 194J", "1000.00"},
                {"TDS Receivable 194C", "500.00"},
                {"Acme Corp", "-1500.00"}});

        List<DebtorTdsReceivableScanner.DebtorTanGap> gaps = DebtorTdsReceivableScanner.scan(
                List.of(voucher), Map.of("acme corp", LedgerCategory.OTHER),
                Set.of("acme corp"), Map.of());

        assertThat(gaps).hasSize(1);
        assertThat(gaps.get(0).deducted()).isEqualByComparingTo(new BigDecimal("1000.00"));
    }
}
