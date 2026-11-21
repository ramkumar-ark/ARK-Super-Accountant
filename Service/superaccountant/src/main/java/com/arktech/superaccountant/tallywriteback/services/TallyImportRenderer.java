package com.arktech.superaccountant.tallywriteback.services;

import com.arktech.superaccountant.tallywriteback.models.TallyChangeType;
import com.arktech.superaccountant.tallywriteback.models.TallyField;
import com.arktech.superaccountant.tallywriteback.payload.ExportLine;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Component
public class TallyImportRenderer {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final DefaultPrettyPrinter PRINTER = new DefaultPrettyPrinter()
            .withObjectIndenter(new DefaultIndenter("  ", "\n"))
            .withArrayIndenter(new DefaultIndenter("  ", "\n"));

    private static final boolean INCLUDE_STATIC_VARIABLES = true;
    private static final String STATIC_VARIABLE_NAME = "svMstImportFormat";
    private static final String STATIC_VARIABLE_VALUE = "jsonex";

    private record ChangeKey(TallyChangeType changeType, TallyField field) {
    }

    private record FieldRenderer(String action, String fieldKey) {
    }

    private static final Map<ChangeKey, FieldRenderer> RENDERERS = Map.of(
            new ChangeKey(TallyChangeType.ALTER_FIELD, TallyField.PAN),
            new FieldRenderer("Alter", "incometaxnumber"));

    public String render(List<ExportLine> lines) {
        ObjectNode root = MAPPER.createObjectNode();
        if (INCLUDE_STATIC_VARIABLES) {
            ArrayNode staticVariables = root.putArray("static_variables");
            ObjectNode staticVariable = staticVariables.addObject();
            staticVariable.put("name", STATIC_VARIABLE_NAME);
            staticVariable.put("value", STATIC_VARIABLE_VALUE);
        }

        ArrayNode tallyMessage = root.putArray("tallymessage");
        lines.stream()
                .sorted(Comparator.comparing(ExportLine::ledgerName))
                .map(this::message)
                .forEach(tallyMessage::add);

        try {
            return MAPPER.writer(PRINTER).writeValueAsString(root) + "\n";
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to render Tally import file", e);
        }
    }

    private ObjectNode message(ExportLine line) {
        FieldRenderer renderer = RENDERERS.get(new ChangeKey(line.changeType(), line.field()));
        if (renderer == null) {
            throw new IllegalArgumentException(
                    "Unsupported Tally change: " + line.changeType() + "/" + line.field());
        }

        ObjectNode message = MAPPER.createObjectNode();
        ObjectNode metadata = message.putObject("metadata");
        metadata.put("type", "Ledger");
        metadata.put("action", renderer.action());
        metadata.put("name", line.ledgerName());
        message.put("name", line.ledgerName());
        message.put(renderer.fieldKey(), line.value());
        return message;
    }
}
