package com.arktech.superaccountant.tds.rpu;

import java.time.LocalDate;

/**
 * C3-RPU input record: deductor and period details for one 26Q return.
 *
 * <p>The first eleven components are the plan's original contract. The remaining
 * components are optional BH values that Tc may supply from {@code Tds26qDetail} or the
 * workflow; null means "not available" and the field is emitted blank. See
 * {@code tds/rpu-format-notes.md} ("Deviations from plan interface").
 */
public record RpuHeader(
        String tan,
        String deductorPan,
        String deductorName,
        String deductorAddress,
        String deductorType,
        String responsiblePersonName,
        String responsiblePersonDesignation,
        String originalReceiptNumber,
        int financialYearStart,
        int quarter,
        LocalDate fileCreationDate,
        String deductorBranch,
        Integer deductorStateCode,
        String deductorPincode,
        String deductorEmail,
        String deductorMobile,
        String responsiblePersonPan,
        String responsiblePersonAddress,
        Integer responsiblePersonStateCode,
        String responsiblePersonPin,
        String responsiblePersonEmail,
        String responsiblePersonMobile,
        boolean deductorAddressChanged,
        boolean responsiblePersonAddressChanged,
        boolean regularStatementFiledEarlier) {

    /**
     * Convenience constructor matching the plan's original interface. Optional detail is
     * left null; the previous-statement flag is supplied explicitly by the caller.
     */
    public RpuHeader(String tan, String deductorPan, String deductorName, String deductorAddress,
                     String deductorType, String responsiblePersonName, String responsiblePersonDesignation,
                     String originalReceiptNumber, int financialYearStart, int quarter,
                     LocalDate fileCreationDate, boolean regularStatementFiledEarlier) {
        this(tan, deductorPan, deductorName, deductorAddress, deductorType,
                responsiblePersonName, responsiblePersonDesignation, originalReceiptNumber,
                financialYearStart, quarter, fileCreationDate,
                null, null, null, null, null, null, null, null, null, null, null,
                false, false,
                regularStatementFiledEarlier);
    }

    /**
     * Last day of the return's quarter (30 Jun, 30 Sep, 31 Dec, 31 Mar). A nil challan carries no
     * real deposit date, so both the 26Q and Form 140 exports report this date instead.
     * Null for a quarter outside 1-4.
     */
    public LocalDate quarterEndDate() {
        return switch (quarter) {
            case 1 -> LocalDate.of(financialYearStart, 6, 30);
            case 2 -> LocalDate.of(financialYearStart, 9, 30);
            case 3 -> LocalDate.of(financialYearStart, 12, 31);
            case 4 -> LocalDate.of(financialYearStart + 1, 3, 31);
            default -> null;
        };
    }
}
