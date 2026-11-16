package com.arktech.superaccountant.filedreturns.parse;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Gstr3bPdfParserTest {

    private byte[] pdf(String... lines) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
                float y = 750;
                for (String line : lines) {
                    cs.beginText();
                    cs.newLineAtOffset(50, y);
                    cs.showText(line);
                    cs.endText();
                    y -= 15;
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }

    private static final String HEADER = """
            GST GSTR-3B
            GSTIN: 29ABCDE1234F1Z5
            Year: 2026-27  Period: April-June
            """;

    @Test
    void parsesHeaderAndRows() throws IOException {
        Gstr3bPdfParser p = new Gstr3bPdfParser();
        Gstr3bParsed parsed = p.parse(pdf(
                "GSTR-3B",
                "GSTIN: 29ABCDE1234F1Z5",
                "Year: 2026-27   Period: April",
                "3.1(a) Outward taxable supplies (other than zero rated)",
                "1,00,000.00  18,000.00  9,000.00  9,000.00  0.00",
                "4A(5) All other ITC",
                "5,000.00  2,500.00  2,500.00  0.00"
        ));
        assertEquals("29ABCDE1234F1Z5", parsed.gstin());
        assertEquals("2026-04", parsed.filingPeriod());
        assertEquals(2, parsed.rows().size());
        var r31a = parsed.rows().get(0);
        assertEquals("3.1a", r31a.rowCode());
        assertEquals(0, new BigDecimal("100000.00").compareTo(r31a.taxable()));
        assertEquals(0, new BigDecimal("18000.00").compareTo(r31a.igst()));
        assertEquals(0, new BigDecimal("9000.00").compareTo(r31a.cgst()));
        assertEquals(0, new BigDecimal("9000.00").compareTo(r31a.sgst()));
        assertEquals(0, BigDecimal.ZERO.compareTo(r31a.cess()));
        var r4a5 = parsed.rows().get(1);
        assertEquals("4A5", r4a5.rowCode());
        assertEquals(0, new BigDecimal("5000.00").compareTo(r4a5.igst()));
        assertEquals(0, new BigDecimal("2500.00").compareTo(r4a5.cgst()));
        assertNull(r4a5.taxable());
        assertTrue(parsed.warnings().isEmpty());
    }

    @Test
    void quarterLabelMapsToLastMonth() throws IOException {
        Gstr3bParsed parsed = new Gstr3bPdfParser().parse(pdf(
                "GSTR-3B",
                "GSTIN: 29ABCDE1234F1Z5",
                "Year: 2026-27   Period: April-June",
                "3.1(a) Outward taxable supplies (other than zero rated)",
                "1,000.00  0.00  0.00  0.00  0.00"
        ));
        assertEquals("2026-06", parsed.filingPeriod());
    }

    @Test
    void wrongAmountCountWarns() throws IOException {
        Gstr3bParsed parsed = new Gstr3bPdfParser().parse(pdf(
                "GSTR-3B",
                "GSTIN: 29ABCDE1234F1Z5",
                "Year: 2026-27   Period: April",
                "3.1(a) Outward taxable supplies (other than zero rated)",
                "1,000.00  0.00"
        ));
        var row = parsed.rows().get(0);
        assertNull(row.taxable());
        assertTrue(parsed.warnings().stream()
                .anyMatch(w -> w.contains("3.1a") && w.contains("expected 5 amounts, found 2")));
    }

    @Test
    void garbageThrows() throws IOException {
        assertThrows(FiledReturnParseException.class,
                () -> new Gstr3bPdfParser().parse(pdf("hello world", "nothing here")));
    }
}
