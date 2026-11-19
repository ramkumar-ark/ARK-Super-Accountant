package com.arktech.superaccountant.gstr1.json;

import com.arktech.superaccountant.gstr1.models.PreparedReturn;
import com.arktech.superaccountant.gstr1.models.PreparedReturnRow;
import com.arktech.superaccountant.workflow.models.FilerFrequency;
import com.arktech.superaccountant.workflow.models.FilerProfile;
import com.arktech.superaccountant.workflow.models.Workflow;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamWriteFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Builds the versioned GSTR-1 portal upload JSON ("Prepare Offline") from a prepared
 * return. Pure and deterministic: the same input always yields the same bytes, so a
 * download hash stays stable.
 *
 * <p>Key names, shapes and limits follow the GST Offline Tool V3.2.4 output (scout
 * report sa-gstr1-json-ref): version "GST3.2.4", hash "hash", txpd/txpda,
 * hsn.hsn_b2b/hsn_b2c, doc_issue.doc_det, export items flat, cdnur pos only for B2CL,
 * split at 19,000 items or 4.7 MB, empty sections omitted, amounts 2 dp numbers.</p>
 *
 * <p>Amendment rows (isAmendment) are not written yet: amendment sections are Ship 6
 * (plan T20). Amendment rows are skipped.</p>
 */
@Component
public class Gstr1JsonWriter {

    private static final DateTimeFormatter DD_MM_YYYY = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final DateTimeFormatter MM_YYYY = DateTimeFormatter.ofPattern("MMyyyy");
    // amounts print without exponent notation: 10000, never 1E+2
    private static final ObjectMapper MAPPER = new ObjectMapper(
            JsonFactory.builder().enable(StreamWriteFeature.WRITE_BIGDECIMAL_AS_PLAIN).build());
    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    private static final List<String> SECTION_ORDER = List.of(
            "b2b", "b2cl", "b2cs", "exp", "nil", "cdnr", "cdnur", "at", "txpd", "doc_issue", "hsn");

    public List<PortalFilePart> write(PreparedReturn prepared, List<PreparedReturnRow> rows,
                                      Workflow workflow, String gstin, FilerProfile profile,
                                      WriterOptions options) {
        String fp = filingPeriod(workflow, profile);
        Map<String, List<PreparedReturnRow>> byTable = new LinkedHashMap<>();
        for (PreparedReturnRow row : rows) {
            if (row.isAmendment()) {
                continue; // amendment sections are Ship 6 (T20)
            }
            byTable.computeIfAbsent(row.getTableCode(), k -> new ArrayList<>()).add(row);
        }

        List<Block> blocks = new ArrayList<>();
        boolean dropIrp = profile != null && profile.isEinvoiceEnabled() && !options.includeIrpTables();
        if (!dropIrp) {
            blocks.addAll(b2bBlocks(byTable.get("B2B"), gstin));
        }
        blocks.addAll(b2clBlocks(byTable.get("B2CL")));
        blocks.addAll(b2csBlocks(byTable.get("B2CS"), gstin));
        if (!dropIrp) {
            blocks.addAll(expBlocks(byTable.get("EXP")));
        }
        blocks.addAll(nilBlocks(byTable.get("NIL"), gstin));
        if (!dropIrp) {
            blocks.addAll(cdnrBlocks(byTable.get("CDNR"), gstin));
        }
        blocks.addAll(cdnurBlocks(byTable.get("CDNUR")));
        blocks.addAll(atBlocks(byTable.get("AT"), gstin, "at", true));
        blocks.addAll(atBlocks(byTable.get("TXPD"), gstin, "txpd", false));
        blocks.addAll(docIssueBlocks(byTable.get("DOCS")));
        blocks.addAll(hsnBlocks(byTable.get("HSN")));

        return pack(blocks, gstin, fp);
    }

    // ---------- filing period ----------

    static String filingPeriod(Workflow workflow, FilerProfile profile) {
        FilerFrequency frequency = profile == null ? FilerFrequency.MONTHLY : profile.getFrequency();
        YearMonth month = frequency == FilerFrequency.QRMP
                ? YearMonth.from(workflow.getPeriodEnd())
                : YearMonth.from(workflow.getPeriodStart());
        return month.format(MM_YYYY);
    }

    // ---------- section builders ----------

    private List<Block> b2bBlocks(List<PreparedReturnRow> rows, String orgGstin) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        return grouped("b2b", "ctin", "inv", rows,
                row -> {
                    Map<String, Object> p = row.payload();
                    ObjectNode inv = NODES.objectNode();
                    inv.put("inum", text(p, "invoiceNo"));
                    inv.put("idt", date(text(p, "invoiceDate")));
                    inv.put("val", amount(num(p, "invoiceValue")));
                    inv.put("pos", text(p, "pos"));
                    inv.put("rchrg", "N");
                    inv.put("inv_typ", "R");
                    inv.putArray("itms").add(item(num(p, "rate"), num(p, "taxableValue"),
                            tax(p, orgGstin, text(p, "pos")), num(p, "cess")));
                    return inv;
                },
                row -> text(row.payload(), "gstin"),
                null);
    }

    private List<Block> b2clBlocks(List<PreparedReturnRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        return grouped("b2cl", "pos", "inv", rows,
                row -> {
                    Map<String, Object> p = row.payload();
                    ObjectNode inv = NODES.objectNode();
                    inv.put("inum", text(p, "invoiceNo"));
                    inv.put("idt", date(text(p, "invoiceDate")));
                    inv.put("val", amount(num(p, "invoiceValue")));
                    inv.putArray("itms").add(item(num(p, "rate"), num(p, "taxableValue"),
                            new TaxAmounts(num(p, "igst"), null, null), num(p, "cess")));
                    return inv;
                },
                row -> text(row.payload(), "pos"),
                null);
    }

    private List<Block> b2csBlocks(List<PreparedReturnRow> rows, String orgGstin) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<Block> blocks = new ArrayList<>();
        for (PreparedReturnRow row : rows) {
            Map<String, Object> p = row.payload();
            ObjectNode entry = NODES.objectNode();
            String pos = text(p, "pos");
            entry.put("sply_ty", intraState(orgGstin, pos) ? "INTRA" : "INTER");
            entry.put("rt", amount(num(p, "rate")));
            entry.put("typ", "OE");
            entry.put("pos", pos);
            entry.put("txval", amount(num(p, "taxableValue")));
            putTaxKeys(entry, tax(p, orgGstin, pos));
            entry.put("csamt", amount(num(p, "cess")));
            blocks.add(Block.flat("b2cs", entry));
        }
        return blocks;
    }

    private List<Block> expBlocks(List<PreparedReturnRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        return grouped("exp", "exp_typ", "inv", rows,
                row -> {
                    Map<String, Object> p = row.payload();
                    ObjectNode inv = NODES.objectNode();
                    inv.put("inum", text(p, "invoiceNo"));
                    inv.put("idt", date(text(p, "invoiceDate")));
                    inv.put("val", amount(num(p, "invoiceValue")));
                    // export items are flat in the portal JSON: no num, no itm_det wrapper
                    ObjectNode item = inv.putArray("itms").addObject();
                    item.put("txval", amount(num(p, "taxableValue")));
                    item.put("rt", amount(num(p, "rate")));
                    item.put("iamt", amount(num(p, "igst")));
                    item.put("csamt", amount(num(p, "cess")));
                    return inv;
                },
                row -> "WP".equals(text(row.payload(), "exportType")) ? "WPAY" : "WOPAY",
                null);
    }

    private List<Block> nilBlocks(List<PreparedReturnRow> rows, String orgGstin) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        ObjectNode nil = NODES.objectNode();
        ArrayNode inv = nil.putArray("inv");
        for (PreparedReturnRow row : rows) {
            Map<String, Object> p = row.payload();
            ObjectNode entry = inv.addObject();
            entry.put("sply_ty", intraState(orgGstin, text(p, "pos")) ? "INTRB2C" : "INTERB2C");
            entry.put("nil_amt", amount(num(p, "taxableValue")));
        }
        return List.of(Block.object("nil", nil));
    }

    private List<Block> cdnrBlocks(List<PreparedReturnRow> rows, String orgGstin) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        return grouped("cdnr", "ctin", "nt", rows,
                row -> {
                    Map<String, Object> p = row.payload();
                    ObjectNode nt = NODES.objectNode();
                    nt.put("nt_num", text(p, "noteNo"));
                    nt.put("nt_dt", date(text(p, "noteDate")));
                    nt.put("ntty", noteTypeCode(text(p, "noteType")));
                    nt.put("val", amount(num(p, "invoiceValue").abs()));
                    nt.put("pos", text(p, "pos"));
                    nt.put("rchrg", "N");
                    nt.put("inv_typ", "R");
                    nt.putArray("itms").add(item(num(p, "rate"), num(p, "taxableValue").abs(),
                            tax(p, orgGstin, text(p, "pos")).map(BigDecimal::abs), num(p, "cess").abs()));
                    return nt;
                },
                row -> text(row.payload(), "gstin"),
                null);
    }

    private List<Block> cdnurBlocks(List<PreparedReturnRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<Block> blocks = new ArrayList<>();
        for (PreparedReturnRow row : rows) {
            Map<String, Object> p = row.payload();
            ObjectNode entry = NODES.objectNode();
            entry.put("nt_num", text(p, "noteNo"));
            entry.put("nt_dt", date(text(p, "noteDate")));
            entry.put("ntty", noteTypeCode(text(p, "noteType")));
            entry.put("val", amount(num(p, "invoiceValue").abs()));
            // portal CDNUR type values are B2CL | EXPWP | EXPWOP; the prepared row's
            // "B2CS" bucket (intra-state unregistered note) has no dedicated portal
            // value, so it is written as B2CL with pos. UNVERIFIED: portal behaviour
            // for below-threshold CDNUR rows.
            entry.put("typ", "B2CL");
            entry.put("pos", text(p, "pos"));
            entry.putArray("itms").add(item(num(p, "rate"), num(p, "taxableValue").abs(),
                    new TaxAmounts(num(p, "igst"), null, null).map(BigDecimal::abs), num(p, "cess").abs()));
            blocks.add(Block.flat("cdnur", entry));
        }
        return blocks;
    }

    private List<Block> atBlocks(List<PreparedReturnRow> rows, String orgGstin,
                                 String section, boolean keepZeroCess) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        return grouped(section, "pos", "itms", rows,
                row -> {
                    Map<String, Object> p = row.payload();
                    ObjectNode item = NODES.objectNode();
                    item.put("rt", amount(num(p, "rate")));
                    item.put("ad_amt", amount(num(p, "advanceAmount")));
                    putTaxKeys(item, tax(p, orgGstin, text(p, "pos")));
                    if (keepZeroCess || num(p, "cess").signum() != 0) {
                        item.put("csamt", amount(num(p, "cess")));
                    }
                    return item;
                },
                row -> text(row.payload(), "pos"),
                (key, template) -> template.put("sply_ty", intraState(orgGstin, key) ? "INTRA" : "INTER"));
    }

    private List<Block> docIssueBlocks(List<PreparedReturnRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        Map<Integer, ArrayNode> docLists = new LinkedHashMap<>();
        for (PreparedReturnRow row : rows) {
            Map<String, Object> p = row.payload();
            int docNum = docNumber(text(p, "voucherType"));
            ArrayNode docs = docLists.computeIfAbsent(docNum, k -> NODES.arrayNode());
            ObjectNode doc = docs.addObject();
            doc.put("num", docs.size());
            doc.put("from", text(p, "from"));
            doc.put("to", text(p, "to"));
            int total = num(p, "total").intValue();
            int cancelled = num(p, "cancelled").intValue();
            doc.put("totnum", total);
            doc.put("cancel", cancelled);
            doc.put("net_issue", total - cancelled);
        }
        ObjectNode docIssue = NODES.objectNode();
        ArrayNode docDet = docIssue.putArray("doc_det");
        for (Map.Entry<Integer, ArrayNode> e : docLists.entrySet()) {
            ObjectNode det = docDet.addObject();
            det.put("doc_num", e.getKey());
            det.put("doc_typ", docTypeName(e.getKey()));
            det.set("docs", e.getValue());
        }
        return List.of(Block.object("doc_issue", docIssue));
    }

    private List<Block> hsnBlocks(List<PreparedReturnRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        ObjectNode hsn = NODES.objectNode();
        ArrayNode b2b = hsn.putArray("hsn_b2b");
        ArrayNode b2c = hsn.putArray("hsn_b2c");
        for (PreparedReturnRow row : rows) {
            Map<String, Object> p = row.payload();
            ArrayNode target = "B2B".equals(text(p, "supplyType")) ? b2b : b2c;
            ObjectNode h = target.addObject();
            h.put("num", target.size());
            h.put("hsn_sc", text(p, "hsn"));
            // no description source in the prepared rows: both keys carry the HSN code
            h.put("user_desc", text(p, "hsn"));
            h.put("desc", text(p, "hsn"));
            h.put("uqc", text(p, "uqc"));
            h.put("qty", amount(num(p, "quantity")));
            h.put("rt", amount(num(p, "rate")));
            h.put("txval", amount(num(p, "taxableValue")));
            // the tool writes all four tax keys on every HSN row
            h.put("iamt", amount(num(p, "igst")));
            h.put("samt", amount(num(p, "sgst")));
            h.put("camt", amount(num(p, "cgst")));
            h.put("csamt", amount(num(p, "cess")));
        }
        return List.of(Block.object("hsn", hsn));
    }

    // ---------- shared helpers ----------

    private record TaxAmounts(BigDecimal igst, BigDecimal cgst, BigDecimal samt) {
    TaxAmounts map(Function<BigDecimal, BigDecimal> f) {
        return new TaxAmounts(igst == null ? null : f.apply(igst),
                cgst == null ? null : f.apply(cgst),
                samt == null ? null : f.apply(samt));
    }
    }

    private List<Block> grouped(String section, String keyField, String childField,
                                List<PreparedReturnRow> rows, Function<PreparedReturnRow, ObjectNode> mapper,
                                Function<PreparedReturnRow, String> keyFn,
                                BiConsumer<String, ObjectNode> templateFiller) {
        List<Block> blocks = new ArrayList<>();
        Map<String, List<ObjectNode>> pending = new LinkedHashMap<>();
        Map<String, int[]> sizes = new LinkedHashMap<>(); // items, bytes
        for (PreparedReturnRow row : rows) {
            String key = keyFn.apply(row);
            if (key == null) {
                continue;
            }
            ObjectNode entry = mapper.apply(row);
            List<ObjectNode> entries = pending.computeIfAbsent(key, k -> new ArrayList<>());
            int[] size = sizes.computeIfAbsent(key, k -> new int[2]);
            entries.add(entry);
            size[0]++;
            size[1] += bytesOf(entry) + 1;
            if (size[0] >= Gstr1WriterVersion.MAX_ITEMS_PER_PART
                    || size[1] >= Gstr1WriterVersion.MAX_BYTES_PER_PART) {
                blocks.add(groupBlock(section, keyField, childField, key, templateFiller, entries));
                pending.remove(key);
                sizes.remove(key);
            }
        }
        for (Map.Entry<String, List<ObjectNode>> e : pending.entrySet()) {
            blocks.add(groupBlock(section, keyField, childField, e.getKey(), templateFiller, e.getValue()));
        }
        return blocks;
    }

    private Block groupBlock(String section, String keyField, String childField, String key,
                             BiConsumer<String, ObjectNode> templateFiller, List<ObjectNode> entries) {
        ObjectNode template = NODES.objectNode();
        template.put(keyField, key);
        if (templateFiller != null) {
            templateFiller.accept(key, template);
        }
        return Block.group(section, keyField, childField, key, template, entries);
    }

    private static ObjectNode item(BigDecimal rate, BigDecimal taxable, TaxAmounts tax, BigDecimal cess) {
        ObjectNode det = NODES.objectNode();
        det.put("txval", amount(taxable));
        det.put("rt", amount(rate));
        if (tax.igst() != null) {
            det.put("iamt", amount(tax.igst()));
        } else {
            det.put("camt", amount(tax.cgst()));
            det.put("samt", amount(tax.samt()));
        }
        det.put("csamt", amount(cess));
        return NODES.objectNode().put("num", itemNumber(rate)).set("itm_det", det);
    }

    private static int itemNumber(BigDecimal rate) {
        BigDecimal num = rate.multiply(BigDecimal.valueOf(100)).add(BigDecimal.ONE);
        try {
            return num.intValueExact();
        } catch (ArithmeticException e) {
            return 1;
        }
    }

    private TaxAmounts tax(Map<String, Object> p, String orgGstin, String pos) {
        if (intraState(orgGstin, pos)) {
            return new TaxAmounts(null, num(p, "cgst"), num(p, "sgst"));
        }
        return new TaxAmounts(num(p, "igst"), null, null);
    }

    private static void putTaxKeys(ObjectNode node, TaxAmounts tax) {
        if (tax.igst() != null) {
            node.put("iamt", amount(tax.igst()));
        } else {
            node.put("camt", amount(tax.cgst()));
            node.put("samt", amount(tax.samt()));
        }
    }

    private static boolean intraState(String orgGstin, String pos) {
        return orgGstin != null && orgGstin.length() >= 2 && pos != null
                && pos.equals(orgGstin.substring(0, 2));
    }

    private static String noteTypeCode(String noteType) {
        return noteType != null && noteType.toLowerCase(Locale.ROOT).contains("debit") ? "D" : "C";
    }

    private static int docNumber(String voucherType) {
        String type = voucherType == null ? "" : voucherType.toLowerCase(Locale.ROOT);
        if (type.contains("credit note")) {
            return 5;
        }
        if (type.contains("debit note")) {
            return 4;
        }
        return 1;
    }

    private static String docTypeName(int docNum) {
        return switch (docNum) {
            case 4 -> "Debit Note";
            case 5 -> "Credit Note";
            default -> "Invoices for outward supply";
        };
    }

    private static String date(String isoDate) {
        if (isoDate == null) {
            return null;
        }
        try {
            return LocalDate.parse(isoDate).format(DD_MM_YYYY);
        } catch (Exception e) {
            return isoDate;
        }
    }

    static BigDecimal amount(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal norm = value.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros();
        return norm.signum() == 0 ? BigDecimal.ZERO : norm;
    }

    private static BigDecimal num(Map<String, Object> p, String key) {
        Object value = p.get(key);
        if (value instanceof BigDecimal bd) {
            return bd;
        }
        if (value instanceof Number n) {
            return new BigDecimal(n.toString());
        }
        return BigDecimal.ZERO;
    }

    private static String text(Map<String, Object> p, String key) {
        Object value = p.get(key);
        return value == null ? null : value.toString();
    }

    private static int bytesOf(JsonNode node) {
        try {
            return MAPPER.writeValueAsBytes(node).length;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to size portal JSON fragment", e);
        }
    }

    // ---------- packing ----------

    private List<PortalFilePart> pack(List<Block> blocks, String gstin, String fp) {
        List<PortalFilePart> parts = new ArrayList<>();
        Part current = new Part();
        for (Block block : blocks) {
            if (current.itemCount > 0 && (current.itemCount + block.items() > Gstr1WriterVersion.MAX_ITEMS_PER_PART
                    || current.bytes + block.bytes() > Gstr1WriterVersion.MAX_BYTES_PER_PART)) {
                parts.add(finish(current, gstin, fp, parts.size() + 1));
                current = new Part();
            }
            current.add(block);
        }
        parts.add(finish(current, gstin, fp, parts.size() + 1));
        if (parts.size() == 1) {
            PortalFilePart only = parts.get(0);
            return List.of(new PortalFilePart(fileName(fp, gstin, 0), only.bytes(), only.itemCount()));
        }
        List<PortalFilePart> named = new ArrayList<>();
        for (int i = 0; i < parts.size(); i++) {
            PortalFilePart part = parts.get(i);
            named.add(new PortalFilePart(fileName(fp, gstin, i + 1), part.bytes(), part.itemCount()));
        }
        return named;
    }

    private PortalFilePart finish(Part part, String gstin, String fp, int index) {
        ObjectNode root = NODES.objectNode();
        root.put("gstin", gstin);
        root.put("fp", fp);
        root.put("version", Gstr1WriterVersion.PORTAL_VERSION);
        root.put("hash", Gstr1WriterVersion.HASH_PLACEHOLDER);
        for (String section : SECTION_ORDER) {
            JsonNode node = part.sections.get(section);
            if (node != null) {
                root.set(section, node);
            }
        }
        byte[] bytes = serialize(root);
        return new PortalFilePart(fileName(fp, gstin, index), bytes, part.itemCount);
    }

    private static String fileName(String fp, String gstin, int partIndex) {
        String base = "GSTR1_" + fp + "_" + gstin;
        return partIndex <= 0 ? base + ".json" : base + "_" + partIndex + ".json";
    }

    private static byte[] serialize(ObjectNode root) {
        try {
            return MAPPER.writeValueAsBytes(root);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize portal JSON", e);
        }
    }

    private enum Kind { FLAT, GROUP, OBJECT }

    private record Block(Kind kind, String section, String keyField, String childField,
                         String groupKey, ObjectNode template, List<ObjectNode> entries,
                         ObjectNode object, int items, int bytes) {

        static Block flat(String section, ObjectNode entry) {
            return new Block(Kind.FLAT, section, null, null, null, null, null, entry, 1, bytesOf(entry));
        }

        static Block group(String section, String keyField, String childField, String groupKey,
                           ObjectNode template, List<ObjectNode> entries) {
            int bytes = 0;
            for (ObjectNode entry : entries) {
                bytes += bytesOf(entry) + 1;
            }
            return new Block(Kind.GROUP, section, keyField, childField, groupKey, template, entries,
                    null, entries.size(), bytes);
        }

        static Block object(String section, ObjectNode node) {
            return new Block(Kind.OBJECT, section, null, null, null, null, null, node, 1, bytesOf(node));
        }
    }

    private final class Part {
        private final Map<String, JsonNode> sections = new LinkedHashMap<>();
        private int itemCount;
        private long bytes;

        void add(Block block) {
            switch (block.kind()) {
                case FLAT -> {
                    arrayFor(block.section()).add(block.object());
                    itemCount += block.items();
                    bytes += block.bytes();
                }
                case OBJECT -> {
                    sections.put(block.section(), block.object());
                    itemCount += block.items();
                    bytes += block.bytes();
                }
                case GROUP -> addGroup(block);
            }
        }

        private void addGroup(Block block) {
            ArrayNode array = arrayFor(block.section());
            JsonNode last = array.isEmpty() ? null : array.get(array.size() - 1);
            if (last instanceof ObjectNode group
                    && block.groupKey().equals(group.path(block.keyField()).asText(null))) {
                group.withArray(block.childField()).addAll(block.entries());
            } else {
                ObjectNode group = block.template().deepCopy();
                group.set(block.childField(), NODES.arrayNode().addAll(block.entries()));
                array.add(group);
            }
            itemCount += block.items();
            bytes += block.bytes();
        }

        private ArrayNode arrayFor(String section) {
            JsonNode existing = sections.get(section);
            if (existing instanceof ArrayNode a) {
                return a;
            }
            ArrayNode array = NODES.arrayNode();
            sections.put(section, array);
            return array;
        }
    }
}
