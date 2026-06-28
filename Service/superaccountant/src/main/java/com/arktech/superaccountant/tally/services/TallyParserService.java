package com.arktech.superaccountant.tally.services;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.arktech.superaccountant.masters.classifier.LedgerCategoryClassifier;
import com.arktech.superaccountant.masters.classifier.ParsedLedger;
import com.arktech.superaccountant.tally.models.TallyMessage;
import com.arktech.superaccountant.tally.models.Voucher;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class TallyParserService {

    private final ObjectMapper objectMapper;

    private LedgerCategoryClassifier categoryClassifier;

    @Autowired
    public TallyParserService(LedgerCategoryClassifier categoryClassifier) {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.categoryClassifier = categoryClassifier;
    }

    // No-arg constructor for direct instantiation in existing tests
    public TallyParserService() {
        this(new LedgerCategoryClassifier());
    }

    public TallyMessage parseJson(MultipartFile file) throws IOException {
        byte[] bytes = file.getBytes();
        String jsonContent = convertToUtf8String(bytes);
        return objectMapper.readValue(jsonContent, TallyMessage.class);
    }

    /**
     * Parse a Tally masters JSON file (exported ledgers + groups).
     * Expects the tallymessage envelope with ledger and group objects.
     * Returns a list of ParsedLedger with categories resolved via the group hierarchy.
     *
     * @throws IllegalArgumentException if the JSON is malformed, missing required fields,
     *         or uses an unsupported encoding (UTF-32).
     */
    public List<ParsedLedger> parseMastersJson(MultipartFile file) throws IOException {
        byte[] bytes = file.getBytes();

        // Reject UTF-32 files — not supported
        if (isUtf32(bytes)) {
            throw new IllegalArgumentException(
                    "UTF-32 encoded files are not supported. Please export from TallyPrime as UTF-8 or UTF-16.");
        }

        String jsonContent = convertToUtf8String(bytes);

        JsonNode root;
        try {
            root = objectMapper.readTree(jsonContent);
        } catch (Exception e) {
            throw new IllegalArgumentException("Malformed JSON: " + e.getMessage(), e);
        }

        JsonNode tallymessage = root.get("tallymessage");
        if (tallymessage == null || !tallymessage.isArray()) {
            throw new IllegalArgumentException(
                    "Invalid masters JSON: missing 'tallymessage' array envelope.");
        }

        // Phase 1: Extract ALL groups first so hierarchy is complete before classifying ledgers
        Map<String, String> groupHierarchy = new HashMap<>();
        for (JsonNode element : tallymessage) {
            String elementType = resolveElementType(element);
            if ("Group".equalsIgnoreCase(elementType)) {
                JsonNode groupNode = resolveDataNode(element, "group");
                String groupName = resolveElementName(element, groupNode);
                String parentName = textOrNull(groupNode, "parent");
                if (groupName != null && !groupName.isBlank()) {
                    groupHierarchy.put(groupName.toLowerCase(), parentName != null ? parentName.toLowerCase().trim() : "");
                }
            }
        }

        // Phase 2: Parse ledger objects and classify with the complete hierarchy
        List<ParsedLedger> ledgers = new ArrayList<>();
        for (JsonNode element : tallymessage) {
            String elementType = resolveElementType(element);
            if (!"Ledger".equalsIgnoreCase(elementType)) continue;

            JsonNode ledgerNode = resolveDataNode(element, "ledger");
            String name = resolveElementName(element, ledgerNode);
            String guid = textOrNull(ledgerNode, "guid");

            if (name == null || name.isBlank()) {
                continue;
            }
            if (guid == null || guid.isBlank()) {
                throw new IllegalArgumentException(
                        "Ledger '" + name + "' is missing required 'guid' field.");
            }

            String parentGroup = textOrNull(ledgerNode, "parent");
            Boolean gstApplicable = parseBooleanFlag(ledgerNode, "taxtype", "GST");
            Boolean tdsApplicable = resolveBooleanField(ledgerNode, "istdsapplicable", "yes",
                    "istdsapplicable", null);
            String gstApplicabilityType = resolveGstApplicabilityType(ledgerNode);

            com.arktech.superaccountant.masters.models.LedgerCategory category =
                    categoryClassifier.classify(parentGroup, gstApplicable, tdsApplicable, groupHierarchy);

            ledgers.add(ParsedLedger.builder()
                    .name(name)
                    .guid(guid)
                    .parentGroup(parentGroup)
                    .gstApplicable(gstApplicable)
                    .tdsApplicable(tdsApplicable)
                    .gstApplicabilityType(gstApplicabilityType)
                    .category(category)
                    .build());
        }

        return ledgers;
    }

    public Map<String, List<Voucher>> groupByVoucherType(TallyMessage message) {
        if (message.getTallymessage() == null) {
            return Map.of();
        }
        return message.getTallymessage().stream()
                .filter(v -> v.getVouchertypename() != null)
                .collect(Collectors.groupingBy(Voucher::getVouchertypename));
    }

    /**
     * Detects encoding from BOM and converts to UTF-8 string.
     * Supports UTF-16 LE, UTF-16 BE, UTF-8 with BOM, and plain UTF-8.
     * UTF-32 files are detected separately via isUtf32() and rejected before this call.
     */
    private String convertToUtf8String(byte[] bytes) {
        Charset charset = detectCharset(bytes);
        String content = new String(bytes, charset);
        // Strip BOM character if present after decoding
        if (!content.isEmpty() && content.charAt(0) == '\uFEFF') {
            content = content.substring(1);
        }
        return content;
    }

    private Charset detectCharset(byte[] bytes) {
        if (bytes.length >= 2) {
            // UTF-16 LE BOM: FF FE (but NOT FF FE 00 00 which is UTF-32 LE)
            if ((bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xFE) {
                if (bytes.length < 4 || !((bytes[2] & 0xFF) == 0x00 && (bytes[3] & 0xFF) == 0x00)) {
                    return StandardCharsets.UTF_16LE;
                }
                // else: UTF-32 LE — isUtf32() handles rejection
            }
            // UTF-16 BE BOM: FE FF (but NOT 00 00 FE FF which is UTF-32 BE)
            if ((bytes[0] & 0xFF) == 0xFE && (bytes[1] & 0xFF) == 0xFF) {
                return StandardCharsets.UTF_16BE;
            }
        }
        if (bytes.length >= 3) {
            // UTF-8 BOM: EF BB BF
            if ((bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF) {
                return StandardCharsets.UTF_8;
            }
        }
        return StandardCharsets.UTF_8;
    }

    /**
     * Returns true if the byte array starts with a UTF-32 BOM.
     * UTF-32 BE: 00 00 FE FF
     * UTF-32 LE: FF FE 00 00
     */
    private boolean isUtf32(byte[] bytes) {
        if (bytes.length < 4) return false;
        // UTF-32 BE BOM
        if ((bytes[0] & 0xFF) == 0x00 && (bytes[1] & 0xFF) == 0x00
                && (bytes[2] & 0xFF) == 0xFE && (bytes[3] & 0xFF) == 0xFF) {
            return true;
        }
        // UTF-32 LE BOM
        if ((bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xFE
                && (bytes[2] & 0xFF) == 0x00 && (bytes[3] & 0xFF) == 0x00) {
            return true;
        }
        return false;
    }

    /**
     * Determines element type from either metadata.type (Tally native export)
     * or by checking for "ledger"/"group" wrapper keys (simplified test format).
     */
    private String resolveElementType(JsonNode element) {
        JsonNode metadata = element.get("metadata");
        if (metadata != null && metadata.isObject()) {
            String type = textOrNull(metadata, "type");
            if (type != null) return type;
        }
        if (element.has("ledger")) return "Ledger";
        if (element.has("group")) return "Group";
        return null;
    }

    /**
     * Returns the data node for a ledger/group element.
     * For Tally native format (flat with metadata), the element itself is the data node.
     * For simplified format (nested under "ledger"/"group" key), returns the nested node.
     */
    private JsonNode resolveDataNode(JsonNode element, String wrapperKey) {
        JsonNode nested = element.get(wrapperKey);
        if (nested != null && nested.isObject()) return nested;
        return element;
    }

    /**
     * Resolves the name from either metadata.name (Tally native) or the data node's "name" field.
     */
    private String resolveElementName(JsonNode element, JsonNode dataNode) {
        JsonNode metadata = element.get("metadata");
        if (metadata != null && metadata.isObject()) {
            String name = textOrNull(metadata, "name");
            if (name != null) return name;
        }
        return textOrNull(dataNode, "name");
    }

    /**
     * Resolves a boolean classification field, handling both:
     * - String comparison (e.g., taxtype == "GST")
     * - Native boolean fields (e.g., istdsapplicable: true/false)
     * - String fields with "Applicable" keyword (e.g., gstapplicable: "Applicable")
     */
    private Boolean resolveBooleanField(JsonNode node, String stringField, String trueValue,
                                         String boolField, String applicableField) {
        // Check native boolean field first (Tally native format)
        if (boolField != null) {
            JsonNode boolNode = node.get(boolField);
            if (boolNode != null && boolNode.isBoolean()) {
                return boolNode.asBoolean() ? Boolean.TRUE : Boolean.FALSE;
            }
        }

        // Check "applicable" string field (e.g., "gstapplicable": "Applicable")
        if (applicableField != null) {
            String applicableVal = textOrNull(node, applicableField);
            if (applicableVal != null && applicableVal.toLowerCase().contains("applicable")
                    && !applicableVal.toLowerCase().contains("not applicable")) {
                return Boolean.TRUE;
            }
        }

        // Fall back to string comparison (e.g., taxtype == "GST")
        JsonNode field = node.get(stringField);
        if (field == null || field.isNull()) return null;
        if (field.isBoolean()) {
            return field.asBoolean() ? Boolean.TRUE : Boolean.FALSE;
        }
        String val = field.asText("").trim();
        if (val.isEmpty()) return null;
        return val.equalsIgnoreCase(trueValue) ? Boolean.TRUE : Boolean.FALSE;
    }

    /**
     * Resolves GST applicability type from Tally fields.
     * Maps "gstapplicable" string values to GstApplicabilityType enum names.
     */
    private String resolveGstApplicabilityType(JsonNode node) {
        String gstApplicable = textOrNull(node, "gstapplicable");
        if (gstApplicable == null) return null;

        String cleaned = gstApplicable.replaceAll("[\\x00-\\x1F]", "").trim().toLowerCase();
        if (cleaned.contains("not applicable")) return "NOT_APPLICABLE";
        if (cleaned.contains("applicable")) return "TAXABLE";
        if (cleaned.contains("exempt")) return "EXEMPT";
        return null;
    }

    private String textOrNull(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        if (field == null || field.isNull()) return null;
        String text = field.asText("").trim();
        return text.isEmpty() ? null : text;
    }

    /**
     * Parse a boolean flag from a JSON field. Returns true if the field value
     * matches the expected value (case-insensitive), false otherwise, null if field absent.
     */
    private Boolean parseBooleanFlag(JsonNode node, String fieldName, String trueValue) {
        String val = textOrNull(node, fieldName);
        if (val == null) return null;
        return val.equalsIgnoreCase(trueValue) ? Boolean.TRUE : Boolean.FALSE;
    }
}
