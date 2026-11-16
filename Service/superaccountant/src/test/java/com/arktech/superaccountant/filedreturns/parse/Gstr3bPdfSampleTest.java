package com.arktech.superaccountant.filedreturns.parse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class Gstr3bPdfSampleTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    private static InputStream resource(String name) {
        return Gstr3bPdfSampleTest.class.getResourceAsStream("/filedreturns/samples/" + name);
    }

    @Test
    void parsesCaptainGstr3bSample() throws Exception {
        try (InputStream pdfIn = resource("gstr3b.pdf");
             InputStream expectedIn = resource("gstr3b.expected.json")) {
            assumeTrue(pdfIn != null && expectedIn != null, "sample gstr3b.pdf not provided yet");
            Gstr3bParsed parsed = new Gstr3bPdfParser().parse(pdfIn.readAllBytes());
            JsonNode expected = mapper.readTree(expectedIn);
            assertEquals(expected.get("gstin").asText(), parsed.gstin());
            assertEquals(expected.get("filingPeriod").asText(), parsed.filingPeriod());
            JsonNode expectedRows = expected.get("rows");
            assertEquals(expectedRows.size(), parsed.rows().size());
            for (int i = 0; i < expectedRows.size(); i++) {
                JsonNode e = expectedRows.get(i);
                var row = parsed.rows().get(i);
                assertEquals(e.get("rowCode").asText(), row.rowCode(), "row " + i);
                for (String amount : List.of("taxable", "igst", "cgst", "sgst", "cess")) {
                    JsonNode ev = e.get(amount);
                    BigDecimal actual = switch (amount) {
                        case "taxable" -> row.taxable();
                        case "igst" -> row.igst();
                        case "cgst" -> row.cgst();
                        case "sgst" -> row.sgst();
                        default -> row.cess();
                    };
                    if (ev == null || ev.isNull()) {
                        assertEquals(null, actual, amount + " for row " + i);
                    } else {
                        assertEquals(0, ev.decimalValue().compareTo(actual), amount + " for row " + i);
                    }
                }
            }
        }
    }
}
