package com.arktech.superaccountant.gstr1.services.tables;

import com.arktech.superaccountant.gstr1.models.Gstr1FillInAttribute;
import com.arktech.superaccountant.gstr1.models.Gstr1FillInScope;
import com.arktech.superaccountant.gstr1.models.PreparedReturn;
import com.arktech.superaccountant.gstr1.models.PreparedReturnRow;
import com.arktech.superaccountant.gstr1.repository.Gstr1FillInRepository;
import com.arktech.superaccountant.gstr1.repository.PreparedReturnRepository;
import com.arktech.superaccountant.gstr1.repository.PreparedReturnRowRepository;
import com.arktech.superaccountant.gstr1.services.Gstr1FillInService;
import com.arktech.superaccountant.gstr1.services.Gstr1PrepareService;
import com.arktech.superaccountant.gstr1.services.ReportingClass;
import com.arktech.superaccountant.gstr1.services.ReportingClassifier;
import com.arktech.superaccountant.gstr1.services.VoucherView;
import com.arktech.superaccountant.gstr1.services.VoucherViewBuilder;
import com.arktech.superaccountant.workflow.models.Workflow;
import com.arktech.superaccountant.workflow.repository.WorkflowRepository;
import com.arktech.superaccountant.workflow.snapshot.AmendmentChangeKind;
import com.arktech.superaccountant.workflow.snapshot.AmendmentFollowUp;
import com.arktech.superaccountant.workflow.snapshot.AmendmentFollowUpRepository;
import com.arktech.superaccountant.workflow.snapshot.AmendmentStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Builds amendment and late-reporting rows for a prepared return (plan T20).
 *
 * <p>Open {@link AmendmentFollowUp}s of the organization are folded into the
 * current workflow's return (follow-ups attached to the current workflow are
 * skipped: their return simply rebuilds). Per change kind:
 * <ul>
 *   <li>CHANGED → amendment row in the amendment table (B2B→B2BA, ...), revised
 *       values from the current books' view of the voucher, original number/date
 *       from the frozen original prepared-return row's payload (never the day book).</li>
 *   <li>DELETED → amendment to zero; emitted only when the accountant has
 *       acknowledged with an AMENDMENT_ZERO_ACK fill-in (R5). Without the ack the
 *       finalize gate (Gstr1FinalizePrecondition) blocks instead.
 *       UNVERIFIED: portal treatment of a cancellation after filing (Table 13
 *       counts cancelled docs only for period cancellations) — needs confirmation.</li>
 *   <li>LATE → fresh row in the NORMAL table flagged lateReporting; the liability
 *       lands in the current return (R6). Amendment sections are not involved.</li>
 *   <li>NIL follow-ups are skipped: the portal JSON has no NIL amendment section
 *       (R4, UNVERIFIED).</li>
 * </ul>
 *
 * <p>S4 (correcting a doc reported in IFF) is out of scope for this ship.
 */
@Component
public class AmendmentBuilder {

    private static final DateTimeFormatter MM_YYYY = DateTimeFormatter.ofPattern("MMyyyy");
    private static final Set<String> ZEROED_KEYS = Set.of(
            "taxableValue", "igst", "cgst", "sgst", "cess", "invoiceValue", "advanceAmount");

    @Autowired
    private AmendmentFollowUpRepository followUpRepository;
    @Autowired
    private PreparedReturnRepository preparedReturnRepository;
    @Autowired
    private PreparedReturnRowRepository rowRepository;
    @Autowired
    private Gstr1FillInRepository fillInRepository;
    @Autowired
    private WorkflowRepository workflowRepository;
    @Autowired
    private VoucherViewBuilder viewBuilder;
    @Autowired
    private Gstr1FillInService fillInService;
    @Autowired
    private B2bBuilder b2bBuilder;
    @Autowired
    private B2clBuilder b2clBuilder;
    @Autowired
    private B2csBuilder b2csBuilder;
    @Autowired
    private ExportBuilder exportBuilder;
    @Autowired
    private CdnBuilder cdnBuilder;
    @Autowired
    private AdvanceBuilder advanceBuilder;

    public AmendmentBuilder(AmendmentFollowUpRepository followUpRepository,
                            PreparedReturnRepository preparedReturnRepository,
                            PreparedReturnRowRepository rowRepository,
                            Gstr1FillInRepository fillInRepository,
                            WorkflowRepository workflowRepository,
                            VoucherViewBuilder viewBuilder,
                            Gstr1FillInService fillInService,
                            B2bBuilder b2bBuilder,
                            B2clBuilder b2clBuilder,
                            B2csBuilder b2csBuilder,
                            ExportBuilder exportBuilder,
                            CdnBuilder cdnBuilder,
                            AdvanceBuilder advanceBuilder) {
        this.followUpRepository = followUpRepository;
        this.preparedReturnRepository = preparedReturnRepository;
        this.rowRepository = rowRepository;
        this.fillInRepository = fillInRepository;
        this.workflowRepository = workflowRepository;
        this.viewBuilder = viewBuilder;
        this.fillInService = fillInService;
        this.b2bBuilder = b2bBuilder;
        this.b2clBuilder = b2clBuilder;
        this.b2csBuilder = b2csBuilder;
        this.exportBuilder = exportBuilder;
        this.cdnBuilder = cdnBuilder;
        this.advanceBuilder = advanceBuilder;
    }

    public AmendmentBuilder() {
    }

    /**
     * Marks late-reported rows among {@code currentRows} in place and returns the
     * amendment rows to append to the return.
     */
    public List<PreparedReturnRow> build(Workflow current, List<PreparedReturnRow> currentRows) {
        List<AmendmentFollowUp> followUps = followUpRepository.findByOrganizationIdAndStatus(
                current.getOrganizationId(), AmendmentStatus.OPEN);
        if (followUps.isEmpty()) {
            return List.of();
        }
        Map<String, VoucherView> viewsByKey = new LinkedHashMap<>();
        for (VoucherView view : viewBuilder.buildAll(current)) {
            viewsByKey.put(view.getVoucherKey(), view);
        }
        ReportingClassifier classifier = new ReportingClassifier(
                (view, attribute) -> fillInService.resolve(
                        current, view.getVoucherKey(), null, attribute));
        Set<String> usedKeys = new HashSet<>();
        for (PreparedReturnRow row : currentRows) {
            usedKeys.add(row.getRowKey());
        }
        List<PreparedReturnRow> amendmentRows = new ArrayList<>();
        for (AmendmentFollowUp followUp : followUps) {
            if (current.getId().equals(followUp.getWorkflowId())) {
                continue; // this workflow's return rebuilds from its own captured data
            }
            switch (followUp.getChange() == null ? AmendmentChangeKind.CHANGED : followUp.getChange()) {
                case LATE -> addLateRow(current, followUp, viewsByKey, classifier, currentRows,
                        amendmentRows, usedKeys);
                case CHANGED -> addChangedRow(current, followUp, viewsByKey, classifier,
                        amendmentRows, usedKeys);
                case DELETED -> addDeletedRow(current, followUp, amendmentRows, usedKeys);
            }
        }
        return amendmentRows;
    }

    // ---------- change kinds ----------

    private void addLateRow(Workflow current, AmendmentFollowUp followUp,
                            Map<String, VoucherView> viewsByKey, ReportingClassifier classifier,
                            List<PreparedReturnRow> currentRows,
                            List<PreparedReturnRow> amendmentRows, Set<String> usedKeys) {
        String voucherKey = followUp.getVoucherKey();
        if (voucherKey == null) {
            return;
        }
        VoucherView view = viewsByKey.get(voucherKey);
        if (view == null) {
            return; // voucher no longer in the current books; nothing to report
        }
        // in-period voucher: the normal builders already produced the row
        for (PreparedReturnRow row : currentRows) {
            if (!row.isAmendment() && voucherKey.equals(row.getSourceVoucherKey())) {
                markLate(row, followUp, view);
                return;
            }
        }
        YearMonth period = YearMonth.from(current.getPeriodStart());
        ReportingClass cls = classifier.classify(view, period).category();
        List<PreparedReturnRow> built = buildForClass(cls, current, List.of(view));
        for (PreparedReturnRow row : built) {
            markLate(row, followUp, view);
            uniquify(row, usedKeys, "late:");
            usedKeys.add(row.getRowKey());
            amendmentRows.add(row);
        }
    }

    private void addChangedRow(Workflow current, AmendmentFollowUp followUp,
                               Map<String, VoucherView> viewsByKey, ReportingClassifier classifier,
                               List<PreparedReturnRow> amendmentRows, Set<String> usedKeys) {
        PreparedReturnRow original = originalRow(followUp);
        if (original == null) {
            return;
        }
        VoucherView view = followUp.getVoucherKey() == null ? null
                : viewsByKey.get(followUp.getVoucherKey());
        if (view == null) {
            return; // revised view unavailable; nothing reliable to report
        }
        ReportingClass cls = classifier.classify(view, YearMonth.from(current.getPeriodStart())).category();
        List<PreparedReturnRow> built = buildForClass(cls, current, List.of(view));
        if (built.isEmpty()) {
            return; // the builder filtered the view out (e.g. cancelled); UNVERIFIED fallback
        }
        PreparedReturnRow row = built.get(0);
        String amendCode = amendmentCode(row.getTableCode());
        if (amendCode == null) {
            return;
        }
        Map<String, Object> payload = row.payload();
        putOriginalKeys(payload, amendCode, original.payload());
        TableRows.savePayload(row, payload);
        row.setTableCode(amendCode);
        row.setRowKey(uniquified(amendCode.toLowerCase(Locale.ROOT) + ":" + followUp.getVoucherKey(),
                usedKeys, String.valueOf(followUp.getId())));
        row.setAmendment(true);
        row.setOriginalPeriod(originalPeriod(followUp));
        row.setOriginalDocKey(followUp.getOriginalDocKey());
        row.setFollowUpId(followUp.getId());
        usedKeys.add(row.getRowKey());
        amendmentRows.add(row);
    }

    private void addDeletedRow(Workflow current, AmendmentFollowUp followUp,
                               List<PreparedReturnRow> amendmentRows, Set<String> usedKeys) {
        if (followUp.getVoucherKey() == null || followUp.getTableCode() == null) {
            return;
        }
        if (!ackPresent(current, followUp)) {
            return; // finalize gate blocks until the accountant acknowledges
        }
        PreparedReturnRow original = originalRow(followUp);
        if (original == null) {
            return;
        }
        String amendCode = amendmentCode(followUp.getTableCode());
        if (amendCode == null) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>(original.payload());
        for (String key : ZEROED_KEYS) {
            if (payload.containsKey(key)) {
                payload.put(key, BigDecimal.ZERO);
            }
        }
        putOriginalKeys(payload, amendCode, original.payload());
        String rowKey = uniquified(amendCode.toLowerCase(Locale.ROOT) + ":" + followUp.getVoucherKey(),
                usedKeys, String.valueOf(followUp.getId()));
        amendmentRows.add(TableRows.amendmentRow(current.getId(), amendCode, rowKey,
                followUp.getVoucherKey(), payload, originalPeriod(followUp),
                followUp.getOriginalDocKey(), followUp.getId()));
        usedKeys.add(rowKey);
    }

    // ---------- helpers ----------

    private void markLate(PreparedReturnRow row, AmendmentFollowUp followUp, VoucherView view) {
        row.setLateReporting(true);
        row.setFollowUpId(followUp.getId());
        if (view.getVoucherDate() != null) {
            row.setOriginalPeriod(YearMonth.from(view.getVoucherDate()).toString());
        }
    }

    private List<PreparedReturnRow> buildForClass(ReportingClass cls, Workflow current,
                                                  List<VoucherView> views) {
        return switch (cls) {
            case B2B -> b2bBuilder.build(current, views);
            case B2CL -> b2clBuilder.build(current, views);
            case B2CS -> b2csBuilder.build(current, views);
            case EXP -> exportBuilder.build(current, views);
            case CDNR, CDNUR -> cdnBuilder.build(current, views);
            case ADVANCE, ADV_ADJ -> advanceBuilder.build(current, views);
            default -> List.of();
        };
    }

    /**
     * R4: ReportingClass/table → amendment table code. NIL has no portal
     * amendment section, so it maps to null and follow-ups are skipped.
     */
    private static String amendmentCode(String tableCode) {
        return switch (tableCode == null ? "" : tableCode) {
            case "B2B" -> "B2BA";
            case "B2CL" -> "B2CLA";
            case "B2CS" -> "B2CSA";
            case "CDNR" -> "CDNRA";
            case "CDNUR" -> "CDNURA";
            case "EXP" -> "EXPA";
            case "AT" -> "ATA";
            case "TXPD" -> "TXPDA";
            default -> null;
        };
    }

    /** Original number/date provenance keys per amendment table (writer contract). */
    private static void putOriginalKeys(Map<String, Object> payload, String amendCode,
                                        Map<String, Object> originalPayload) {
        switch (amendCode) {
            case "B2BA", "B2CLA", "EXPA" -> {
                payload.put("oinum", originalPayload.get("invoiceNo"));
                payload.put("oidt", originalPayload.get("invoiceDate"));
            }
            case "CDNRA", "CDNURA" -> {
                payload.put("ontNum", originalPayload.get("noteNo"));
                payload.put("ontDate", originalPayload.get("noteDate"));
            }
            case "B2CSA", "ATA", "TXPDA" ->
                // no doc number in these tables: the amendment points at the original period
                payload.put("omon", mmYYYY(originalPayload));
            default -> { }
        }
    }

    private static String mmYYYY(Map<String, Object> payload) {
        String date = firstNonBlank(payload.get("invoiceDate"), payload.get("noteDate"),
                payload.get("voucherDate"));
        if (date == null || date.length() < 7) {
            return null;
        }
        try {
            return YearMonth.parse(date.substring(0, 7)).format(MM_YYYY);
        } catch (Exception e) {
            return null;
        }
    }

    private static String firstNonBlank(Object... values) {
        for (Object value : values) {
            if (value != null && !value.toString().isBlank()) {
                return value.toString();
            }
        }
        return null;
    }

    private String originalPeriod(AmendmentFollowUp followUp) {
        return workflowRepository.findById(followUp.getOriginalWorkflowId())
                .map(w -> YearMonth.from(w.getPeriodStart()).toString())
                .orElse(null);
    }

    private PreparedReturnRow originalRow(AmendmentFollowUp followUp) {
        if (followUp.getOriginalWorkflowId() == null || followUp.getOriginalDocKey() == null) {
            return null;
        }
        Optional<Workflow> originalWorkflow =
                workflowRepository.findById(followUp.getOriginalWorkflowId());
        if (originalWorkflow.isEmpty()) {
            return null;
        }
        // T22: the original may be an IFF prepared return, not only a GSTR1 one
        Optional<PreparedReturn> prepared = preparedReturnRepository.findByWorkflowIdAndReturnKind(
                followUp.getOriginalWorkflowId(),
                Gstr1PrepareService.returnKind(originalWorkflow.get()));
        if (prepared.isEmpty()) {
            return null;
        }
        return rowRepository.findByPreparedReturnId(prepared.get().getId()).stream()
                .filter(row -> followUp.getOriginalDocKey().equals(row.getRowKey()))
                .findFirst()
                .orElse(null);
    }

    /** True when a DELETED follow-up can yield an amend-to-zero row (original row and amendment section exist). */
    public boolean canAmendToZero(AmendmentFollowUp followUp) {
        return amendmentCode(followUp.getTableCode()) != null && originalRow(followUp) != null;
    }

    private boolean ackPresent(Workflow current, AmendmentFollowUp followUp) {
        return fillInRepository.findByWorkflowIdAndScopeAndKeyAndAttribute(
                current.getId(), Gstr1FillInScope.VOUCHER, followUp.getVoucherKey(),
                Gstr1FillInAttribute.AMENDMENT_ZERO_ACK).isPresent();
    }

    private static String uniquified(String candidate, Set<String> usedKeys, String suffix) {
        return usedKeys.contains(candidate) ? candidate + "-" + suffix : candidate;
    }

    private static void uniquify(PreparedReturnRow row, Set<String> usedKeys, String prefix) {
        if (usedKeys.contains(row.getRowKey())) {
            row.setRowKey(prefix + row.getRowKey());
        }
    }
}
