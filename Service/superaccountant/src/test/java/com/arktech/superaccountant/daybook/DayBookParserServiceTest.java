package com.arktech.superaccountant.daybook;

import com.arktech.superaccountant.daybook.models.ParsedVoucher;
import com.arktech.superaccountant.daybook.models.ParsedVoucherEntry;
import com.arktech.superaccountant.daybook.repository.ParsedVoucherRepository;
import com.arktech.superaccountant.daybook.services.DayBookParserService;
import com.arktech.superaccountant.daybook.services.VoucherCanonicalizer;
import com.arktech.superaccountant.tally.models.GstRateDetail;
import com.arktech.superaccountant.tally.models.LedgerEntry;
import com.arktech.superaccountant.tally.models.TallyMessage;
import com.arktech.superaccountant.tally.models.Voucher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DayBookParserServiceTest {

    @Mock
    ParsedVoucherRepository parsedVoucherRepository;

    @InjectMocks
    DayBookParserService dayBookParserService;

    @Test
    void persistVouchers_normalizesAmountStringToBigDecimal() {
        LedgerEntry entry = new LedgerEntry();
        entry.setLedgername("Test Purchases");
        entry.setAmount("-10000");

        Voucher voucher = new Voucher();
        voucher.setVouchertypename("Purchase");
        voucher.setDate("20250401");
        voucher.setAllLedgerEntries(List.of(entry));
        voucher.setLedgerEntries(List.of());

        TallyMessage tallyMessage = new TallyMessage();
        tallyMessage.setTallymessage(List.of(voucher));

        when(parsedVoucherRepository.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));

        List<ParsedVoucher> result = dayBookParserService.persistVouchers(
                tallyMessage, UUID.randomUUID(), UUID.randomUUID());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getEntries()).hasSize(1);
        ParsedVoucherEntry persistedEntry = result.get(0).getEntries().get(0);
        assertThat(persistedEntry.getAmount()).isEqualByComparingTo(new BigDecimal("-10000"));
        assertThat(persistedEntry.isDebit()).isFalse();
    }

    @Test
    void persistVouchers_preservesDebitSignConvention() {
        LedgerEntry entry = new LedgerEntry();
        entry.setLedgername("Bank Account");
        entry.setAmount("54500");

        Voucher voucher = new Voucher();
        voucher.setVouchertypename("Payment");
        voucher.setDate("20250410");
        voucher.setAllLedgerEntries(List.of(entry));
        voucher.setLedgerEntries(List.of());

        TallyMessage tallyMessage = new TallyMessage();
        tallyMessage.setTallymessage(List.of(voucher));

        when(parsedVoucherRepository.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));

        List<ParsedVoucher> result = dayBookParserService.persistVouchers(
                tallyMessage, UUID.randomUUID(), UUID.randomUUID());

        assertThat(result).hasSize(1);
        ParsedVoucherEntry persistedEntry = result.get(0).getEntries().get(0);
        assertThat(persistedEntry.getAmount()).isEqualByComparingTo(new BigDecimal("54500"));
        assertThat(persistedEntry.isDebit()).isTrue();
    }

    @Test
    void persistVouchers_parsesYYYYMMDDDateToLocalDate() {
        LedgerEntry entry = new LedgerEntry();
        entry.setLedgername("Test Purchases");
        entry.setAmount("-10000");

        Voucher voucher = new Voucher();
        voucher.setVouchertypename("Purchase");
        voucher.setDate("20250401");
        voucher.setAllLedgerEntries(List.of(entry));
        voucher.setLedgerEntries(List.of());

        TallyMessage tallyMessage = new TallyMessage();
        tallyMessage.setTallymessage(List.of(voucher));

        when(parsedVoucherRepository.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));

        List<ParsedVoucher> result = dayBookParserService.persistVouchers(
                tallyMessage, UUID.randomUUID(), UUID.randomUUID());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getVoucherDate()).isEqualTo(LocalDate.of(2025, 4, 1));
    }

    @Test
    void persistVouchers_handlesNullDate() {
        LedgerEntry entry = new LedgerEntry();
        entry.setLedgername("Test Purchases");
        entry.setAmount("-10000");

        Voucher voucher = new Voucher();
        voucher.setVouchertypename("Purchase");
        voucher.setDate(null);
        voucher.setAllLedgerEntries(List.of(entry));
        voucher.setLedgerEntries(List.of());

        TallyMessage tallyMessage = new TallyMessage();
        tallyMessage.setTallymessage(List.of(voucher));

        when(parsedVoucherRepository.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));

        List<ParsedVoucher> result = dayBookParserService.persistVouchers(
                tallyMessage, UUID.randomUUID(), UUID.randomUUID());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getVoucherDate()).isNull();
    }

    @Test
    void persistVouchers_whenTallymessageNull_returnsEmptyList() {
        TallyMessage tallyMessage = new TallyMessage();
        tallyMessage.setTallymessage(null);

        List<ParsedVoucher> result = dayBookParserService.persistVouchers(
                tallyMessage, UUID.randomUUID(), UUID.randomUUID());

        assertThat(result).isEmpty();
    }

    @Test
    void persistVouchers_usesGetAllLedgerEntriesCombined() {
        LedgerEntry entry1 = new LedgerEntry();
        entry1.setLedgername("Test Purchases");
        entry1.setAmount("-10000");

        LedgerEntry entry2 = new LedgerEntry();
        entry2.setLedgername("Input CGST @9%");
        entry2.setAmount("-900");

        Voucher voucher = new Voucher();
        voucher.setVouchertypename("Purchase");
        voucher.setDate("20250401");
        // Populate allLedgerEntries (primary), leave ledgerEntries empty
        voucher.setAllLedgerEntries(List.of(entry1, entry2));
        voucher.setLedgerEntries(List.of());

        TallyMessage tallyMessage = new TallyMessage();
        tallyMessage.setTallymessage(List.of(voucher));

        when(parsedVoucherRepository.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));

        List<ParsedVoucher> result = dayBookParserService.persistVouchers(
                tallyMessage, UUID.randomUUID(), UUID.randomUUID());

        assertThat(result).hasSize(1);
        // Both entries from allledgerentries must be persisted
        assertThat(result.get(0).getEntries()).hasSize(2);
    }

    @Test
    void persistsGstRateSummedFromRateDetails() {
        LedgerEntry expense = new LedgerEntry();
        expense.setLedgername("Job Work Charges");
        expense.setAmount("10000.00");
        expense.setGstovrdntaxability("Taxable");
        GstRateDetail cgst = new GstRateDetail();
        cgst.setGstratedutyhead("Central Tax");
        cgst.setGstrate("9");
        GstRateDetail sgst = new GstRateDetail();
        sgst.setGstratedutyhead("State Tax");
        sgst.setGstrate("9");
        expense.setRatedetails(List.of(cgst, sgst));

        Voucher voucher = new Voucher();
        voucher.setVouchertypename("Purchase");
        voucher.setDate("20250401");
        voucher.setAllLedgerEntries(List.of(expense));
        voucher.setLedgerEntries(List.of());

        TallyMessage tallyMessage = new TallyMessage();
        tallyMessage.setTallymessage(List.of(voucher));

        when(parsedVoucherRepository.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));

        List<ParsedVoucher> saved = dayBookParserService.persistVouchers(
                tallyMessage, UUID.randomUUID(), UUID.randomUUID());

        ParsedVoucherEntry pve = saved.get(0).getEntries().get(0);
        assertThat(pve.getGstRate()).isEqualByComparingTo(new BigDecimal("18"));
        assertThat(pve.getGstTaxability()).isEqualTo("Taxable");
    }

    @Test
    void gstRateNullWhenNoRateDetails() {
        LedgerEntry entry = new LedgerEntry();
        entry.setLedgername("Input CGST");
        entry.setAmount("-900.00");

        Voucher voucher = new Voucher();
        voucher.setVouchertypename("Purchase");
        voucher.setDate("20250401");
        voucher.setAllLedgerEntries(List.of(entry));
        voucher.setLedgerEntries(List.of());

        TallyMessage tallyMessage = new TallyMessage();
        tallyMessage.setTallymessage(List.of(voucher));

        when(parsedVoucherRepository.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));

        List<ParsedVoucher> saved = dayBookParserService.persistVouchers(
                tallyMessage, UUID.randomUUID(), UUID.randomUUID());

        assertThat(saved.get(0).getEntries().get(0).getGstRate()).isNull();
        assertThat(saved.get(0).getEntries().get(0).getGstTaxability()).isNull();
    }

    @Test
    void gstRateIgnoresNonNumericRateDetail() {
        LedgerEntry expense = new LedgerEntry();
        expense.setLedgername("Job Work Charges");
        expense.setAmount("10000.00");
        expense.setGstovrdntaxability("Taxable");
        GstRateDetail cgst = new GstRateDetail();
        cgst.setGstratedutyhead("Central Tax");
        cgst.setGstrate("9");
        GstRateDetail sgst = new GstRateDetail();
        sgst.setGstratedutyhead("State Tax");
        sgst.setGstrate("NA");
        GstRateDetail cess = new GstRateDetail();
        cess.setGstratedutyhead("Cess");
        cess.setGstrate("  ");
        expense.setRatedetails(List.of(cgst, sgst, cess));

        Voucher voucher = new Voucher();
        voucher.setVouchertypename("Purchase");
        voucher.setDate("20250401");
        voucher.setAllLedgerEntries(List.of(expense));
        voucher.setLedgerEntries(List.of());

        TallyMessage tallyMessage = new TallyMessage();
        tallyMessage.setTallymessage(List.of(voucher));

        when(parsedVoucherRepository.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));

        List<ParsedVoucher> saved = dayBookParserService.persistVouchers(
                tallyMessage, UUID.randomUUID(), UUID.randomUUID());

        assertThat(saved.get(0).getEntries().get(0).getGstRate()).isEqualByComparingTo(new BigDecimal("9.00"));
    }

    @Test
    void persistVouchers_capturesPartyGstRegistrationType() {
        LedgerEntry entryA = new LedgerEntry();
        entryA.setLedgername("Test Purchases");
        entryA.setAmount("-100");

        LedgerEntry entryB = new LedgerEntry();
        entryB.setLedgername("Test Purchases");
        entryB.setAmount("-100");

        Voucher voucherA = new Voucher();
        voucherA.setVouchertypename("Purchase");
        voucherA.setDate("20250401");
        voucherA.setGstregistrationtype("Regular");
        voucherA.setAllLedgerEntries(List.of(entryA));
        voucherA.setLedgerEntries(List.of());

        Voucher voucherB = new Voucher();
        voucherB.setVouchertypename("Purchase");
        voucherB.setDate("20250401");
        voucherB.setAllLedgerEntries(List.of(entryB));
        voucherB.setLedgerEntries(List.of());

        TallyMessage tallyMessage = new TallyMessage();
        tallyMessage.setTallymessage(List.of(voucherA, voucherB));

        when(parsedVoucherRepository.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));

        List<ParsedVoucher> result = dayBookParserService.persistVouchers(
                tallyMessage, UUID.randomUUID(), UUID.randomUUID());

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getPartyGstRegistrationType()).isEqualTo("Regular");
        assertThat(result.get(1).getPartyGstRegistrationType()).isNull();
    }

    private static GstRateDetail rate(String head, String rate) {
        GstRateDetail d = new GstRateDetail();
        d.setGstratedutyhead(head);
        d.setGstrate(rate);
        return d;
    }

    private BigDecimal parsedRateFor(GstRateDetail... details) {
        LedgerEntry expense = new LedgerEntry();
        expense.setLedgername("Job Work Charges");
        expense.setAmount("10000.00");
        expense.setGstovrdntaxability("Taxable");
        expense.setRatedetails(List.of(details));

        Voucher voucher = new Voucher();
        voucher.setVouchertypename("Purchase");
        voucher.setDate("20250401");
        voucher.setAllLedgerEntries(List.of(expense));
        voucher.setLedgerEntries(List.of());

        TallyMessage tallyMessage = new TallyMessage();
        tallyMessage.setTallymessage(List.of(voucher));

        when(parsedVoucherRepository.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));

        List<ParsedVoucher> saved = dayBookParserService.persistVouchers(
                tallyMessage, UUID.randomUUID(), UUID.randomUUID());
        return saved.get(0).getEntries().get(0).getGstRate();
    }

    @Test
    void gstRateUsesIntegratedWhenAllHeadsListed() {
        assertThat(parsedRateFor(rate("Central Tax", "9"), rate("State Tax", "9"),
                rate("Integrated Tax", "18"), rate("Cess", "0")))
                .isEqualByComparingTo(new BigDecimal("18"));
    }

    @Test
    void gstRateIntegratedOnly() {
        assertThat(parsedRateFor(rate("Integrated Tax", "18"))).isEqualByComparingTo(new BigDecimal("18"));
    }

    @Test
    void gstRateCentralPlusStateOnly() {
        assertThat(parsedRateFor(rate("Central Tax", "9"), rate("State Tax", "9")))
                .isEqualByComparingTo(new BigDecimal("18"));
    }

    @Test
    void gstRateCentralPlusUtTax() {
        assertThat(parsedRateFor(rate("Central Tax", "9"), rate("UT Tax", "9")))
                .isEqualByComparingTo(new BigDecimal("18"));
    }

    @Test
    void gstRateFallsBackToCentralStateWhenIntegratedZero() {
        assertThat(parsedRateFor(rate("Central Tax", "9"), rate("State Tax", "9"), rate("Integrated Tax", "0")))
                .isEqualByComparingTo(new BigDecimal("18"));
    }

    @Test
    void gstRateExcludesNonZeroCess() {
        assertThat(parsedRateFor(rate("Central Tax", "9"), rate("State Tax", "9"), rate("Cess", "12")))
                .isEqualByComparingTo(new BigDecimal("18"));
        assertThat(parsedRateFor(rate("Integrated Tax", "18"), rate("Cess", "12")))
                .isEqualByComparingTo(new BigDecimal("18"));
    }

    @Test
    void persistVouchers_setsTallyIdsAndContentHash() {
        LedgerEntry entry = new LedgerEntry();
        entry.setLedgername("Test Purchases");
        entry.setAmount("-100.00");

        Voucher voucher = new Voucher();
        voucher.setVouchertypename("Purchase");
        voucher.setVouchernumber("101");
        voucher.setDate("20250401");
        voucher.setMasterid("123");
        voucher.setAlterid("456");
        voucher.setAllLedgerEntries(List.of(entry));
        voucher.setLedgerEntries(List.of());

        TallyMessage tallyMessage = new TallyMessage();
        tallyMessage.setTallymessage(List.of(voucher));

        when(parsedVoucherRepository.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));

        List<ParsedVoucher> result = dayBookParserService.persistVouchers(
                tallyMessage, UUID.randomUUID(), UUID.randomUUID());

        assertThat(result).hasSize(1);
        ParsedVoucher persisted = result.get(0);
        assertThat(persisted.getTallyMasterId()).isEqualTo("123");
        assertThat(persisted.getTallyAlterId()).isEqualTo("456");
        assertThat(persisted.getContentHash())
                .isEqualTo(VoucherCanonicalizer.contentHash(voucher));
    }
}
