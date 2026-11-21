package com.arktech.superaccountant.tds.rpu;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The only class that knows the Protean Form No. 140 regular file layout
 * (TDS statements under the Income-tax Act 2025, s.397(3)(b) read with Rule 219).
 *
 * <p>Every field position comes from the official Protean file-format specification
 * (Form 140 v1.2, 22072026) — see {@code resources/tds/rpu-format-notes-140.md}.
 * Builders return the fields in spec order; joining with {@link #DELIMITER} and
 * terminating lines is the caller's job.
 */
public final class Rpu140FormatSpec {

    public static final String DELIMITER = RpuFormatSpec.DELIMITER;
    public static final String LINE_ENDING = RpuFormatSpec.LINE_ENDING;
    public static final String FORM = "140";

    private static final Map<String, String> SECTION_CODES = Map.of(
            "194A", "1022",
            "194C", "1024",
            "194H", "1006",
            "194I", "1009",
            "194J_A", "1026",
            "194J_B", "1027",
            "194Q", "1031");

    private Rpu140FormatSpec() {
    }

    // ---------------------------------------------------------------- FH

    public static List<String> fileHeader(RpuFormatSpec.FhData d) {
        List<String> f = new ArrayList<>(18);
        f.add(String.valueOf(d.lineNumber()));
        f.add("FH");
        f.add("NS1");
        f.add("R");
        f.add(RpuFormatSpec.date(d.creationDate()));
        f.add("1");
        f.add("D");
        f.add(text(d.tan()));
        f.add(String.valueOf(d.totalBatches()));
        f.add("SUPER ACCOUNTANT");
        addBlanks(f, 8);
        return f;
    }

    // ---------------------------------------------------------------- BH

    public static List<String> batchHeader(RpuFormatSpec.BhData d) {
        RpuHeader h = d.header();
        List<String> f = new ArrayList<>(72);
        f.add(String.valueOf(d.lineNumber()));               // 1
        f.add("BH");                                         // 2
        f.add(String.valueOf(d.batchNumber()));              // 3
        f.add(String.valueOf(d.challanCount()));             // 4
        f.add(FORM);                                         // 5
        addBlanks(f, 3);                                     // 6-8
        f.add(text(h.originalReceiptNumber()));              // 9
        addBlanks(f, 3);                                     // 10-12
        f.add(text(h.tan()));                                // 13
        f.add("");                                           // 14
        f.add(text(h.deductorPan()).isEmpty() ? "PANNOTREQD" : text(h.deductorPan())); // 15
        f.add(assessmentYear(h.financialYearStart()));       // 16
        f.add(taxYear(h.financialYearStart()));              // 17
        f.add("Q" + h.quarter());                            // 18
        f.add(text(h.deductorName()));                       // 19
        f.add("");                                           // 20 (address country, optional)
        f.addAll(RpuFormatSpec.addressLines(h.deductorAddress())); // 21-25
        f.add(stateCode(h.deductorStateCode()));             // 26
        f.add(text(h.deductorPincode()));                    // 27
        f.add(text(h.deductorEmail()));                      // 28
        f.add("91");                                         // 29
        f.add(text(h.deductorMobile()));                     // 30
        f.add("");                                           // 31 (filler)
        f.add(text(h.deductorType()));                       // 32
        f.add(text(h.responsiblePersonName()));              // 33
        f.add(text(h.responsiblePersonDesignation()));       // 34
        f.addAll(RpuFormatSpec.addressLines(h.responsiblePersonAddress())); // 35-39
        f.add(stateCode(h.responsiblePersonStateCode()));    // 40
        f.add(text(h.responsiblePersonPin()));               // 41
        f.add(text(h.responsiblePersonEmail()));             // 42
        f.add("India");                                      // 43 (responsible person country)
        f.add("91");                                         // 44
        f.add(text(h.responsiblePersonMobile()).isEmpty()
                ? text(h.deductorMobile()) : text(h.responsiblePersonMobile())); // 45
        f.add("");                                           // 46
        f.add(RpuFormatSpec.amount(d.batchTotalDeposit()));  // 47
        addBlanks(f, 4);                                     // 48-51
        f.add(h.regularStatementFiledEarlier() ? "Y" : "N"); // 52
        f.add("");                                           // 53
        addBlanks(f, 4);                                     // 54-57
        f.add("");                                           // 58
        f.add(text(h.responsiblePersonPan()));               // 59
        addBlanks(f, 8);                                     // 60-67
        f.add("");                                           // 68 (AIN)
        f.add("");                                           // 69 (GSTN)
        addBlanks(f, 3);                                     // 70-72
        return f;
    }

    // ---------------------------------------------------------------- CD

    public static List<String> challanDetail(CdData d) {
        List<String> f = new ArrayList<>(30);
        f.add(String.valueOf(d.lineNumber()));               // 1
        f.add("CD");                                         // 2
        f.add("1");                                          // 3
        f.add(String.valueOf(d.challanSerialOnFile()));      // 4
        f.add(String.valueOf(d.deducteeCount()));            // 5
        f.add(d.nil() ? "Y" : "N");                          // 6
        f.add("");                                           // 7
        f.add(RpuFormatSpec.amount(d.incomeTax()));          // 8
        f.add(RpuFormatSpec.amount(d.interest()));           // 9
        f.add(RpuFormatSpec.amount(d.fee()));                // 10
        f.add(RpuFormatSpec.amount(d.others()));             // 11
        f.add(RpuFormatSpec.amount(d.total()));              // 12
        f.add(d.nil() ? "" : "C");                           // 13
        f.add("");                                           // 14
        f.add(d.nil() ? "" : text(d.bsrCode()));             // 15
        f.add("");                                           // 16
        f.add(d.nil() ? "" : text(d.challanSerialNumber())); // 17
        f.add("");                                           // 18
        f.add(RpuFormatSpec.date(d.depositDate()));          // 19
        f.add("");                                           // 20
        f.add(RpuFormatSpec.amount(d.annexureTaxDeposited())); // 21
        f.add(RpuFormatSpec.amount(d.annexureTaxDeducted()));  // 22
        f.add(d.nil() ? "" : "200");                         // 23
        f.add(RpuFormatSpec.amount(d.interest()));           // 24
        f.add(RpuFormatSpec.amount(d.others()));             // 25
        addBlanks(f, 5);                                     // 26-30
        return f;
    }

    // ---------------------------------------------------------------- DD

    public static List<String> deducteeDetail(RpuFormatSpec.DdData d) {
        List<String> f = new ArrayList<>(45);
        f.add(String.valueOf(d.lineNumber()));               // 1
        f.add("DD");                                         // 2
        f.add("1");                                          // 3
        f.add(String.valueOf(d.challanSerialOnFile()));      // 4
        f.add(String.valueOf(d.deducteeSerialInChallan()));  // 5
        f.add("O");                                          // 6
        f.add("");                                           // 7
        f.add(RpuFormatSpec.pan(d.pan(), d.panValid()));     // 8
        f.add(RpuFormatSpec.sanitiseName(d.name()));         // 9
        addBlanks(f, 5);                                     // 10-14
        f.add(sectionCode(d.section(), d.pan()));             // 15
        addBlanks(f, 2);                                     // 16-17
        f.add(hasAmount(d.tdsDeposited()) ? "Y" : "N");      // 18
        f.add(RpuFormatSpec.date(d.paymentDate()));          // 19
        f.add(RpuFormatSpec.amount(d.amountPaid()));         // 20
        f.add("");                                           // 21
        f.add("");                                           // 22
        f.add("");                                           // 23
        f.add(RpuFormatSpec.amount(d.tdsDeducted()));        // 24
        f.add(RpuFormatSpec.amount(d.tdsDeposited()));       // 25
        f.add("");                                           // 26
        f.add(hasAmount(d.tdsDeducted())
                ? RpuFormatSpec.date(d.deductionDate()) : ""); // 27
        f.add(RpuFormatSpec.rate(d.rate()));                 // 28
        addBlanks(f, 2);                                     // 29-30
        f.add("");                                           // 31 (Form 121 UIN)
        f.add(RpuFormatSpec.remark(d.higherRate(), d.panValid())); // 32
        f.add("");                                           // 33 (s.395 certificate)
        addBlanks(f, 11);                                    // 34-44
        f.add("");                                           // 45
        return f;
    }

    // ---------------------------------------------------------------- helpers

    public static String sectionCode(String internal) {
        return sectionCode(internal, null);
    }

    /**
     * Form 140 tax code for a deductee. For 194C the new Act splits the code by payee type:
     * individual/HUF (PAN 4th character P or H) is 1023, everyone else 1024. When the PAN is
     * missing or invalid the payee type cannot be determined and this throws rather than
     * defaulting — a valid PAN is enforced separately by the PANS_VALID finalize gate.
     */
    public static String sectionCode(String internal, String deducteePan) {
        String code = internal == null ? null : SECTION_CODES.get(internal);
        if (code == null) {
            if ("194M".equals(internal)) {
                throw new IllegalArgumentException(
                        "194M has no Form 140 code: from 1 April 2026 it is reported in "
                                + "Form 141 Schedule C, which this export does not support yet");
            }
            throw new IllegalArgumentException("Unknown TDS section for Form 140: " + internal);
        }
        if ("194C".equals(internal)) {
            if (deducteePan == null || deducteePan.length() != 10) {
                throw new IllegalArgumentException(
                        "Cannot determine 194C payee type (individual/HUF vs others) for Form 140: "
                                + "deductee PAN is missing or invalid");
            }
            char panType = deducteePan.charAt(3);
            return (panType == 'P' || panType == 'H') ? "1023" : "1024";
        }
        return code;
    }

    static String assessmentYear(int financialYearStart) {
        return String.valueOf(financialYearStart + 1)
                + String.format("%02d", (financialYearStart + 2) % 100);
    }

    static String taxYear(int financialYearStart) {
        return String.valueOf(financialYearStart)
                + String.format("%02d", (financialYearStart + 1) % 100);
    }

    private static String stateCode(Integer code) {
        return code == null || code <= 0 ? "" : String.format("%02d", code);
    }

    private static boolean hasAmount(BigDecimal value) {
        return value != null && value.signum() != 0;
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }

    private static void addBlanks(List<String> fields, int count) {
        for (int i = 0; i < count; i++) {
            fields.add("");
        }
    }

    // ---------------------------------------------------------------- records

    public record CdData(int lineNumber, int challanSerialOnFile, String bsrCode,
                         LocalDate depositDate, String challanSerialNumber,
                         BigDecimal incomeTax, BigDecimal interest, BigDecimal fee,
                         BigDecimal others, BigDecimal total, boolean nil, int deducteeCount,
                         BigDecimal annexureTaxDeposited, BigDecimal annexureTaxDeducted) {
    }
}
