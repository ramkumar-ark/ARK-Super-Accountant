package com.arktech.superaccountant.filedreturns.parse;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Gstr1JsonParserTest {

    private byte[] fixture;

    @BeforeEach
    void load() throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/filedreturns/gstr1-synthetic.json")) {
            fixture = in.readAllBytes();
        }
    }

    @Test
    void parsesHeaderAndInvoiceLines() {
        Gstr1Parsed parsed = new Gstr1JsonParser().parse(fixture);
        assertEquals("29ABCDE1234F1Z5", parsed.gstin());
        assertEquals("2026-04", parsed.filingPeriod());

        List<com.arktech.superaccountant.filedreturns.FiledReturnLine> b2b = parsed.lines().stream()
                .filter(l -> l.getTableCode().equals("B2B")).toList();
        assertEquals(2, b2b.size());
        assertTrue(b2b.stream().anyMatch(l -> l.getRate() != null && l.getRate().doubleValue() == 18.0));
        assertTrue(b2b.stream().anyMatch(l -> l.getRate() != null && l.getRate().doubleValue() == 5.0));
        b2b.forEach(l -> {
            assertEquals(java.time.LocalDate.of(2026, 4, 3), l.getDocDate());
            assertEquals("INTER", l.getSupplyType());
            assertEquals("27AAECS6620H1ZQ", l.getCounterpartyGstin());
            assertEquals("INV-100", l.getDocNumber());
        });
    }

    @Test
    void parsesSchemaVariants() throws IOException {
        // ntty C -> CN is in the base fixture; flat exp itms too; cdnra section absent.
        Gstr1Parsed base = new Gstr1JsonParser().parse(fixture);
        assertEquals(15, base.lines().size());

        ObjectMapper mapper = new ObjectMapper();
        byte[] wrapped = mapper.writeValueAsBytes(
                mapper.readTree("{\"data\":" + new String(fixture) + "}"));
        Gstr1Parsed wrappedParsed = new Gstr1JsonParser().parse(wrapped);
        assertEquals(15, wrappedParsed.lines().size());
    }

    @Test
    void b2csaCarriesOriginalPeriod() {
        Gstr1Parsed parsed = new Gstr1JsonParser().parse(fixture);
        List<com.arktech.superaccountant.filedreturns.FiledReturnLine> b2csa = parsed.lines().stream()
                .filter(l -> l.getTableCode().equals("B2CSA")).toList();
        assertEquals(2, b2csa.size());
        b2csa.forEach(l -> assertEquals("2026-03", l.getOriginalPeriod()));
    }

    @Test
    void parsesSplitHsnSections() throws IOException {
        byte[] v3;
        try (InputStream in = getClass().getResourceAsStream("/filedreturns/gstr1-synthetic-v3.json")) {
            v3 = in.readAllBytes();
        }
        Gstr1Parsed parsed = new Gstr1JsonParser().parse(v3);
        assertFalse(parsed.warnings().stream().anyMatch(w -> w.contains("hsn_b2b") || w.contains("hsn_b2c")));

        List<com.arktech.superaccountant.filedreturns.FiledReturnLine> hsn = parsed.lines().stream()
                .filter(l -> l.getTableCode().equals("HSN")).toList();
        assertEquals(3, hsn.size());
        assertEquals(2, hsn.stream().filter(l -> "B2B".equals(l.getHsnCategory())).count());
        assertEquals(1, hsn.stream().filter(l -> "B2C".equals(l.getHsnCategory())).count());
        assertTrue(hsn.stream().anyMatch(l -> "9983".equals(l.getLabel())
                && l.getTaxable().doubleValue() == 1000.00
                && l.getIgst().doubleValue() == 180.00));
        assertTrue(hsn.stream().anyMatch(l -> "9403".equals(l.getLabel())
                && l.getCgst().doubleValue() == 9.00));
    }

    @Test
    void parsesCurrentDocIssueShape() throws IOException {
        byte[] v3;
        try (InputStream in = getClass().getResourceAsStream("/filedreturns/gstr1-synthetic-v3.json")) {
            v3 = in.readAllBytes();
        }
        Gstr1Parsed parsed = new Gstr1JsonParser().parse(v3);
        List<com.arktech.superaccountant.filedreturns.FiledReturnLine> docs = parsed.lines().stream()
                .filter(l -> l.getTableCode().equals("DOC")).toList();
        assertEquals(2, docs.size());
        assertEquals("1", docs.get(0).getDocNumber());
        assertEquals(111, docs.get(0).getInvoiceValue().intValueExact());
        assertEquals("2", docs.get(1).getDocNumber());
        assertEquals(5, docs.get(1).getInvoiceValue().intValueExact());
        assertEquals("CN-1-CN-5", docs.get(1).getLabel());
    }

    @Test
    void rejectsNonGstr1Json() {
        assertThrows(FiledReturnParseException.class,
                () -> new Gstr1JsonParser().parse("{\"foo\":1}".getBytes()));
    }
}
