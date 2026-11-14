package com.arktech.superaccountant.tds.rpu;

import com.arktech.superaccountant.tds.models.TdsDeducteeType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The only class that knows the Protean 26Q regular file layout.
 *
 * <p>Every field position comes from {@code resources/tds/rpu-format-notes.md}
 * (version {@value #FORMAT_VERSION}). Builders return the fields in spec order; joining
 * with {@link #DELIMITER} and terminating lines is the caller's job.
 */
public final class RpuFormatSpec {

    public static final String DELIMITER = "^";
    public static final String LINE_ENDING = "\r\n";
    public static final String FORM = "26Q";
    public static final String FORMAT_VERSION = "7.8";

    public static final String PAN_NOT_AVAILABLE = "PANNOTAVBL";

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("ddMMyyyy");
    private static final int NAME_MAX = 75;
    private static final int DESIGNATION_MAX = 20;
    private static final int ADDRESS_LINES = 5;
    private static final int ADDRESS_LINE_LENGTH = 25;

    private static final Map<String, String> SECTION_CODES = Map.of(
            "194A", "94A",
            "194C", "94C",
            "194H", "94H",
            "194I", "4IB",
            "194J_A", "4JA",
            "194J_B", "4JB",
            "194M", "94M",
            "194Q", "94Q");

    private RpuFormatSpec() {
    }

    // ---------------------------------------------------------------- FH

    public static List<String> fileHeader(FhData d) {
        List<String> f = new ArrayList<>(18);
        f.add(String.valueOf(d.lineNumber()));
        f.add("FH");
        f.add("NS1");
        f.add("R");
        f.add(date(d.creationDate()));
        f.add("1");
        f.add("D");
        f.add(text(d.tan()));
        f.add(String.valueOf(d.totalBatches()));
        f.add("SUPER ACCOUNTANT");
        addBlanks(f, 8);
        return f;
    }

    // ---------------------------------------------------------------- BH

    public static List<String> batchHeader(BhData d) {
        RpuHeader h = d.header();
        List<String> f = new ArrayList<>(72);
        f.add(String.valueOf(d.lineNumber()));
        f.add("BH");
        f.add(String.valueOf(d.batchNumber()));
        f.add(String.valueOf(d.challanCount()));
        f.add(FORM);
        addBlanks(f, 3);                                    // 6-8
        f.add(text(h.originalReceiptNumber()));             // 9
        addBlanks(f, 3);                                    // 10-12
        f.add(text(h.tan()));                               // 13
        f.add("");                                          // 14
        f.add(text(h.deductorPan()).isEmpty() ? "PANNOTREQD" : text(h.deductorPan())); // 15
        f.add(assessmentYear(h.financialYearStart()));      // 16
        f.add(financialYear(h.financialYearStart()));       // 17
        f.add("Q" + h.quarter());                           // 18
        f.add(text(h.deductorName()));                      // 19
        f.add(text(h.deductorBranch()).isEmpty() ? "NA" : text(h.deductorBranch())); // 20
        f.addAll(addressLines(h.deductorAddress()));        // 21-25
        f.add(stateCode(h.deductorStateCode()));            // 26
        f.add(text(h.deductorPincode()));                   // 27
        f.add(text(h.deductorEmail()));                     // 28
        f.add("");                                          // 29
        f.add("");                                          // 30
        f.add(h.deductorAddressChanged() ? "Y" : "N");      // 31
        f.add(text(h.deductorType()));                      // 32
        f.add(text(h.responsiblePersonName()));             // 33
        f.add(truncate(text(h.responsiblePersonDesignation()), DESIGNATION_MAX)); // 34
        f.addAll(addressLines(h.responsiblePersonAddress())); // 35-39
        f.add(stateCode(h.responsiblePersonStateCode()));   // 40
        f.add(text(h.responsiblePersonPin()));              // 41
        f.add(text(h.responsiblePersonEmail()));            // 42
        f.add(text(h.responsiblePersonMobile()).isEmpty()
                ? text(h.deductorMobile()) : text(h.responsiblePersonMobile())); // 43
        f.add("");                                          // 44
        f.add("");                                          // 45
        f.add(h.responsiblePersonAddressChanged() ? "Y" : "N"); // 46
        f.add(amount(d.batchTotalDeposit()));               // 47
        addBlanks(f, 3);                                    // 48-50
        f.add("N");                                         // 51
        f.add(h.regularStatementFiledEarlier() ? "Y" : "N"); // 52
        addBlanks(f, 6);                                    // 53-58
        f.add(text(h.responsiblePersonPan()));              // 59
        addBlanks(f, 2);                                    // 60-61
        addBlanks(f, 6);                                    // 62-67
        addBlanks(f, 2);                                    // 68-69
        addBlanks(f, 3);                                    // 70-72
        return f;
    }

    // ---------------------------------------------------------------- CD

    public static List<String> challanDetail(CdData d) {
        List<String> f = new ArrayList<>(41);
        f.add(String.valueOf(d.lineNumber()));
        f.add("CD");
        f.add("1");
        f.add(String.valueOf(d.challanSerialOnFile()));
        f.add(String.valueOf(d.deducteeCount()));
        f.add(d.nil() ? "Y" : "N");
        addBlanks(f, 5);                                    // 7-11
        f.add(d.nil() ? "" : text(d.challanSerialNumber())); // 12
        f.add("");                                          // 13
        f.add("");                                          // 14
        f.add("");                                          // 15
        f.add(d.nil() ? "" : text(d.bsrCode()));            // 16
        f.add("");                                          // 17
        f.add(date(d.depositDate()));                       // 18
        f.add("");                                          // 19
        f.add("");                                          // 20
        f.add("");                                          // 21
        f.add(amount(d.tds()));                             // 22
        f.add(amount(d.surcharge()));                       // 23
        f.add(amount(d.educationCess()));                   // 24
        f.add(amount(d.interest()));                        // 25
        f.add(amount(d.penalty()));                         // 26
        f.add(amount(d.total()));                           // 27
        f.add("");                                          // 28
        f.add(amount(d.annexureTdsDeposited()));            // 29
        f.add(amount(d.annexureIncomeTax()));               // 30
        f.add(amount(d.annexureSurcharge()));               // 31
        f.add(amount(d.annexureCess()));                    // 32
        f.add(amount(d.annexureTotalDeducted()));           // 33
        f.add(amount(d.interest()));                        // 34
        f.add(amount(d.penalty()));                         // 35
        f.add("");                                          // 36
        f.add(d.nil() ? "" : "N");                          // 37
        f.add("");                                          // 38
        f.add("0.00");                                      // 39
        f.add(d.nil() ? "" : "200");                        // 40
        f.add("");                                          // 41
        return f;
    }

    // ---------------------------------------------------------------- DD

    public static List<String> deducteeDetail(DdData d) {
        List<String> f = new ArrayList<>(54);
        f.add(String.valueOf(d.lineNumber()));
        f.add("DD");
        f.add("1");
        f.add(String.valueOf(d.challanSerialOnFile()));
        f.add(String.valueOf(d.deducteeSerialInChallan()));
        f.add("O");
        f.add("");                                          // 7
        f.add(d.type() == TdsDeducteeType.COMPANY ? "01" : "02"); // 8
        f.add("");                                          // 9
        f.add(pan(d.pan(), d.panValid()));                  // 10
        f.add("");                                          // 11
        f.add("");                                          // 12
        f.add(sanitiseName(d.name()));                      // 13
        f.add(amount(d.tdsDeducted()));                     // 14
        f.add("0.00");                                      // 15
        f.add("0.00");                                      // 16
        f.add(amount(d.tdsDeducted()));                     // 17
        f.add("");                                          // 18
        f.add(amount(d.tdsDeposited()));                    // 19
        f.add("");                                          // 20
        f.add("");                                          // 21
        f.add(amount(d.amountPaid()));                      // 22
        f.add(date(d.paymentDate()));                       // 23
        f.add(hasAmount(d.tdsDeducted()) ? date(d.deductionDate()) : ""); // 24
        f.add("");                                          // 25
        f.add(rate(d.rate()));                              // 26
        f.add("");                                          // 27
        f.add("");                                          // 28
        f.add("");                                          // 29
        f.add(remark(d.higherRate(), d.panValid()));        // 30
        f.add("");                                          // 31
        f.add("");                                          // 32
        f.add(sectionCode(d.section()));                    // 33
        f.add("");                                          // 34
        addBlanks(f, 8);                                    // 35-42
        addBlanks(f, 6);                                    // 43-48
        addBlanks(f, 5);                                    // 49-53
        f.add("");                                          // 54
        return f;
    }

    // ---------------------------------------------------------------- helpers

    public static String date(LocalDate d) {
        return d == null ? "" : DATE.format(d);
    }

    public static String amount(BigDecimal a) {
        return a == null ? "0.00" : a.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    public static String rate(BigDecimal rate) {
        return rate == null ? "0.0000" : rate.setScale(4, RoundingMode.HALF_UP).toPlainString();
    }

    public static String pan(String pan, boolean panValid) {
        if (!panValid || pan == null || pan.isBlank()) {
            return PAN_NOT_AVAILABLE;
        }
        return pan;
    }

    public static String sectionCode(String internal) {
        String code = internal == null ? null : SECTION_CODES.get(internal);
        if (code == null) {
            throw new IllegalArgumentException("Unknown TDS section: " + internal);
        }
        return code;
    }

    public static String sanitiseName(String raw) {
        if (raw == null) {
            return "";
        }
        String cleaned = raw.replaceAll("[^A-Za-z0-9 .,&()/\\-]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return truncate(cleaned, NAME_MAX);
    }

    public static String remark(boolean higherRate, boolean panValid) {
        return higherRate && !panValid ? "C" : "";
    }

    /** Word-wraps an address into exactly five 25-character BH lines. */
    public static List<String> addressLines(String raw) {
        List<String> lines = new ArrayList<>();
        if (raw != null) {
            String cleaned = raw.replaceAll("\\s+", " ").trim();
            StringBuilder current = new StringBuilder();
            for (String word : cleaned.split(" ")) {
                while (word.length() > ADDRESS_LINE_LENGTH) {
                    if (current.length() > 0) {
                        lines.add(current.toString());
                        current.setLength(0);
                    }
                    lines.add(word.substring(0, ADDRESS_LINE_LENGTH));
                    word = word.substring(ADDRESS_LINE_LENGTH);
                    if (lines.size() >= ADDRESS_LINES) {
                        break;
                    }
                }
                if (lines.size() >= ADDRESS_LINES || word.isEmpty()) {
                    continue;
                }
                if (current.length() == 0) {
                    current.append(word);
                } else if (current.length() + 1 + word.length() <= ADDRESS_LINE_LENGTH) {
                    current.append(' ').append(word);
                } else {
                    lines.add(current.toString());
                    current.setLength(0);
                    current.append(word);
                }
            }
            if (current.length() > 0 && lines.size() < ADDRESS_LINES) {
                lines.add(current.toString());
            }
        }
        while (lines.size() < ADDRESS_LINES) {
            lines.add("");
        }
        return lines.subList(0, ADDRESS_LINES);
    }

    private static String assessmentYear(int financialYearStart) {
        int end = financialYearStart + 1;
        return String.valueOf(end) + String.format("%02d", (financialYearStart + 2) % 100);
    }

    private static String financialYear(int financialYearStart) {
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

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static void addBlanks(List<String> fields, int count) {
        for (int i = 0; i < count; i++) {
            fields.add("");
        }
    }

    // ---------------------------------------------------------------- records

    public record FhData(int lineNumber, String tan, int totalBatches, LocalDate creationDate) {
    }

    public record BhData(int lineNumber, int batchNumber, int challanCount, RpuHeader header,
                         BigDecimal batchTotalDeposit) {
    }

    public record CdData(int lineNumber, int challanSerialOnFile, String bsrCode, LocalDate depositDate,
                         String challanSerialNumber, BigDecimal tds, BigDecimal surcharge,
                         BigDecimal educationCess, BigDecimal interest, BigDecimal penalty,
                         BigDecimal total, boolean nil, int deducteeCount,
                         BigDecimal annexureTdsDeposited, BigDecimal annexureIncomeTax,
                         BigDecimal annexureSurcharge, BigDecimal annexureCess,
                         BigDecimal annexureTotalDeducted) {
    }

    public record DdData(int lineNumber, int challanSerialOnFile, int deducteeSerialInChallan,
                         String pan, boolean panValid, String name, TdsDeducteeType type, String section,
                         BigDecimal amountPaid, BigDecimal tdsDeducted, BigDecimal tdsDeposited,
                         LocalDate paymentDate, LocalDate deductionDate, BigDecimal rate,
                         boolean higherRate) {
    }
}
