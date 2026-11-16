package com.arktech.superaccountant.filedreturns.parse;

import com.arktech.superaccountant.filedreturns.payload.Gstr3bEntryRequest;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class Gstr3bPdfParser {

    public static final String PARSER_VERSION = "gstr3b-pdf-1";

    private static final Pattern GSTIN = Pattern.compile(
            "\\b(\\d{2}[A-Z]{5}\\d{4}[A-Z][1-9A-Z]Z[0-9A-Z])\\b");
    private static final Pattern YEAR = Pattern.compile("Year\\s*:?\\s*(\\d{4})\\s*-\\s*(\\d{2})");
    private static final Pattern PERIOD = Pattern.compile("Period\\s*:?\\s*([A-Za-z -]+)");
    private static final Pattern AMOUNT = Pattern.compile("\\d{1,3}(?:,\\d{2,3})*\\.\\d{2}");

    private static final Map<String, Integer> MONTHS = Map.ofEntries(
            Map.entry("january", 1), Map.entry("february", 2), Map.entry("march", 3),
            Map.entry("april", 4), Map.entry("may", 5), Map.entry("june", 6),
            Map.entry("july", 7), Map.entry("august", 8), Map.entry("september", 9),
            Map.entry("october", 10), Map.entry("november", 11), Map.entry("december", 12));

    public Gstr3bParsed parse(byte[] pdf) {
        String text;
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            text = stripper.getText(doc);
        } catch (IOException e) {
            throw new FiledReturnParseException("Could not read a GSTR-3B from this PDF; enter the values manually");
        }
        String[] lines = text.split("\\r?\\n");
        List<String> warnings = new ArrayList<>();

        String gstin = null;
        for (String line : lines) {
            Matcher m = GSTIN.matcher(line);
            if (m.find()) {
                gstin = m.group(1);
                break;
            }
        }

        String filingPeriod = extractFilingPeriod(lines);

        List<Gstr3bEntryRequest.Row> rows = new ArrayList<>();
        Map<String, Boolean> seen = new HashMap<>();
        for (int i = 0; i < lines.length; i++) {
            Gstr3bRowLabels.RowLabel row = matchRow(lines[i]);
            if (row == null) {
                continue;
            }
            List<BigDecimal> amounts = findAmounts(lines, i, row.expectedAmounts());
            if (amounts.size() != row.expectedAmounts()) {
                warnings.add("Row " + row.rowCode() + ": expected " + row.expectedAmounts()
                        + " amounts, found " + amounts.size());
                rows.add(row(row.rowCode(), null, null, null, null, null));
                seen.put(row.rowCode(), true);
                continue;
            }
            if (row.expectedAmounts() == 5) {
                rows.add(row(row.rowCode(), amounts.get(0), amounts.get(1), amounts.get(2),
                        amounts.get(3), amounts.get(4)));
            } else {
                rows.add(row(row.rowCode(), null, amounts.get(0), amounts.get(1),
                        amounts.get(2), amounts.get(3)));
            }
            seen.put(row.rowCode(), true);
        }

        if (gstin == null) {
            throw new FiledReturnParseException("Could not read a GSTR-3B from this PDF; enter the values manually");
        }
        if (rows.isEmpty()) {
            throw new FiledReturnParseException("Could not read a GSTR-3B from this PDF; enter the values manually");
        }
        if (filingPeriod == null) {
            throw new FiledReturnParseException("Could not read a GSTR-3B from this PDF; enter the values manually");
        }
        return new Gstr3bParsed(gstin, filingPeriod, rows, warnings);
    }

    private Gstr3bEntryRequest.Row row(String code, BigDecimal taxable, BigDecimal igst,
                                       BigDecimal cgst, BigDecimal sgst, BigDecimal cess) {
        return new Gstr3bEntryRequest.Row(code, taxable, igst, cgst, sgst, cess);
    }

    private String extractFilingPeriod(String[] lines) {
        Integer yearStart = null;
        Integer yearEnd = null;
        for (String line : lines) {
            Matcher y = YEAR.matcher(line);
            if (y.find()) {
                yearStart = Integer.parseInt(y.group(1));
                yearEnd = 2000 + Integer.parseInt(y.group(2));
                break;
            }
        }
        String periodText = null;
        for (String line : lines) {
            Matcher p = PERIOD.matcher(line);
            if (p.find()) {
                periodText = p.group(1);
                break;
            }
        }
        if (yearStart == null || periodText == null) {
            return null;
        }
        Integer month = monthFrom(periodText);
        if (month == null) {
            return null;
        }
        int year = month >= 4 ? yearStart : yearStart + 1;
        return String.format("%04d-%02d", year, month);
    }

    private Integer monthFrom(String periodText) {
        String lower = periodText.toLowerCase();
        Integer found = null;
        for (Map.Entry<String, Integer> e : MONTHS.entrySet()) {
            if (lower.contains(e.getKey())) {
                if (found == null || e.getValue() > found) {
                    found = e.getValue();
                }
            }
        }
        return found;
    }

    private Gstr3bRowLabels.RowLabel matchRow(String line) {
        for (Gstr3bRowLabels.RowLabel row : Gstr3bRowLabels.ROWS.values()) {
            for (String pattern : row.labelPatterns()) {
                if (Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(line).find()) {
                    return row;
                }
            }
        }
        return null;
    }

    private List<BigDecimal> findAmounts(String[] lines, int labelIndex, int expected) {
        Pattern noCommaAmount = Pattern.compile("\\d+\\.\\d{2}");
        for (int j = labelIndex; j <= Math.min(labelIndex + 2, lines.length - 1); j++) {
            Matcher m = AMOUNT.matcher(lines[j]);
            List<BigDecimal> found = new ArrayList<>();
            while (m.find()) {
                found.add(new BigDecimal(m.group().replace(",", "")));
            }
            if (!found.isEmpty() && found.size() >= expected) {
                return found.subList(0, expected);
            }
            if (!found.isEmpty()) {
                return found;
            }
            Matcher plain = noCommaAmount.matcher(lines[j]);
            List<BigDecimal> plainFound = new ArrayList<>();
            while (plain.find()) {
                plainFound.add(new BigDecimal(plain.group()));
            }
            if (!plainFound.isEmpty()) {
                return plainFound;
            }
        }
        return List.of();
    }
}
