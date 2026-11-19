package com.arktech.superaccountant.filedreturns.parse;

import com.arktech.superaccountant.filedreturns.FiledReturnLine;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class Gstr1JsonParser {

    public static final String PARSER_VERSION = "gstr1-json-1";

    private static final DateTimeFormatter DD_MM_YYYY = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final DateTimeFormatter MM_YYYY = DateTimeFormatter.ofPattern("MMyyyy");

    private final ObjectMapper mapper = new ObjectMapper();

    public Gstr1Parsed parse(byte[] json) {
        JsonNode root;
        try {
            root = mapper.readTree(json);
        } catch (Exception e) {
            throw new FiledReturnParseException("Not a GSTR-1 JSON: body is not JSON");
        }
        if (root == null || !root.isObject()) {
            throw new FiledReturnParseException("Not a GSTR-1 JSON: body is not a JSON object");
        }
        if (!root.hasNonNull("gstin")) {
            // Root wrapper: descend once into a single object-valued field.
            JsonNode wrapper = null;
            int objectFields = 0;
            Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> f = fields.next();
                if (f.getValue().isObject()) {
                    objectFields++;
                    wrapper = f.getValue();
                }
            }
            if (wrapper != null && objectFields == 1 && wrapper.hasNonNull("gstin")) {
                root = wrapper;
            }
        }

        List<String> warnings = new ArrayList<>();
        if (!root.hasNonNull("gstin") || !root.hasNonNull("fp")) {
            throw new FiledReturnParseException("Not a GSTR-1 JSON: missing gstin or fp");
        }
        String gstin = root.get("gstin").asText();
        String filingPeriod = period(root.get("fp").asText());

        List<FiledReturnLine> lines = new ArrayList<>();
        Iterator<Map.Entry<String, JsonNode>> sections = root.fields();
        while (sections.hasNext()) {
            Map.Entry<String, JsonNode> section = sections.next();
            String key = section.getKey();
            switch (key) {
                case "gstin", "fp", "version", "hash", "file_hash", "checksum" -> { }
                case "b2b" -> parseInvoiceSection(section.getValue(), "B2B", false, false, gstin, lines);
                case "b2ba" -> parseInvoiceSection(section.getValue(), "B2BA", true, false, gstin, lines);
                case "cdnr" -> parseNoteSection(section.getValue(), "CDNR", false, gstin, lines);
                case "cdnra" -> parseNoteSection(section.getValue(), "CDNRA", true, gstin, lines);
                case "b2cl" -> parseB2clSection(section.getValue(), "B2CL", false, lines);
                case "b2cla" -> parseB2clSection(section.getValue(), "B2CLA", true, lines);
                case "exp" -> parseExpSection(section.getValue(), "EXP", false, lines);
                case "expa" -> parseExpSection(section.getValue(), "EXPA", true, lines);
                case "cdnur" -> parseCdnurSection(section.getValue(), "CDNUR", false, lines);
                case "cdnura" -> parseCdnurSection(section.getValue(), "CDNURA", true, lines);
                case "b2cs" -> parseB2csSection(section.getValue(), "B2CS", null, lines);
                case "b2csa" -> parseB2csaSection(section.getValue(), "B2CSA", lines);
                case "nil" -> parseNilSection(section.getValue(), lines);
                case "at" -> parseAtSection(section.getValue(), "AT", lines);
                case "atadj" -> parseAtSection(section.getValue(), "ATADJ", lines);
                case "txpd" -> parseAtSection(section.getValue(), "TXPD", lines);
                case "txpda" -> parseAtSection(section.getValue(), "TXPDA", lines);
                case "hsn" -> parseHsnWrapper(section.getValue(), lines);
                case "hsn_b2b" -> parseHsnSection(section.getValue(), "B2B", lines);
                case "hsn_b2c" -> parseHsnSection(section.getValue(), "B2C", lines);
                case "doc_issue" -> parseDocIssueSection(section.getValue(), lines);
                default -> warnings.add("Ignored section " + key);
            }
        }
        int lineNo = 1;
        for (FiledReturnLine line : lines) {
            line.setLineNo(lineNo++);
            normaliseAmounts(line);
        }
        return new Gstr1Parsed(gstin, filingPeriod, lines, warnings);
    }

    private String period(String fp) {
        try {
            return YearMonth.parse(fp, MM_YYYY).format(DateTimeFormatter.ofPattern("yyyy-MM"));
        } catch (Exception e) {
            throw new FiledReturnParseException("Not a GSTR-1 JSON: unreadable fp " + fp);
        }
    }

    private String parseDocDate(String raw) {
        try {
            return LocalDate.parse(raw, DD_MM_YYYY).toString();
        } catch (Exception e) {
            return null;
        }
    }

    private void parseInvoiceSection(JsonNode section, String tableCode, boolean amended,
                                     boolean dummy, String orgGstin, List<FiledReturnLine> lines) {
        for (JsonNode party : section) {
            String ctin = text(party, "ctin");
            JsonNode invoices = party.get("inv");
            if (invoices == null) {
                continue;
            }
            for (JsonNode inv : invoices) {
                String pos = text(inv, "pos");
                String invoiceType = text(inv, "inv_typ");
                String docNumber = text(inv, "inum");
                String docDate = parseDocDate(text(inv, "idt"));
                BigDecimal invoiceValue = decimal(inv, "val");
                String originalDocNumber = amended ? text(inv, "oinum") : null;
                String originalDocDate = amended ? parseDocDate(text(inv, "oidt")) : null;
                addRateItems(inv, lines, tableCode, "INV", ctin, docNumber, docDate, pos,
                        invoiceType, invoiceValue, originalDocNumber, originalDocDate, orgGstin);
            }
        }
    }

    private void parseNoteSection(JsonNode section, String tableCode, boolean amended,
                                  String orgGstin, List<FiledReturnLine> lines) {
        for (JsonNode party : section) {
            String ctin = text(party, "ctin");
            JsonNode notes = party.get("nt");
            if (notes == null) {
                continue;
            }
            for (JsonNode nt : notes) {
                String ntty = text(nt, "ntty");
                String docType = "C".equalsIgnoreCase(ntty) ? "CN" : "D".equalsIgnoreCase(ntty) ? "DN" : ntty;
                String pos = text(nt, "pos");
                String docNumber = text(nt, "nt_num");
                String docDate = parseDocDate(text(nt, "nt_dt"));
                BigDecimal invoiceValue = decimal(nt, "val");
                String originalDocNumber = amended ? text(nt, "ont_num") : null;
                String originalDocDate = amended ? parseDocDate(text(nt, "ont_dt")) : null;
                addRateItems(nt, lines, tableCode, docType, ctin, docNumber, docDate, pos,
                        null, invoiceValue, originalDocNumber, originalDocDate, orgGstin);
            }
        }
    }

    private void parseB2clSection(JsonNode section, String tableCode, boolean amended, List<FiledReturnLine> lines) {
        for (JsonNode posNode : section) {
            String pos = text(posNode, "pos");
            JsonNode invoices = posNode.get("inv");
            if (invoices == null) {
                continue;
            }
            for (JsonNode inv : invoices) {
                String docNumber = text(inv, "inum");
                String docDate = parseDocDate(text(inv, "idt"));
                BigDecimal invoiceValue = decimal(inv, "val");
                String originalDocNumber = amended ? text(inv, "oinum") : null;
                String originalDocDate = amended ? parseDocDate(text(inv, "oidt")) : null;
                addRateItems(inv, lines, tableCode, "INV", null, docNumber, docDate, pos,
                        null, invoiceValue, originalDocNumber, originalDocDate, null);
            }
        }
    }

    private void parseExpSection(JsonNode section, String tableCode, boolean amended, List<FiledReturnLine> lines) {
        for (JsonNode exp : section) {
            String invoiceType = text(exp, "exp_typ");
            JsonNode invoices = exp.get("inv");
            if (invoices == null) {
                continue;
            }
            for (JsonNode inv : invoices) {
                String docNumber = text(inv, "inum");
                String docDate = parseDocDate(text(inv, "idt"));
                BigDecimal invoiceValue = decimal(inv, "val");
                String originalDocNumber = amended ? text(inv, "oinum") : null;
                String originalDocDate = amended ? parseDocDate(text(inv, "oidt")) : null;
                JsonNode items = inv.get("itms");
                if (items == null || !items.isArray()) {
                    continue;
                }
                for (JsonNode item : items) {
                    FiledReturnLine line = baseLine(tableCode);
                    line.setDocType("INV");
                    line.setDocNumber(docNumber);
                    line.setDocDate(docDate == null ? null : LocalDate.parse(docDate));
                    line.setInvoiceType(invoiceType);
                    line.setInvoiceValue(invoiceValue);
                    line.setOriginalDocNumber(originalDocNumber);
                    line.setOriginalDocDate(originalDocDate == null ? null : LocalDate.parse(originalDocDate));
                    JsonNode det = item.has("itm_det") ? item.get("itm_det") : item;
                    applyAmounts(line, det);
                    lines.add(line);
                }
            }
        }
    }

    private void parseCdnurSection(JsonNode section, String tableCode, boolean amended, List<FiledReturnLine> lines) {
        for (JsonNode nt : section) {
            FiledReturnLine line = baseLine(tableCode);
            line.setInvoiceType(text(nt, "typ"));
            line.setDocType("C".equalsIgnoreCase(text(nt, "ntty")) ? "CN"
                    : "D".equalsIgnoreCase(text(nt, "ntty")) ? "DN" : text(nt, "ntty"));
            line.setDocNumber(text(nt, "nt_num"));
            String docDate = parseDocDate(text(nt, "nt_dt"));
            line.setDocDate(docDate == null ? null : LocalDate.parse(docDate));
            line.setInvoiceValue(decimal(nt, "val"));
            if (amended) {
                line.setOriginalDocNumber(text(nt, "ont_num"));
                String originalDocDate = parseDocDate(text(nt, "ont_dt"));
                line.setOriginalDocDate(originalDocDate == null ? null : LocalDate.parse(originalDocDate));
            }
            JsonNode items = nt.get("itms");
            if (items == null || !items.isArray() || items.isEmpty()) {
                applyAmounts(line, nt);
                lines.add(line);
            } else {
                for (JsonNode item : items) {
                    FiledReturnLine copy = copyWithoutIds(line);
                    JsonNode det = item.has("itm_det") ? item.get("itm_det") : item;
                    applyAmounts(copy, det);
                    lines.add(copy);
                }
            }
        }
    }

    private void parseB2csSection(JsonNode section, String tableCode, String dummy, List<FiledReturnLine> lines) {
        for (JsonNode row : section) {
            FiledReturnLine line = baseLine(tableCode);
            line.setSupplyType("INTER".equalsIgnoreCase(text(row, "sply_ty")) ? "INTER" : "INTRA");
            line.setPlaceOfSupply(text(row, "pos"));
            applyAmounts(line, row);
            lines.add(line);
        }
    }

    private void parseB2csaSection(JsonNode section, String tableCode, List<FiledReturnLine> lines) {
        for (JsonNode row : section) {
            String originalPeriod = period(text(row, "omon"));
            String supplyType = "INTER".equalsIgnoreCase(text(row, "sply_ty")) ? "INTER" : "INTRA";
            String pos = text(row, "pos");
            JsonNode items = row.get("itms");
            if (items == null || !items.isArray()) {
                continue;
            }
            for (JsonNode item : items) {
                FiledReturnLine line = baseLine(tableCode);
                line.setSupplyType(supplyType);
                line.setPlaceOfSupply(pos);
                line.setOriginalPeriod(originalPeriod);
                applyAmounts(line, item);
                lines.add(line);
            }
        }
    }

    private void parseNilSection(JsonNode section, List<FiledReturnLine> lines) {
        JsonNode inv = section.get("inv");
        if (inv == null || !inv.isArray()) {
            return;
        }
        for (JsonNode row : inv) {
            FiledReturnLine line = baseLine("NIL");
            line.setSupplyType("INTER".equalsIgnoreCase(text(row, "sply_ty")) ? "INTER" : "INTRA");
            line.setLabel(text(row, "sply_ty"));
            line.setTaxable(nvl(decimal(row, "nil_amt")).add(nvl(decimal(row, "expt_amt")))
                    .add(nvl(decimal(row, "ngsup_amt"))));
            lines.add(line);
        }
    }

    private void parseAtSection(JsonNode section, String tableCode, List<FiledReturnLine> lines) {
        for (JsonNode row : section) {
            String pos = text(row, "pos");
            // portal V3.2.4 shape carries the items in `itms` with the advance amount
            // in `ad_amt`; the older offline-tool shape used a `txpd` array
            JsonNode items = row.get("itms");
            boolean portalShape = items != null && items.isArray();
            if (!portalShape) {
                items = row.get("txpd");
                if (items == null || !items.isArray()) {
                    continue;
                }
            }
            for (JsonNode item : items) {
                FiledReturnLine line = baseLine(tableCode);
                line.setSupplyType("INTER".equalsIgnoreCase(text(row, "sply_ty")) ? "INTER" : "INTRA");
                line.setPlaceOfSupply(pos);
                applyAmounts(line, item);
                if (portalShape) {
                    line.setTaxable(nvl(decimal(item, "ad_amt")));
                }
                BigDecimal atadj = decimal(item, "atadj");
                if (atadj != null && tableCode.equals("AT")) {
                    line.setIgst(nvl(line.getIgst()).subtract(atadj));
                }
                lines.add(line);
            }
        }
    }

    /**
     * Portal V3.2.4 emits {@code hsn: {hsn_b2b: [...], hsn_b2c: [...]}}; older exports
     * carry a flat array (optionally under {@code data}).
     */
    private void parseHsnWrapper(JsonNode section, List<FiledReturnLine> lines) {
        JsonNode b2b = section.get("hsn_b2b");
        JsonNode b2c = section.get("hsn_b2c");
        if ((b2b != null && b2b.isArray()) || (b2c != null && b2c.isArray())) {
            if (b2b != null && b2b.isArray()) {
                parseHsnSection(b2b, "B2B", lines);
            }
            if (b2c != null && b2c.isArray()) {
                parseHsnSection(b2c, "B2C", lines);
            }
            return;
        }
        parseHsnSection(section, null, lines);
    }

    private void parseHsnSection(JsonNode section, String category, List<FiledReturnLine> lines) {
        JsonNode data = section.has("data") ? section.get("data") : section;
        if (data == null || !data.isArray()) {
            return;
        }
        for (JsonNode row : data) {
            FiledReturnLine line = baseLine("HSN");
            line.setHsnCategory(category);
            line.setLabel(text(row, "hsn_sc"));
            line.setTaxable(nvl(decimal(row, "txval")));
            line.setIgst(nvl(decimal(row, "iamt")));
            line.setCgst(nvl(decimal(row, "camt")));
            line.setSgst(nvl(decimal(row, "samt")));
            line.setCess(nvl(decimal(row, "csamt")));
            lines.add(line);
        }
    }

    private void parseDocIssueSection(JsonNode section, List<FiledReturnLine> lines) {
        JsonNode docDet = section.get("doc_det");
        if (docDet == null || !docDet.isArray()) {
            return;
        }
        for (JsonNode series : docDet) {
            JsonNode docs = series.get("docs");
            if (docs == null || !docs.isArray()) {
                continue;
            }
            for (JsonNode doc : docs) {
                FiledReturnLine line = baseLine("DOC");
                line.setDocNumber(text(series, "doc_num"));
                line.setLabel(text(doc, "from") + "-" + text(doc, "to"));
                BigDecimal issued = decimal(doc, "totnum");
                if (issued == null) issued = decimal(doc, "net_issue");
                if (issued == null) issued = decimal(doc, "net_num");
                line.setInvoiceValue(nvl(issued));
                line.setTaxable(nvl(decimal(doc, "cancel")));
                lines.add(line);
            }
        }
    }

    private void addRateItems(JsonNode invoice, List<FiledReturnLine> lines, String tableCode,
                              String docType, String counterpartyGstin, String docNumber, String docDate,
                              String pos, String invoiceType, BigDecimal invoiceValue,
                              String originalDocNumber, String originalDocDate, String orgGstin) {
        JsonNode items = invoice.get("itms");
        if (items == null || !items.isArray()) {
            return;
        }
        for (JsonNode item : items) {
            JsonNode det = item.has("itm_det") ? item.get("itm_det") : item;
            FiledReturnLine line = baseLine(tableCode);
            line.setDocType(docType);
            line.setCounterpartyGstin(counterpartyGstin);
            line.setDocNumber(docNumber);
            line.setDocDate(docDate == null ? null : LocalDate.parse(docDate));
            line.setPlaceOfSupply(pos);
            line.setInvoiceType(invoiceType);
            line.setInvoiceValue(invoiceValue);
            line.setOriginalDocNumber(originalDocNumber);
            line.setOriginalDocDate(originalDocDate == null ? null : LocalDate.parse(originalDocDate));
            applyAmounts(line, det);
            line.setSupplyType(supplyType(pos, line, orgGstin));
            lines.add(line);
        }
    }

    private String supplyType(String pos, FiledReturnLine line, String orgGstin) {
        if (pos != null && !pos.isBlank() && orgGstin != null && orgGstin.length() >= 2) {
            return pos.equals(orgGstin.substring(0, 2)) ? "INTRA" : "INTER";
        }
        return nvl(line.getIgst()).signum() > 0 ? "INTER" : "INTRA";
    }

    private void applyAmounts(FiledReturnLine line, JsonNode det) {
        line.setRate(decimal(det, "rt"));
        line.setTaxable(nvl(decimal(det, "txval")));
        line.setIgst(nvl(decimal(det, "iamt")));
        line.setCgst(nvl(decimal(det, "camt")));
        line.setSgst(nvl(decimal(det, "samt")));
        line.setCess(nvl(decimal(det, "csamt")));
    }

    private void normaliseAmounts(FiledReturnLine line) {        line.setTaxable(nvl(line.getTaxable()));
        line.setIgst(nvl(line.getIgst()));
        line.setCgst(nvl(line.getCgst()));
        line.setSgst(nvl(line.getSgst()));
        line.setCess(nvl(line.getCess()));
    }

    private FiledReturnLine baseLine(String tableCode) {
        FiledReturnLine line = new FiledReturnLine();
        line.setTableCode(tableCode);
        line.setTaxable(BigDecimal.ZERO);
        line.setIgst(BigDecimal.ZERO);
        line.setCgst(BigDecimal.ZERO);
        line.setSgst(BigDecimal.ZERO);
        line.setCess(BigDecimal.ZERO);
        return line;
    }

    private FiledReturnLine copyWithoutIds(FiledReturnLine source) {
        FiledReturnLine line = new FiledReturnLine();
        line.setTableCode(source.getTableCode());
        line.setDocType(source.getDocType());
        line.setCounterpartyGstin(source.getCounterpartyGstin());
        line.setDocNumber(source.getDocNumber());
        line.setDocDate(source.getDocDate());
        line.setOriginalDocNumber(source.getOriginalDocNumber());
        line.setOriginalDocDate(source.getOriginalDocDate());
        line.setOriginalPeriod(source.getOriginalPeriod());
        line.setSupplyType(source.getSupplyType());
        line.setPlaceOfSupply(source.getPlaceOfSupply());
        line.setRate(source.getRate());
        line.setInvoiceType(source.getInvoiceType());
        line.setLabel(source.getLabel());
        line.setHsnCategory(source.getHsnCategory());
        line.setInvoiceValue(source.getInvoiceValue());
        line.setTaxable(source.getTaxable());
        line.setIgst(source.getIgst());
        line.setCgst(source.getCgst());
        line.setSgst(source.getSgst());
        line.setCess(source.getCess());
        return line;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private BigDecimal decimal(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.decimalValue();
    }

    private BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
