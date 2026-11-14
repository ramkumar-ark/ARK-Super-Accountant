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
}
