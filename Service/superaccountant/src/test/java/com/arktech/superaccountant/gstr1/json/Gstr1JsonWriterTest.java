package com.arktech.superaccountant.gstr1.json;

import com.arktech.superaccountant.gstr1.models.PreparedReturn;
import com.arktech.superaccountant.gstr1.models.PreparedReturnRow;
import com.arktech.superaccountant.workflow.models.FilerFrequency;
import com.arktech.superaccountant.workflow.models.FilerProfile;
import com.arktech.superaccountant.workflow.models.Workflow;
import com.arktech.superaccountant.workflow.models.WorkflowStage;
import com.arktech.superaccountant.workflow.models.WorkflowType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Gstr1JsonWriterTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void writesHeaderAndIntraStateB2bInvoice() {
        Workflow workflow = workflow(FilerFrequency.MONTHLY);
        List<PreparedReturnRow> rows = new ArrayList<>();
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("gstin", "33BBBBB1111B1ZU");
        p.put("invoiceNo", "INV-001");
        p.put("invoiceDate", "2025-04-15");
        p.put("pos", "33");
        p.put("rate", new BigDecimal("18"));
        p.put("taxableValue", new BigDecimal("10000"));
        p.put("igst", BigDecimal.ZERO);
        p.put("cgst", new BigDecimal("900"));
        p.put("sgst", new BigDecimal("900"));
        p.put("cess", BigDecimal.ZERO);
        p.put("invoiceValue", new BigDecimal("11800"));
        rows.add(row(workflow.getId(), "B2B", "b2b:v1", p));
        PreparedReturn prepared = prepared(workflow.getId());

        List<PortalFilePart> parts = writer().write(prepared, rows, workflow, "33AAAAA0000A1Z9",
                profile(FilerFrequency.MONTHLY), WriterOptions.defaults());

        assertEquals(1, parts.size());
        PortalFilePart part = parts.get(0);
        assertEquals("GSTR1_042025_33AAAAA0000A1Z9.json", part.fileName());
        assertEquals(1, part.itemCount());
        String json = new String(part.bytes(), StandardCharsets.UTF_8);
        assertEquals("""
                {"gstin":"33AAAAA0000A1Z9","fp":"042025","version":"GST3.2.4","hash":"hash",\
                "b2b":[{"ctin":"33BBBBB1111B1ZU","inv":[{"inum":"INV-001","idt":"15-04-2025",\
                "val":11800,"pos":"33","rchrg":"N","inv_typ":"R","itms":[{"num":1801,\
                "itm_det":{"txval":10000,"rt":18,"camt":900,"samt":900,"csamt":0}}]}]}]}""", json);
    }

    @Test
    void omitsAllSectionsWhenNoRows() throws Exception {
        Workflow workflow = workflow(FilerFrequency.MONTHLY);

        List<PortalFilePart> parts = writer().write(prepared(workflow.getId()), List.of(), workflow,
                "33AAAAA0000A1Z9", profile(FilerFrequency.MONTHLY), WriterOptions.defaults());

        assertEquals(1, parts.size());
        assertEquals(0, parts.get(0).itemCount());
        assertEquals("""
                {"gstin":"33AAAAA0000A1Z9","fp":"042025","version":"GST3.2.4","hash":"hash"}""",
                new String(parts.get(0).bytes(), StandardCharsets.UTF_8));
    }

    @Test    void nilOnlyPeriodHasNoEmptySections() throws Exception {
        Workflow workflow = workflow(FilerFrequency.MONTHLY);
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("pos", "33");
        p.put("taxableValue", new BigDecimal("5000"));
        List<PreparedReturnRow> rows = List.of(row(workflow.getId(), "NIL", "nil:v1", p));

        List<PortalFilePart> parts = writer().write(prepared(workflow.getId()), rows, workflow,
                "33AAAAA0000A1Z9", profile(FilerFrequency.MONTHLY), WriterOptions.defaults());

        JsonNode root = MAPPER.readTree(new String(parts.get(0).bytes(), StandardCharsets.UTF_8));
        assertEquals(1, parts.size());
        assertTrue(root.has("nil"));
        for (String section : List.of("b2b", "b2cl", "b2cs", "exp", "cdnr", "cdnur", "at", "txpd",
                "doc_issue", "hsn")) {
            assertTrue(!root.has(section), "unexpected section " + section);
        }
        assertTrue(!root.get("nil").isArray());
        assertTrue(root.get("nil").has("inv"));
        assertTrue(root.get("nil").get("inv").size() > 0);
    }

    @Test
    void nilRowsAreAggregatedPerSupplyType() throws Exception {
        Workflow workflow = workflow(FilerFrequency.MONTHLY);
        List<PreparedReturnRow> rows = new ArrayList<>();
        for (String[] r : new String[][] {{"27", "100"}, {"29", "250"}, {"33", "40"}}) {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("pos", r[0]);
            p.put("taxableValue", new BigDecimal(r[1]));
            rows.add(row(workflow.getId(), "NIL", "nil:" + r[0], p));
        }

        List<PortalFilePart> parts = writer().write(prepared(workflow.getId()), rows, workflow,
                "33AAAAA0000A1Z9", profile(FilerFrequency.MONTHLY), WriterOptions.defaults());

        JsonNode inv = MAPPER.readTree(new String(parts.get(0).bytes(), StandardCharsets.UTF_8))
                .get("nil").get("inv");
        assertEquals(2, inv.size());
        for (JsonNode entry : inv) {
            BigDecimal expected = new BigDecimal(
                    "INTERB2C".equals(entry.get("sply_ty").asText()) ? "350" : "40");
            assertEquals(0, expected.compareTo(entry.get("nil_amt").decimalValue()));
        }
    }

    @Test
    void q6bOmitsEinvoiceSectionsWhenIrpTablesExcluded() throws Exception {
        Workflow workflow = workflow(FilerFrequency.MONTHLY);
        List<PreparedReturnRow> rows = new ArrayList<>();
        rows.add(row(workflow.getId(), "B2B", "b2b:v1", b2bPayload()));
        rows.add(row(workflow.getId(), "CDNR", "cdnr:v1", cdnrPayload()));
        rows.add(row(workflow.getId(), "EXP", "exp:v1", expPayload()));
        FilerProfile profile = profile(FilerFrequency.MONTHLY);
        profile.setEinvoiceEnabled(true);

        List<PortalFilePart> omitted = writer().write(prepared(workflow.getId()), rows, workflow,
                "33AAAAA0000A1Z9", profile, new WriterOptions(false));
        JsonNode root = MAPPER.readTree(new String(omitted.get(0).bytes(), StandardCharsets.UTF_8));
        assertTrue(!root.has("b2b") && !root.has("cdnr") && !root.has("exp"));

        List<PortalFilePart> included = writer().write(prepared(workflow.getId()), rows, workflow,
                "33AAAAA0000A1Z9", profile, new WriterOptions(true));
        JsonNode root2 = MAPPER.readTree(new String(included.get(0).bytes(), StandardCharsets.UTF_8));
        assertTrue(root2.has("b2b") && root2.has("cdnr") && root2.has("exp"));
    }

    @Test    void splitsAtNineteenThousandItems() throws Exception {
        Workflow workflow = workflow(FilerFrequency.MONTHLY);
        List<PreparedReturnRow> rows = new ArrayList<>();
        for (int i = 1; i <= 19001; i++) {
            rows.add(row(workflow.getId(), "B2CS", "b2cs:v" + i, b2csPayload("INV-" + i)));
        }

        List<PortalFilePart> parts = writer().write(prepared(workflow.getId()), rows, workflow,
                "33AAAAA0000A1Z9", profile(FilerFrequency.MONTHLY), WriterOptions.defaults());

        assertEquals(2, parts.size());
        assertTrue(parts.get(0).itemCount() <= 19000);
        assertEquals(19001, parts.get(0).itemCount() + parts.get(1).itemCount());
        for (int i = 0; i < parts.size(); i++) {
            assertEquals("GSTR1_042025_33AAAAA0000A1Z9_" + (i + 1) + ".json", parts.get(i).fileName());
            JsonNode root = MAPPER.readTree(new String(parts.get(i).bytes(), StandardCharsets.UTF_8));
            assertEquals("33AAAAA0000A1Z9", root.get("gstin").asText());
            assertEquals("042025", root.get("fp").asText());
            assertTrue(root.has("b2cs"));
        }
    }

    @Test    void deterministicOutput() throws Exception {
        Workflow workflow = workflow(FilerFrequency.MONTHLY);
        List<PreparedReturnRow> rows = new ArrayList<>();
        rows.add(row(workflow.getId(), "B2B", "b2b:v1", b2bPayload()));
        rows.add(row(workflow.getId(), "B2CS", "b2cs:v1", b2csPayload("INV-2")));

        List<PortalFilePart> first = writer().write(prepared(workflow.getId()), rows, workflow,
                "33AAAAA0000A1Z9", profile(FilerFrequency.MONTHLY), WriterOptions.defaults());
        List<PortalFilePart> second = writer().write(prepared(workflow.getId()), rows, workflow,
                "33AAAAA0000A1Z9", profile(FilerFrequency.MONTHLY), WriterOptions.defaults());

        assertEquals(first.size(), second.size());
        for (int i = 0; i < first.size(); i++) {
            assertEquals(first.get(i).fileName(), second.get(i).fileName());
            assertEquals(first.get(i).itemCount(), second.get(i).itemCount());
            assertArrayEquals(first.get(i).bytes(), second.get(i).bytes());
        }
    }

    @Test    void amountsAreAtMostTwoDecimals() throws Exception {
        Workflow workflow = workflow(FilerFrequency.MONTHLY);
        Map<String, Object> p = b2bPayload();
        p.put("taxableValue", new BigDecimal("10000.5"));
        p.put("cgst", new BigDecimal("90.045"));
        p.put("sgst", new BigDecimal("90.045"));
        p.put("invoiceValue", new BigDecimal("10181.095"));
        List<PreparedReturnRow> rows = List.of(row(workflow.getId(), "B2B", "b2b:v1", p));

        List<PortalFilePart> parts = writer().write(prepared(workflow.getId()), rows, workflow,
                "33AAAAA0000A1Z9", profile(FilerFrequency.MONTHLY), WriterOptions.defaults());

        String json = new String(parts.get(0).bytes(), StandardCharsets.UTF_8);
        assertTrue(json.contains("\"txval\":10000.5"));
        assertTrue(json.contains("\"camt\":90.05"));
        assertTrue(json.contains("\"samt\":90.05"));
        assertTrue(json.contains("\"val\":10181.1"));
        assertTrue(!json.contains("10000.50"));
    }

    private Gstr1JsonWriter writer() {
        return new Gstr1JsonWriter();
    }

    private static Workflow workflow(FilerFrequency frequency) {
        Workflow w = new Workflow();
        w.setId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        w.setOrganizationId(UUID.fromString("22222222-2222-2222-2222-222222222222"));
        w.setType(WorkflowType.GSTR1);
        w.setPeriodStart(LocalDate.of(2025, 4, 1));
        w.setPeriodEnd(LocalDate.of(2025, 4, 30));
        w.setPeriodKey("2025-04");
        w.setFinancialYear(2025);
        w.setStage(WorkflowStage.PREPARE);
        return w;
    }

    private static FilerProfile profile(FilerFrequency frequency) {
        FilerProfile p = new FilerProfile();
        p.setId(UUID.fromString("33333333-3333-3333-3333-333333333333"));
        p.setOrganizationId(UUID.fromString("22222222-2222-2222-2222-222222222222"));
        p.setFrequency(frequency);
        return p;
    }

    private static PreparedReturn prepared(UUID workflowId) {
        PreparedReturn prepared = new PreparedReturn();
        prepared.setId(UUID.fromString("44444444-4444-4444-4444-444444444444"));
        prepared.setWorkflowId(workflowId);
        prepared.setReturnKind("GSTR1");
        return prepared;
    }

    private static Map<String, Object> b2bPayload() {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("gstin", "33BBBBB1111B1ZU");
        p.put("invoiceNo", "INV-001");
        p.put("invoiceDate", "2025-04-15");
        p.put("pos", "33");
        p.put("rate", new BigDecimal("18"));
        p.put("taxableValue", new BigDecimal("10000"));
        p.put("igst", BigDecimal.ZERO);
        p.put("cgst", new BigDecimal("900"));
        p.put("sgst", new BigDecimal("900"));
        p.put("cess", BigDecimal.ZERO);
        p.put("invoiceValue", new BigDecimal("11800"));
        return p;
    }

    private static Map<String, Object> b2csPayload(String invoiceRef) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("invoiceRef", invoiceRef);
        p.put("pos", "33");
        p.put("rate", new BigDecimal("18"));
        p.put("taxableValue", new BigDecimal("1000"));
        p.put("igst", BigDecimal.ZERO);
        p.put("cgst", new BigDecimal("90"));
        p.put("sgst", new BigDecimal("90"));
        p.put("cess", BigDecimal.ZERO);
        return p;
    }

    private static Map<String, Object> cdnrPayload() {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("noteType", "C");
        p.put("gstin", "33BBBBB1111B1ZU");
        p.put("noteNo", "CN-001");
        p.put("noteDate", "2025-04-20");
        p.put("rate", new BigDecimal("18"));
        p.put("taxableValue", new BigDecimal("-1000"));
        p.put("igst", BigDecimal.ZERO);
        p.put("cgst", new BigDecimal("-90"));
        p.put("sgst", new BigDecimal("-90"));
        p.put("cess", BigDecimal.ZERO);
        p.put("invoiceValue", new BigDecimal("-1180"));
        return p;
    }

    private static Map<String, Object> expPayload() {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("invoiceNo", "EXP-001");
        p.put("invoiceDate", "2025-04-18");
        p.put("exportType", "WP");
        p.put("country", "US");
        p.put("rate", new BigDecimal("0"));
        p.put("taxableValue", new BigDecimal("50000"));
        p.put("igst", BigDecimal.ZERO);
        p.put("cgst", BigDecimal.ZERO);
        p.put("sgst", BigDecimal.ZERO);
        p.put("cess", BigDecimal.ZERO);
        p.put("invoiceValue", new BigDecimal("50000"));
        return p;
    }

    private static PreparedReturnRow row(UUID workflowId, String tableCode, String rowKey,
                                         Map<String, Object> payload) {
        PreparedReturnRow row = new PreparedReturnRow();
        row.setId(UUID.randomUUID());
        row.setPreparedReturnId(UUID.fromString("44444444-4444-4444-4444-444444444444"));
        row.setTableCode(tableCode);
        row.setRowKey(rowKey);
        row.setPayload(toJson(payload));
        row.setAmendment(false);
        return row;
    }

    private static String toJson(Map<String, Object> payload) {
        try {
            return MAPPER.writeValueAsString(payload);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
