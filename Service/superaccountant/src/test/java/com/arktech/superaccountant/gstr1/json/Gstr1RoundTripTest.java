package com.arktech.superaccountant.gstr1.json;

import com.arktech.superaccountant.filedreturns.FiledReturnLine;
import com.arktech.superaccountant.filedreturns.parse.Gstr1JsonParser;
import com.arktech.superaccountant.filedreturns.parse.Gstr1Parsed;
import com.arktech.superaccountant.gstr1.models.PreparedReturn;
import com.arktech.superaccountant.gstr1.models.PreparedReturnRow;
import com.arktech.superaccountant.workflow.models.FilerFrequency;
import com.arktech.superaccountant.workflow.models.FilerProfile;
import com.arktech.superaccountant.workflow.models.Workflow;
import com.arktech.superaccountant.workflow.models.WorkflowStage;
import com.arktech.superaccountant.workflow.models.WorkflowType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Round trip: prepared rows → Gstr1JsonWriter → portal JSON → Gstr1JsonParser →
 * filed return lines. Asserts the filed return reproduces what was prepared per
 * table: counts, taxable, tax heads, HSN B2B/B2C split and doc series.
 */
class Gstr1RoundTripTest {

    private static final String ORG_GSTIN = "33AAAAA0000A1Z9";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final Gstr1JsonParser parser = new Gstr1JsonParser();

    @Test
    void matchesCommittedSyntheticFixture() throws Exception {
        Workflow workflow = workflow();
        List<PortalFilePart> parts = allTableParts(workflow, allRows());
        byte[] fixture = getClass().getResourceAsStream("/gstr1/portal-synthetic.json").readAllBytes();
        assertEquals(1, parts.size());
        assertArrayEquals(fixture, parts.get(0).bytes());
    }

    private List<PreparedReturnRow> allRows() {
        Workflow workflow = workflow();
        List<PreparedReturnRow> rows = new ArrayList<>();
        rows.add(row("B2B", "b2b:v1", b2bIntraPayload()));
        rows.add(row("B2B", "b2b:v2", b2bInterPayload()));
        rows.add(row("B2CL", "b2cl:v1", b2clPayload()));
        rows.add(row("B2CS", "b2cs:v1", b2csIntraPayload()));
        rows.add(row("B2CS", "b2cs:v2", b2csInterPayload()));
        rows.add(row("EXP", "exp:v1", expPayload()));
        rows.add(row("NIL", "nil:v1", nilPayload()));
        rows.add(row("CDNR", "cdnr:v1", cdnrPayload()));
        rows.add(row("CDNUR", "cdnur:v1", cdnurPayload()));
        rows.add(row("AT", "at:v1", atPayload()));
        rows.add(row("TXPD", "txpd:v1", txpdPayload()));
        rows.add(row("HSN", "hsn:v1", hsnB2bPayload()));
        rows.add(row("HSN", "hsn:v2", hsnB2cPayload()));
        rows.add(row("DOCS", "docs:v1", invoiceSeriesPayload()));
        rows.add(row("DOCS", "docs:v2", creditNoteSeriesPayload()));
        return rows;
    }

    @Test
    void roundTripsAllTables() throws Exception {
        Workflow workflow = workflow();
        Gstr1Parsed parsed = writeAndParse(workflow, allRows());

        assertTrue(parsed.warnings().isEmpty(), String.join("; ", parsed.warnings()));
        assertEquals(ORG_GSTIN, parsed.gstin());
        assertEquals("2025-04", parsed.filingPeriod());

        List<FiledReturnLine> b2b = lines(parsed, "B2B");
        assertEquals(2, b2b.size());
        assertEquals(sum(b2b, FiledReturnLine::getTaxable), bd("15000"));
        assertEquals(sum(b2b, FiledReturnLine::getCgst), bd("900"));
        assertEquals(sum(b2b, FiledReturnLine::getSgst), bd("900"));
        assertEquals(sum(b2b, FiledReturnLine::getIgst), bd("900"));

        List<FiledReturnLine> b2cl = lines(parsed, "B2CL");
        assertEquals(1, b2cl.size());
        assertEquals(sum(b2cl, FiledReturnLine::getTaxable), bd("2000"));
        assertEquals(sum(b2cl, FiledReturnLine::getIgst), bd("360"));
        assertEquals("27", b2cl.get(0).getPlaceOfSupply());

        List<FiledReturnLine> b2cs = lines(parsed, "B2CS");
        assertEquals(2, b2cs.size());
        assertEquals(sum(b2cs, FiledReturnLine::getTaxable), bd("3000"));
        assertEquals(sum(b2cs, FiledReturnLine::getIgst), bd("360"));
        assertEquals(sum(b2cs, FiledReturnLine::getCgst), bd("90"));
        assertEquals(sum(b2cs, FiledReturnLine::getSgst), bd("90"));
        assertTrue(b2cs.stream().anyMatch(l -> "INTRA".equals(l.getSupplyType())));
        assertTrue(b2cs.stream().anyMatch(l -> "INTER".equals(l.getSupplyType())));

        List<FiledReturnLine> exp = lines(parsed, "EXP");
        assertEquals(1, exp.size());
        assertEquals(sum(exp, FiledReturnLine::getTaxable), bd("50000"));
        assertEquals("WPAY", exp.get(0).getInvoiceType());

        List<FiledReturnLine> nil = lines(parsed, "NIL");
        assertEquals(1, nil.size());
        assertEquals(sum(nil, FiledReturnLine::getTaxable), bd("5000"));

        List<FiledReturnLine> cdnr = lines(parsed, "CDNR");
        assertEquals(1, cdnr.size());
        assertEquals(sum(cdnr, FiledReturnLine::getTaxable), bd("1000"));
        assertEquals(sum(cdnr, FiledReturnLine::getCgst), bd("90"));
        assertEquals("33BBBBB1111B1ZU", cdnr.get(0).getCounterpartyGstin());
        assertEquals("CN", cdnr.get(0).getDocType());
        assertEquals("CN-001", cdnr.get(0).getDocNumber());

        List<FiledReturnLine> cdnur = lines(parsed, "CDNUR");
        assertEquals(1, cdnur.size());
        assertEquals(sum(cdnur, FiledReturnLine::getTaxable), bd("500"));
        assertEquals(sum(cdnur, FiledReturnLine::getIgst), bd("60"));

        List<FiledReturnLine> at = lines(parsed, "AT");
        assertEquals(1, at.size());
        assertEquals(sum(at, FiledReturnLine::getTaxable), bd("10000"));
        assertEquals(sum(at, FiledReturnLine::getCgst), bd("900"));
        assertEquals(sum(at, FiledReturnLine::getSgst), bd("900"));

        List<FiledReturnLine> txpd = lines(parsed, "TXPD");
        assertEquals(1, txpd.size());
        assertEquals(sum(txpd, FiledReturnLine::getTaxable), bd("2000"));
        assertEquals(sum(txpd, FiledReturnLine::getIgst), bd("360"));

        List<FiledReturnLine> hsn = lines(parsed, "HSN");
        assertEquals(2, hsn.size());
        assertEquals(1, hsn.stream().filter(l -> "B2B".equals(l.getHsnCategory())).count());
        assertEquals(1, hsn.stream().filter(l -> "B2C".equals(l.getHsnCategory())).count());
        assertEquals(sum(hsn, FiledReturnLine::getTaxable), bd("18000"));
        assertEquals(sum(hsn, FiledReturnLine::getIgst), bd("2700"));
        assertEquals(sum(hsn, FiledReturnLine::getCgst), bd("270"));

        List<FiledReturnLine> docs = lines(parsed, "DOC");
        assertEquals(2, docs.size());
        FiledReturnLine invoiceSeries = docs.stream()
                .filter(l -> "1".equals(l.getDocNumber())).findFirst().orElseThrow();
        assertEquals(0, invoiceSeries.getInvoiceValue().compareTo(bd("50")));
        assertEquals(0, invoiceSeries.getTaxable().compareTo(bd("2")));
        assertEquals("1-50", invoiceSeries.getLabel());
        FiledReturnLine creditNoteSeries = docs.stream()
                .filter(l -> "5".equals(l.getDocNumber())).findFirst().orElseThrow();
        assertEquals(0, creditNoteSeries.getInvoiceValue().compareTo(bd("5")));
        assertEquals(0, creditNoteSeries.getTaxable().compareTo(bd("0")));
    }

    private Gstr1Parsed writeAndParse(Workflow workflow, List<PreparedReturnRow> rows) {
        List<PortalFilePart> parts = allTableParts(workflow, rows);
        Gstr1Parsed parsed = null;
        List<FiledReturnLine> all = new ArrayList<>();
        for (PortalFilePart part : parts) {
            Gstr1Parsed one = parser.parse(part.bytes());
            all.addAll(one.lines());
            parsed = one;
        }
        return new Gstr1Parsed(parsed.gstin(), parsed.filingPeriod(), all, parsed.warnings());
    }

    private List<PortalFilePart> allTableParts(Workflow workflow, List<PreparedReturnRow> rows) {
        return new Gstr1JsonWriter().write(prepared(workflow.getId()), rows,
                workflow, ORG_GSTIN, profile(), WriterOptions.defaults());
    }

    private List<FiledReturnLine> lines(Gstr1Parsed parsed, String tableCode) {
        return parsed.lines().stream()
                .filter(l -> tableCode.equals(l.getTableCode()))
                .toList();
    }

    private static BigDecimal sum(List<FiledReturnLine> lines, Function<FiledReturnLine, BigDecimal> get) {
        return lines.stream().map(l -> get.apply(l) == null ? BigDecimal.ZERO : get.apply(l))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    // ---------- builders ----------

    private static Workflow workflow() {
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

    private static FilerProfile profile() {
        FilerProfile p = new FilerProfile();
        p.setId(UUID.fromString("33333333-3333-3333-3333-333333333333"));
        p.setOrganizationId(UUID.fromString("22222222-2222-2222-2222-222222222222"));
        p.setFrequency(FilerFrequency.MONTHLY);
        return p;
    }

    private static PreparedReturn prepared(UUID workflowId) {
        PreparedReturn prepared = new PreparedReturn();
        prepared.setId(UUID.fromString("44444444-4444-4444-4444-444444444444"));
        prepared.setWorkflowId(workflowId);
        prepared.setReturnKind("GSTR1");
        return prepared;
    }

    private static PreparedReturnRow row(String tableCode, String rowKey, Map<String, Object> payload) {
        PreparedReturnRow row = new PreparedReturnRow();
        row.setId(UUID.randomUUID());
        row.setPreparedReturnId(UUID.fromString("44444444-4444-4444-4444-444444444444"));
        row.setTableCode(tableCode);
        row.setRowKey(rowKey);
        try {
            row.setPayload(MAPPER.writeValueAsString(payload));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        row.setAmendment(false);
        return row;
    }

    private static Map<String, Object> b2bIntraPayload() {
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

    private static Map<String, Object> b2bInterPayload() {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("gstin", "27CCCCC2222C1Z2");
        p.put("invoiceNo", "INV-002");
        p.put("invoiceDate", "2025-04-16");
        p.put("pos", "27");
        p.put("rate", new BigDecimal("18"));
        p.put("taxableValue", new BigDecimal("5000"));
        p.put("igst", new BigDecimal("900"));
        p.put("cgst", BigDecimal.ZERO);
        p.put("sgst", BigDecimal.ZERO);
        p.put("cess", BigDecimal.ZERO);
        p.put("invoiceValue", new BigDecimal("5900"));
        return p;
    }

    private static Map<String, Object> b2clPayload() {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("invoiceNo", "INV-B2CL-1");
        p.put("invoiceDate", "2025-04-17");
        p.put("pos", "27");
        p.put("rate", new BigDecimal("18"));
        p.put("taxableValue", new BigDecimal("2000"));
        p.put("igst", new BigDecimal("360"));
        p.put("cgst", BigDecimal.ZERO);
        p.put("sgst", BigDecimal.ZERO);
        p.put("cess", BigDecimal.ZERO);
        p.put("invoiceValue", new BigDecimal("2360"));
        return p;
    }

    private static Map<String, Object> b2csIntraPayload() {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("pos", "33");
        p.put("rate", new BigDecimal("18"));
        p.put("taxableValue", new BigDecimal("1000"));
        p.put("igst", BigDecimal.ZERO);
        p.put("cgst", new BigDecimal("90"));
        p.put("sgst", new BigDecimal("90"));
        p.put("cess", BigDecimal.ZERO);
        return p;
    }

    private static Map<String, Object> b2csInterPayload() {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("pos", "27");
        p.put("rate", new BigDecimal("18"));
        p.put("taxableValue", new BigDecimal("2000"));
        p.put("igst", new BigDecimal("360"));
        p.put("cgst", BigDecimal.ZERO);
        p.put("sgst", BigDecimal.ZERO);
        p.put("cess", BigDecimal.ZERO);
        return p;
    }

    private static Map<String, Object> expPayload() {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("invoiceNo", "EXP-001");
        p.put("invoiceDate", "2025-04-18");
        p.put("exportType", "WP");
        p.put("country", "US");
        p.put("rate", BigDecimal.ZERO);
        p.put("taxableValue", new BigDecimal("50000"));
        p.put("igst", BigDecimal.ZERO);
        p.put("cgst", BigDecimal.ZERO);
        p.put("sgst", BigDecimal.ZERO);
        p.put("cess", BigDecimal.ZERO);
        p.put("invoiceValue", new BigDecimal("50000"));
        return p;
    }

    private static Map<String, Object> nilPayload() {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("pos", "33");
        p.put("taxableValue", new BigDecimal("5000"));
        return p;
    }

    private static Map<String, Object> cdnrPayload() {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("noteType", "Credit Note");
        p.put("gstin", "33BBBBB1111B1ZU");
        p.put("noteNo", "CN-001");
        p.put("noteDate", "2025-04-20");
        p.put("pos", "33");
        p.put("rate", new BigDecimal("18"));
        p.put("taxableValue", new BigDecimal("-1000"));
        p.put("igst", BigDecimal.ZERO);
        p.put("cgst", new BigDecimal("-90"));
        p.put("sgst", new BigDecimal("-90"));
        p.put("cess", BigDecimal.ZERO);
        p.put("invoiceValue", new BigDecimal("-1180"));
        return p;
    }

    private static Map<String, Object> cdnurPayload() {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("noteType", "Debit Note");
        p.put("type", "B2CL");
        p.put("pos", "27");
        p.put("noteNo", "DN-UR-001");
        p.put("noteDate", "2025-04-21");
        p.put("rate", new BigDecimal("12"));
        p.put("taxableValue", new BigDecimal("-500"));
        p.put("igst", new BigDecimal("-60"));
        p.put("cgst", BigDecimal.ZERO);
        p.put("sgst", BigDecimal.ZERO);
        p.put("cess", BigDecimal.ZERO);
        return p;
    }

    private static Map<String, Object> atPayload() {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("voucherNo", "RCPT-001");
        p.put("voucherDate", "2025-04-22");
        p.put("pos", "33");
        p.put("rate", new BigDecimal("18"));
        p.put("advanceAmount", new BigDecimal("10000"));
        p.put("igst", BigDecimal.ZERO);
        p.put("cgst", new BigDecimal("900"));
        p.put("sgst", new BigDecimal("900"));
        p.put("cess", BigDecimal.ZERO);
        return p;
    }

    private static Map<String, Object> txpdPayload() {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("voucherNo", "RCPT-002");
        p.put("voucherDate", "2025-04-23");
        p.put("pos", "27");
        p.put("rate", new BigDecimal("18"));
        p.put("advanceAmount", new BigDecimal("2000"));
        p.put("igst", new BigDecimal("360"));
        p.put("cgst", BigDecimal.ZERO);
        p.put("sgst", BigDecimal.ZERO);
        p.put("cess", BigDecimal.ZERO);
        return p;
    }

    private static Map<String, Object> hsnB2bPayload() {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("hsn", "1234");
        p.put("uqc", "PCS");
        p.put("quantity", new BigDecimal("100"));
        p.put("rate", new BigDecimal("18"));
        p.put("supplyType", "B2B");
        p.put("taxableValue", new BigDecimal("15000"));
        p.put("igst", new BigDecimal("2700"));
        p.put("cgst", BigDecimal.ZERO);
        p.put("sgst", BigDecimal.ZERO);
        p.put("cess", BigDecimal.ZERO);
        p.put("invoiceValue", new BigDecimal("17700"));
        return p;
    }

    private static Map<String, Object> hsnB2cPayload() {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("hsn", "5678");
        p.put("uqc", "PCS");
        p.put("quantity", new BigDecimal("30"));
        p.put("rate", new BigDecimal("18"));
        p.put("supplyType", "B2C");
        p.put("taxableValue", new BigDecimal("3000"));
        p.put("igst", BigDecimal.ZERO);
        p.put("cgst", new BigDecimal("270"));
        p.put("sgst", new BigDecimal("270"));
        p.put("cess", BigDecimal.ZERO);
        p.put("invoiceValue", new BigDecimal("3540"));
        return p;
    }

    private static Map<String, Object> invoiceSeriesPayload() {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("voucherType", "Tax Invoice");
        p.put("from", "1");
        p.put("to", "50");
        p.put("total", new BigDecimal("50"));
        p.put("cancelled", new BigDecimal("2"));
        return p;
    }

    private static Map<String, Object> creditNoteSeriesPayload() {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("voucherType", "Credit Note");
        p.put("from", "1");
        p.put("to", "5");
        p.put("total", new BigDecimal("5"));
        p.put("cancelled", BigDecimal.ZERO);
        return p;
    }
}
