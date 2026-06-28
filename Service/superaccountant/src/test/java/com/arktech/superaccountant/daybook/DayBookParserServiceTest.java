package com.arktech.superaccountant.daybook;

import com.arktech.superaccountant.daybook.models.ParsedVoucher;
import com.arktech.superaccountant.daybook.models.ParsedVoucherEntry;
import com.arktech.superaccountant.daybook.repository.ParsedVoucherRepository;
import com.arktech.superaccountant.daybook.services.DayBookParserService;
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
}
