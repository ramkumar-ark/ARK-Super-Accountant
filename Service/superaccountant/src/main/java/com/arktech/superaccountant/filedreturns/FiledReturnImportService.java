package com.arktech.superaccountant.filedreturns;

import com.arktech.superaccountant.filedreturns.payload.Gstr3bEntryRequest;
import com.arktech.superaccountant.masters.models.Organization;
import com.arktech.superaccountant.masters.repository.OrganizationRepository;
import com.arktech.superaccountant.workflow.models.Workflow;
import com.arktech.superaccountant.workflow.models.WorkflowStage;
import com.arktech.superaccountant.workflow.models.WorkflowType;
import com.arktech.superaccountant.workflow.repository.WorkflowRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class FiledReturnImportService {

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private WorkflowRepository workflowRepository;

    @Autowired
    private FiledReturnSnapshotRepository snapshotRepository;

    @Autowired
    private FiledReturnLineRepository lineRepository;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    public FiledReturnSnapshot save(UUID org, String actor, SnapshotDraft draft) {
        Organization organization = organizationRepository.findById(org)
                .orElseThrow(() -> new FiledReturnValidationException("Organization not found"));
        String orgGstin = organization.getGstin();
        String gstin = draft.gstin() == null || draft.gstin().isBlank() ? orgGstin : draft.gstin();
        if (orgGstin == null || !gstin.equalsIgnoreCase(orgGstin)) {
            throw new FiledReturnValidationException(
                    "GSTIN in file (" + gstin + ") does not match the company GSTIN (" + orgGstin + ")");
        }

        Workflow row = matchBaselineRow(org, draft.formType(), draft.filingPeriod());
        if (draft.formType() == FiledFormType.GSTR3B) {
            validate3bLineCodes(draft.lines());
        }
        LocalDate coverStart;
        LocalDate coverEnd;
        if (draft.formType() == FiledFormType.IFF) {
            YearMonth month = YearMonth.parse(draft.filingPeriod());
            coverStart = month.atDay(1);
            coverEnd = month.atEndOfMonth();
        } else {
            coverStart = row.getPeriodStart();
            coverEnd = row.getPeriodEnd();
        }

        String contentHash = contentHash(draft.lines());

        Optional<FiledReturnSnapshot> existing = snapshotRepository
                .findByOrganizationIdAndFormTypeAndFilingPeriodAndSupersededAtIsNull(
                        org, draft.formType(), draft.filingPeriod());
        if (existing.isPresent() && existing.get().getContentHash().equals(contentHash)) {
            return existing.get();
        }
        existing.ifPresent(current -> {
            current.setSupersededAt(Instant.now());
            snapshotRepository.save(current);
        });

        FiledReturnSnapshot snapshot = new FiledReturnSnapshot();
        snapshot.setOrganizationId(org);
        snapshot.setFormType(draft.formType());
        snapshot.setFilingPeriod(draft.filingPeriod());
        snapshot.setCoverStart(coverStart);
        snapshot.setCoverEnd(coverEnd);
        snapshot.setBaselineWorkflowId(row.getId());
        snapshot.setGstin(gstin);
        snapshot.setSourceKind(draft.sourceKind());
        snapshot.setFileId(draft.fileId());
        snapshot.setParserVersion(draft.parserVersion());
        snapshot.setContentHash(contentHash);
        snapshot.setImportedBy(actor);
        snapshot = snapshotRepository.save(snapshot);

        int lineNo = 1;
        List<FiledReturnLine> toSave = new ArrayList<>();
        for (FiledReturnLine line : draft.lines()) {
            line.setSnapshotId(snapshot.getId());
            line.setLineNo(lineNo++);
            toSave.add(line);
        }
        lineRepository.saveAll(toSave);

        eventPublisher.publishEvent(new FiledReturnImportedEvent(org, snapshot.getId()));
        return snapshot;
    }

    private Workflow matchBaselineRow(UUID org, FiledFormType formType, String filingPeriod) {
        WorkflowType wantedType = formType == FiledFormType.GSTR3B ? WorkflowType.GSTR3B : WorkflowType.GSTR1;
        LocalDate first = YearMonth.parse(filingPeriod).atDay(1);
        return workflowRepository.findAllByOrganizationIdAndStageAndActiveTrue(org, WorkflowStage.BASELINE).stream()
                .filter(w -> w.getType() == wantedType)
                .filter(w -> !w.getPeriodStart().isAfter(first) && !w.getPeriodEnd().isBefore(first))
                .findFirst()
                .orElseThrow(() -> new FiledReturnConflictException(
                        "No baseline period covers " + filingPeriod));
    }

    private String contentHash(List<FiledReturnLine> lines) {
        List<FiledReturnLine> sorted = new ArrayList<>(lines);
        sorted.sort(Comparator
                .comparing(FiledReturnLine::getTableCode,
                        Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(FiledReturnLine::getCounterpartyGstin,
                        Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(FiledReturnLine::getDocNumber,
                        Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(FiledReturnLine::getRate,
                        Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(FiledReturnLine::getLineNo));
        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode array = root.putArray("lines");
        for (FiledReturnLine l : sorted) {
            ObjectNode node = array.addObject();
            node.put("tableCode", l.getTableCode());
            node.put("docType", l.getDocType());
            node.put("counterpartyGstin", l.getCounterpartyGstin());
            node.put("docNumber", l.getDocNumber());
            node.put("docDate", l.getDocDate() == null ? null : l.getDocDate().toString());
            node.put("originalDocNumber", l.getOriginalDocNumber());
            node.put("originalDocDate", l.getOriginalDocDate() == null ? null : l.getOriginalDocDate().toString());
            node.put("originalPeriod", l.getOriginalPeriod());
            node.put("supplyType", l.getSupplyType());
            node.put("placeOfSupply", l.getPlaceOfSupply());
            node.put("rate", l.getRate() == null ? null : l.getRate().toPlainString());
            node.put("invoiceType", l.getInvoiceType());
            node.put("label", l.getLabel());
            node.put("invoiceValue", l.getInvoiceValue() == null ? null : l.getInvoiceValue().toPlainString());
            node.put("taxable", nvl(l.getTaxable()).toPlainString());
            node.put("igst", nvl(l.getIgst()).toPlainString());
            node.put("cgst", nvl(l.getCgst()).toPlainString());
            node.put("sgst", nvl(l.getSgst()).toPlainString());
            node.put("cess", nvl(l.getCess()).toPlainString());
        }
        return sha256(root.toString());
    }

    private BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of().formatHex(digest.digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public void validate3bLineCodes(List<FiledReturnLine> lines) {
        Set<String> seen = new HashSet<>();
        List<String> bad = new ArrayList<>();
        for (FiledReturnLine line : lines) {
            boolean known = false;
            for (Gstr3bRowCode code : Gstr3bRowCode.values()) {
                if (code.code().equals(line.getTableCode())) {
                    known = true;
                    break;
                }
            }
            if (!known || !seen.add(line.getTableCode())) {
                bad.add(line.getTableCode());
            }
        }
        if (!bad.isEmpty()) {
            throw new FiledReturnValidationException("Invalid or duplicate 3B rows: " + String.join(", ", bad));
        }
    }

    public void validate3bRows(List<Gstr3bEntryRequest.Row> rows) {        Set<String> seen = new HashSet<>();
        List<String> bad = new ArrayList<>();
        for (Gstr3bEntryRequest.Row row : rows) {
            boolean known = false;
            for (Gstr3bRowCode code : Gstr3bRowCode.values()) {
                if (code.code().equals(row.rowCode())) {
                    known = true;
                    break;
                }
            }
            if (!known) {
                bad.add(row.rowCode());
            } else if (!seen.add(row.rowCode())) {
                bad.add(row.rowCode());
            }
        }
        if (!bad.isEmpty()) {
            throw new FiledReturnValidationException("Invalid or duplicate 3B rows: " + String.join(", ", bad));
        }
    }

    public List<FiledReturnSnapshot> currentSnapshots(UUID org, UUID baselineWorkflowId) {
        Workflow row = workflowRepository.findById(baselineWorkflowId)
                .orElseThrow(() -> new FiledReturnConflictException("Baseline workflow not found"));
        return snapshotRepository
                .findAllByOrganizationIdAndBaselineWorkflowIdOrderByFilingPeriodAscImportedAtAsc(
                        org, baselineWorkflowId).stream()
                .filter(s -> s.getSupersededAt() == null)
                .filter(s -> !s.getCoverStart().isBefore(row.getPeriodStart())
                        && !s.getCoverEnd().isAfter(row.getPeriodEnd()))
                .toList();
    }
}
