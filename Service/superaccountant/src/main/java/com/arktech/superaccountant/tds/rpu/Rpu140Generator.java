package com.arktech.superaccountant.tds.rpu;

import com.arktech.superaccountant.tds.models.TdsChallan;
import com.arktech.superaccountant.tds.models.TdsDeducteeEntry;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Form 140 (Income-tax Rules 2026, Rule 219) e-TDS RPU text file generator for TDS
 * under s.393 of the Income-tax Act 2025 (periods on/after 1 April 2026). Pure and
 * stateless; every value is formatted by {@link Rpu140FormatSpec}.
 */
@Service
public class Rpu140Generator {

    public String generate(RpuHeader header, List<TdsChallan> challans, List<TdsDeducteeEntry> entries) {
        List<TdsChallan> orderedChallans = new ArrayList<>(challans);
        orderedChallans.sort(Comparator
                .comparing(TdsChallan::getDepositDate, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(TdsChallan::getChallanSerialNumber, Comparator.nullsLast(Comparator.naturalOrder())));

        Set<UUID> knownChallanIds = new HashSet<>();
        for (TdsChallan challan : orderedChallans) {
            knownChallanIds.add(challan.getId());
        }

        Map<UUID, List<TdsDeducteeEntry>> entriesByChallan = new HashMap<>();
        for (TdsDeducteeEntry entry : entries) {
            if (entry.getTdsChallanId() == null || !knownChallanIds.contains(entry.getTdsChallanId())) {
                throw new IllegalArgumentException("Deductee entry " + entry.getId()
                        + " references unknown challan " + entry.getTdsChallanId());
            }
            entriesByChallan.computeIfAbsent(entry.getTdsChallanId(), id -> new ArrayList<>()).add(entry);
        }

        BigDecimal batchTotal = orderedChallans.stream()
                .map(TdsChallan::getTotalAmount)
                .map(Rpu140Generator::nz)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<String> lines = new ArrayList<>();
        int lineNumber = 1;
        lines.add(join(Rpu140FormatSpec.fileHeader(
                new RpuFormatSpec.FhData(lineNumber++, header.tan(), 1, header.fileCreationDate()))));
        lines.add(join(Rpu140FormatSpec.batchHeader(
                new RpuFormatSpec.BhData(lineNumber++, 1, orderedChallans.size(), header, batchTotal))));

        int challanSerialOnFile = 1;
        for (TdsChallan challan : orderedChallans) {
            List<TdsDeducteeEntry> challanEntries =
                    new ArrayList<>(entriesByChallan.getOrDefault(challan.getId(), List.of()));
            challanEntries.sort(Comparator
                    .comparing(TdsDeducteeEntry::getDateOfPayment, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(TdsDeducteeEntry::getDeducteeName,
                            Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)));

            BigDecimal annexureTaxDeposited = BigDecimal.ZERO;
            BigDecimal annexureTaxDeducted = BigDecimal.ZERO;
            for (TdsDeducteeEntry entry : challanEntries) {
                annexureTaxDeposited = annexureTaxDeposited.add(nz(entry.getTdsDeposited()));
                annexureTaxDeducted = annexureTaxDeducted.add(nz(entry.getTdsDeducted()));
            }

            BigDecimal tax = nz(challan.getTdsAmount());
            lines.add(join(Rpu140FormatSpec.challanDetail(new Rpu140FormatSpec.CdData(
                    lineNumber++,
                    challanSerialOnFile,
                    challan.getBsrCode(),
                    effectiveDepositDate(challan, header),
                    challan.getChallanSerialNumber(),
                    tax,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    tax,
                    challan.isNilChallan(),
                    challanEntries.size(),
                    annexureTaxDeposited,
                    annexureTaxDeducted))));

            int deducteeSerialInChallan = 1;
            for (TdsDeducteeEntry entry : challanEntries) {
                lines.add(join(Rpu140FormatSpec.deducteeDetail(new RpuFormatSpec.DdData(
                        lineNumber++,
                        challanSerialOnFile,
                        deducteeSerialInChallan++,
                        entry.getDeducteePan(),
                        entry.isPanValid(),
                        entry.getDeducteeName(),
                        entry.getDeducteeType(),
                        entry.getSection(),
                        entry.getAmountPaid(),
                        entry.getTdsDeducted(),
                        entry.getTdsDeposited(),
                        entry.getDateOfPayment(),
                        entry.getDateOfDeduction(),
                        entry.getRateApplied(),
                        entry.isHigherRateApplied()))));
            }
            challanSerialOnFile++;
        }

        return String.join(Rpu140FormatSpec.LINE_ENDING, lines) + Rpu140FormatSpec.LINE_ENDING;
    }

    private static LocalDate effectiveDepositDate(TdsChallan challan, RpuHeader header) {
        if (challan.getDepositDate() != null) {
            return challan.getDepositDate();
        }
        if (!challan.isNilChallan()) {
            return null;
        }
        return switch (header.quarter()) {
            case 1 -> LocalDate.of(header.financialYearStart(), 6, 30);
            case 2 -> LocalDate.of(header.financialYearStart(), 9, 30);
            case 3 -> LocalDate.of(header.financialYearStart(), 12, 31);
            case 4 -> LocalDate.of(header.financialYearStart() + 1, 3, 31);
            default -> null;
        };
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static String join(List<String> fields) {
        return String.join(Rpu140FormatSpec.DELIMITER, fields);
    }
}
