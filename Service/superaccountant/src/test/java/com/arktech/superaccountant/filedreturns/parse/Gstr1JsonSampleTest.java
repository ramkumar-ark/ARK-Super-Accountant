package com.arktech.superaccountant.filedreturns.parse;

import com.arktech.superaccountant.filedreturns.FiledReturnLine;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class Gstr1JsonSampleTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    private static InputStream resource(String name) {
        return Gstr1JsonSampleTest.class.getResourceAsStream("/filedreturns/samples/" + name);
    }

    @Test
    void parsesCaptainSample() throws IOException {
        try (InputStream json = resource("gstr1.json");
             InputStream expectedIn = resource("gstr1.expected.json")) {
            assumeTrue(json != null && expectedIn != null, "sample gstr1.json not provided yet");
            Gstr1Parsed parsed = new Gstr1JsonParser().parse(json.readAllBytes());
            JsonNode expected = mapper.readTree(expectedIn);
            assertEquals(expected.get("gstin").asText(), parsed.gstin());
            assertEquals(expected.get("filingPeriod").asText(), parsed.filingPeriod());
            JsonNode tables = expected.get("tables");
            tables.fieldNames().forEachRemaining(tableCode -> {
                JsonNode t = tables.get(tableCode);
                var lines = parsed.lines().stream().filter(l -> l.getTableCode().equals(tableCode)).toList();
                assertEquals(t.get("lines").asInt(), lines.size(), "lines for " + tableCode);
                assertEquals(t.get("taxable").decimalValue(), lines.stream()
                        .map(FiledReturnLine::getTaxable).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add),
                        "taxable for " + tableCode);
                assertEquals(t.get("igst").decimalValue(), lines.stream()
                        .map(FiledReturnLine::getIgst).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add),
                        "igst for " + tableCode);
                assertEquals(t.get("cgst").decimalValue(), lines.stream()
                        .map(FiledReturnLine::getCgst).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add),
                        "cgst for " + tableCode);
                assertEquals(t.get("sgst").decimalValue(), lines.stream()
                        .map(FiledReturnLine::getSgst).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add),
                        "sgst for " + tableCode);
                assertEquals(t.get("cess").decimalValue(), lines.stream()
                        .map(FiledReturnLine::getCess).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add),
                        "cess for " + tableCode);
            });
        }
    }

    @Test
    void parsesCaptainIffSample() throws IOException {
        try (InputStream json = resource("iff.json");
             InputStream expectedIn = resource("iff.expected.json")) {
            assumeTrue(json != null && expectedIn != null, "sample iff.json not provided yet");
            Gstr1Parsed parsed = new Gstr1JsonParser().parse(json.readAllBytes());
            JsonNode expected = mapper.readTree(expectedIn);
            assertTrue(parsed.lines().size() > 0 || expected.get("tables").isEmpty());
        }
    }
}
