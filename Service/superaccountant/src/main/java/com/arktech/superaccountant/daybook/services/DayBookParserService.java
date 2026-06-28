package com.arktech.superaccountant.daybook.services;

import com.arktech.superaccountant.daybook.models.ParsedVoucher;
import com.arktech.superaccountant.daybook.models.ParsedVoucherEntry;
import com.arktech.superaccountant.daybook.repository.ParsedVoucherRepository;
import com.arktech.superaccountant.tally.models.LedgerEntry;
import com.arktech.superaccountant.tally.models.TallyMessage;
import com.arktech.superaccountant.tally.models.Voucher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Normalizes and persists parsed Tally vouchers and their ledger entries.
 *
 * Key normalizations:
 * - Date: YYYYMMDD string → LocalDate (null if null or non-8-char)
 * - Amount: string → BigDecimal with sign preserved (Tally convention: negative = credit side)
 * - Ledger entries: always read via getAllLedgerEntriesCombined() — handles Payment/Purchase dual-key quirk
 */
@Service
public class DayBookParserService {

    @Autowired
    private ParsedVoucherRepository parsedVoucherRepository;

    /**
     * Normalizes and persists all vouchers from a parsed TallyMessage.
     *
     * @param tallyMessage parsed message from TallyParserService.parseJson()
     * @param jobId        the DayBookUploadJob ID to link vouchers to
     * @param orgId        the organization ID for tenant isolation
     * @return saved vouchers (empty list if tallymessage is null or empty)
     */
    @Transactional
    public List<ParsedVoucher> persistVouchers(TallyMessage tallyMessage, UUID jobId, UUID orgId) {
        // Null tallymessage: TallyParserService returned a TallyMessage with no vouchers.
        // This is treated as an empty result — controller layer handles the null case
        // and decides whether it is a FAILED job (malformed file) or COMPLETED with 0 vouchers.
        if (tallyMessage.getTallymessage() == null) {
            return List.of();
        }
        if (tallyMessage.getTallymessage().isEmpty()) {
            return List.of();
        }

        List<ParsedVoucher> vouchers = new ArrayList<>();
        for (Voucher v : tallyMessage.getTallymessage()) {
            ParsedVoucher pv = new ParsedVoucher();
            pv.setUploadJobId(jobId);
            pv.setOrganizationId(orgId);
            pv.setVoucherGuid(v.getGuid());
            pv.setVoucherNumber(v.getVouchernumber());
            pv.setVoucherTypeName(v.getVouchertypename() != null ? v.getVouchertypename() : "Unknown");
            pv.setVoucherDate(parseTallyDate(v.getDate()));
            pv.setPartyLedgerName(v.getPartyledgername());
            pv.setPartyGstin(v.getPartygstin());
            pv.setNarration(v.getNarration());
            pv.setInvoice(v.isIsinvoice());
            pv.setReverseChargeApplicable(v.isIsreversechargeapplicable());
            pv.setCancelled(v.isIscancelled());

            List<ParsedVoucherEntry> entries = new ArrayList<>();
            BigDecimal totalDebit = BigDecimal.ZERO;
            BigDecimal totalCredit = BigDecimal.ZERO;

            // CRITICAL: always use getAllLedgerEntriesCombined() — never getLedgerEntries() directly.
            // Payment vouchers use "allledgerentries"; Purchase vouchers use "ledgerentries".
            for (LedgerEntry le : v.getAllLedgerEntriesCombined()) {
                ParsedVoucherEntry pve = new ParsedVoucherEntry();
                pve.setParsedVoucher(pv);
                pve.setOrganizationId(orgId);
                pve.setLedgerName(le.getLedgername() != null ? le.getLedgername() : "");
                pve.setRawAmount(le.getAmount());

                BigDecimal amount = parseAmount(le.getAmount());
                pve.setAmount(amount);
                // Tally convention: positive amount = debit side; negative = credit side
                pve.setDebit(amount.compareTo(BigDecimal.ZERO) > 0);
                pve.setGstClass(le.getGstclass());
                pve.setPartyLedger(le.isIspartyledger());

                // totalDebit = sum of positive amounts; totalCredit = sum of abs(negative amounts)
                if (amount.compareTo(BigDecimal.ZERO) > 0) {
                    totalDebit = totalDebit.add(amount);
                } else if (amount.compareTo(BigDecimal.ZERO) < 0) {
                    totalCredit = totalCredit.add(amount.abs());
                }
                entries.add(pve);
            }
            pv.setEntries(entries);
            pv.setTotalDebit(totalDebit);
            pv.setTotalCredit(totalCredit);
            vouchers.add(pv);
        }
        return parsedVoucherRepository.saveAll(vouchers);
    }

    /**
     * Parses a Tally YYYYMMDD date string to LocalDate.
     * Returns null if the input is null or not exactly 8 characters.
     */
    private LocalDate parseTallyDate(String tallyDate) {
        if (tallyDate == null || tallyDate.length() != 8) return null;
        try {
            return LocalDate.parse(tallyDate, DateTimeFormatter.BASIC_ISO_DATE);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Parses a Tally amount string to BigDecimal.
     * Preserves sign: negative = credit side per Tally convention.
     * Returns ZERO for null, blank, or unparseable input.
     */
    private BigDecimal parseAmount(String amount) {
        if (amount == null || amount.isBlank()) return BigDecimal.ZERO;
        try {
            return new BigDecimal(amount.trim().replace(",", ""));
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }
}
