package com.arktech.superaccountant.tallywriteback.services;

import com.arktech.superaccountant.tallywriteback.models.TallyChangeType;
import com.arktech.superaccountant.tallywriteback.models.TallyField;
import com.arktech.superaccountant.tallywriteback.payload.ExportLine;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TallyImportRendererTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final TallyImportRenderer renderer = new TallyImportRenderer();

    private static String normalize(String text) {
        return text.replace("\r\n", "\n").replace('\r', '\n');
    }

    private static String fixture() throws IOException {
        try (InputStream stream = TallyImportRendererTest.class
                .getResourceAsStream("/fixtures/tally-pan-changes-sample.json")) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String clearingFixture() throws IOException {
        try (InputStream stream = TallyImportRendererTest.class
                .getResourceAsStream("/fixtures/tally-pan-changes-clearing-sample.json")) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static List<ExportLine> sampleLines() {
        return List.of(
                new ExportLine(TallyChangeType.ALTER_FIELD, TallyField.PAN,
                        "SAMPLE SUPPLIER STEELS", "PQRST5678U"),
                new ExportLine(TallyChangeType.ALTER_FIELD, TallyField.PAN,
                        "ANANDHA & \"CO\" TRADERS", "ABCDE1234F"));
    }

    @Test
    void rendersGoldenImportFileWithEscapedNamesInSortedOrder() throws IOException {
        String expected = normalize(fixture());
        String actual = normalize(renderer.render(sampleLines()));

        assertEquals(expected, actual);
    }

    @Test
    void eachMessageHasOnlyMetadataNameAndFieldKeys() throws IOException {
        JsonNode root = MAPPER.readTree(renderer.render(sampleLines()));

        assertEquals(List.of("static_variables", "tallymessage"), fieldNames(root));

        List<JsonNode> messages = new ArrayList<>();
        root.get("tallymessage").forEach(messages::add);
        assertEquals(2, messages.size());
        for (JsonNode message : messages) {
            assertEquals(Set.of("metadata", "name", "incometaxnumber"), fieldNameSet(message));
            assertEquals(Set.of("type", "action", "name"), fieldNameSet(message.get("metadata")));
        }
    }

    @Test
    void clearingValueRendersGoldenFileWithEmptyFieldAndKeepsKey() throws IOException {
        List<ExportLine> lines = List.of(new ExportLine(TallyChangeType.ALTER_FIELD, TallyField.PAN,
                "SAMPLE SUPPLIER STEELS", ""));

        String actual = normalize(renderer.render(lines));
        assertEquals(normalize(clearingFixture()), actual);

        JsonNode message = MAPPER.readTree(actual).get("tallymessage").get(0);
        assertEquals("", message.get("incometaxnumber").asText());
        org.junit.jupiter.api.Assertions.assertTrue(
                fieldNameSet(message).contains("incometaxnumber"));
    }

    @Test
    void unknownChangeTypeThrowsIllegalArgument() {
        ExportLine line = new ExportLine(TallyChangeType.CREATE_LEDGER, TallyField.PAN,
                "SAMPLE SUPPLIER STEELS", "PQRST5678U");

        assertThrows(IllegalArgumentException.class, () -> renderer.render(List.of(line)));
    }

    @Test
    void emptyListRendersRootEnvelopeDeterministically() {
        String expected = """
                {
                  "static_variables" : [
                    {
                      "name" : "svMstImportFormat",
                      "value" : "jsonex"
                    }
                  ],
                  "tallymessage" : [ ]
                }
                """;

        String first = renderer.render(List.of());
        String second = renderer.render(List.of());

        assertEquals(expected, first);
        assertEquals(first, second);
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private static Set<String> fieldNameSet(JsonNode node) {
        return fieldNames(node).stream().collect(Collectors.toSet());
    }
}
