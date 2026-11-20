package com.arktech.superaccountant.gstr1.services;

import com.arktech.superaccountant.gstr1.models.PreparedReturn;
import com.arktech.superaccountant.gstr1.models.PreparedReturnRow;
import com.arktech.superaccountant.gstr1.repository.PreparedReturnRepository;
import com.arktech.superaccountant.gstr1.repository.PreparedReturnRowRepository;
import com.arktech.superaccountant.gstr1.services.tables.AdvanceBuilder;
import com.arktech.superaccountant.gstr1.services.tables.AmendmentBuilder;
import com.arktech.superaccountant.gstr1.services.tables.B2bBuilder;
import com.arktech.superaccountant.gstr1.services.tables.B2clBuilder;
import com.arktech.superaccountant.gstr1.services.tables.B2csBuilder;
import com.arktech.superaccountant.gstr1.services.tables.CdnBuilder;
import com.arktech.superaccountant.gstr1.services.tables.DocsIssuedBuilder;
import com.arktech.superaccountant.gstr1.services.tables.ExportBuilder;
import com.arktech.superaccountant.gstr1.services.tables.HsnBuilder;
import com.arktech.superaccountant.gstr1.services.tables.NilExemptBuilder;
import com.arktech.superaccountant.workflow.models.Workflow;
import com.arktech.superaccountant.workflow.models.WorkflowType;
import com.arktech.superaccountant.workflow.repository.WorkflowRepository;
import com.arktech.superaccountant.workflow.service.WorkflowConflictException;
import com.arktech.superaccountant.workflow.snapshot.AmendmentFollowUp;
import com.arktech.superaccountant.workflow.snapshot.AmendmentFollowUpRepository;
import com.arktech.superaccountant.workflow.snapshot.AmendmentStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Builds and freezes the prepared GSTR-1 return for a workflow.
 *
 * <ul>
 *   <li>{@link #buildOrRebuild}: idempotent; replaces all rows from the latest
 *       captured data and fill-ins, but never touches Gstr1FillIn rows. Once the
 *       return is frozen, rows are read-only and the build is a no-op.</li>
 *   <li>{@link #freeze}: stamps frozenAt (idempotent). Called when the workflow
 *       finalizes; afterwards the frozen return is untouched by later day-book
 *       re-uploads.</li>
 *   <li>{@link #release}: clears frozenAt when a workflow is un-finalized;
 *       the next build rebuilds rows.</li>
 * </ul>
 *
 * <p>Rows are produced by running the {@link ReportingClassifier} over the captured
 * views and passing each class's views to the applicable table builder. HSN and DOCS
 * roll up across classes (the classifier never emits NIL — rate-0 supplies bucket as
 * B2CS/B2CL, so the nil builder receives those classes and self-selects). DOCS receives
 * all views because cancelled vouchers (NOT_REPORTED) still count in document series.
 */
@Service
public class Gstr1PrepareService {

    private static final Logger logger =
            LoggerFactory.getLogger(Gstr1PrepareService.class);

    /** Ship 7 / T22: an IFF carries only B2B and CDNR (their amendment variants). */
    private static final Set<ReportingClass> IFF_CLASSES = Set.of(
            ReportingClass.B2B, ReportingClass.CDNR);

    @Autowired
    private WorkflowRepository workflowRepository;

    @Autowired
    private AmendmentFollowUpRepository followUpRepository;

    @Autowired
    private PreparedReturnRepository preparedReturnRepository;

    @Autowired
    private PreparedReturnRowRepository rowRepository;

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
    private NilExemptBuilder nilExemptBuilder;
    @Autowired
    private ExportBuilder exportBuilder;
    @Autowired
    private CdnBuilder cdnBuilder;
    @Autowired
    private AdvanceBuilder advanceBuilder;
    @Autowired
    private HsnBuilder hsnBuilder;
    @Autowired
    private DocsIssuedBuilder docsIssuedBuilder;
    @Autowired
    private AmendmentBuilder amendmentBuilder;

    private ReportingClassifier classifier(Workflow workflow) {
        return new ReportingClassifier((view, attribute) -> fillInService.resolve(
                workflow, view.getVoucherKey(), null, attribute));
    }

    @Transactional
    public PreparedReturn buildOrRebuild(Workflow workflow) {
        requireGstr1(workflow);
        lockWorkflow(workflow);
        PreparedReturn prepared = existing(workflow).orElseGet(() -> create(workflow));
        if (prepared.getFrozenAt() != null) {
            return prepared;
        }
        List<PreparedReturnRow> rows = buildRows(workflow, prepared.getId());
        rowRepository.deleteByPreparedReturnId(prepared.getId());
        prepared.setBuiltAt(Instant.now());
        preparedReturnRepository.save(prepared);
        // rows are replaced wholesale on rebuild: fresh primary keys, generated by the DB
        rows.forEach(row -> row.setId(null));
        rowRepository.saveAll(rows);
        return prepared;
    }

    @Transactional
    public PreparedReturn freeze(Workflow workflow) {
        requireGstr1(workflow);
        lockWorkflow(workflow);
        PreparedReturn prepared = existing(workflow)
                .orElseGet(() -> buildOrRebuild(workflow));
        if (prepared.getFrozenAt() == null) {
            prepared.setFrozenAt(Instant.now());
            preparedReturnRepository.save(prepared);
        }
        return prepared;
    }

    @Transactional
    public PreparedReturn release(Workflow workflow) {
        requireGstr1(workflow);
        PreparedReturn prepared = existing(workflow).orElse(null);
        if (prepared != null && prepared.getFrozenAt() != null) {
            prepared.setFrozenAt(null);
            preparedReturnRepository.save(prepared);
            reopenIncludedFollowUps(workflow, prepared);
        }
        return prepared;
    }

    private void reopenIncludedFollowUps(Workflow workflow, PreparedReturn prepared) {
        Set<UUID> includedIds = rowRepository.findByPreparedReturnId(prepared.getId()).stream()
                .map(PreparedReturnRow::getFollowUpId)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        if (includedIds.isEmpty()) {
            return;
        }
        for (AmendmentFollowUp followUp : followUpRepository.findByOrganizationIdAndStatus(
                workflow.getOrganizationId(), AmendmentStatus.DONE)) {
            if (includedIds.contains(followUp.getId())) {
                followUp.setStatus(AmendmentStatus.OPEN);
                followUp.setDoneBy(null);
                followUp.setDoneAt(null);
                followUpRepository.save(followUp);
            }
        }
    }

    /**
     * R8 closure (T21): after a successful finalize, the open follow-ups of the
     * organization whose amendment/late row was included in THIS prepared return
     * flip to DONE. Idempotent (only OPEN follow-ups are touched) and must never
     * block finalize, so any failure here is logged and swallowed.
     */
    @Transactional
    public void closeIncludedFollowUps(Workflow workflow, String actor) {
        try {
            PreparedReturn prepared = existing(workflow).orElse(null);
            if (prepared == null) {
                return;
            }
            Set<UUID> includedIds = rowRepository.findByPreparedReturnId(prepared.getId()).stream()
                    .map(PreparedReturnRow::getFollowUpId)
                    .filter(java.util.Objects::nonNull)
                    .collect(java.util.stream.Collectors.toSet());
            if (includedIds.isEmpty()) {
                return;
            }
            String note = "Closed: amendment row included in "
                    + YearMonth.from(workflow.getPeriodStart()) + " prepared return";
            for (AmendmentFollowUp followUp : followUpRepository.findByOrganizationIdAndStatus(
                    workflow.getOrganizationId(), AmendmentStatus.OPEN)) {
                if (!workflow.getId().equals(followUp.getWorkflowId())
                        && includedIds.contains(followUp.getId())) {
                    followUp.setStatus(AmendmentStatus.DONE);
                    followUp.setDoneBy(actor);
                    followUp.setDoneAt(Instant.now());
                    followUp.setDoneNote(note);
                    followUpRepository.save(followUp);
                }
            }
        } catch (RuntimeException e) {
            logger.warn("Follow-up closure after finalize failed for workflow {}",
                    workflow.getId(), e);
        }
    }

    // ---------- row production ----------

    private List<PreparedReturnRow> buildRows(Workflow workflow, UUID preparedReturnId) {
        List<VoucherView> views = viewBuilder.build(workflow);
        YearMonth period = YearMonth.from(workflow.getPeriodStart());
        ReportingClassifier classifier = classifier(workflow);
        boolean iff = workflow.getType() == WorkflowType.IFF;
        // T22: a quarter GSTR-1 leaves out documents a finalized IFF already reported
        Set<String> iffDocKeys = iff ? Set.of() : iffReportedDocKeys(workflow);
        Map<ReportingClass, List<VoucherView>> byClass = new EnumMap<>(ReportingClass.class);
        for (VoucherView view : views) {
            ReportingClass category = classifier.classify(view, period).category();
            if (iff && !IFF_CLASSES.contains(category)) {
                continue;
            }
            byClass.computeIfAbsent(category, k -> new ArrayList<>()).add(view);
        }
        if (!iffDocKeys.isEmpty()) {
            excludeIffReportedDocs(byClass, iffDocKeys);
        }
        List<VoucherView> reportable = views.stream()
                .filter(v -> !byClass.getOrDefault(ReportingClass.NOT_REPORTED, List.of()).contains(v))
                .filter(v -> !iffDocKeys.contains("b2b:" + v.getVoucherKey())
                        && !iffDocKeys.contains("cdn:" + v.getVoucherKey()))
                .toList();

        List<PreparedReturnRow> rows = new ArrayList<>();
        rows.addAll(b2bBuilder.build(workflow, byClass.getOrDefault(ReportingClass.B2B, List.of())));
        if (!iff) {
            rows.addAll(b2clBuilder.build(workflow, byClass.getOrDefault(ReportingClass.B2CL, List.of())));
            rows.addAll(b2csBuilder.build(workflow, byClass.getOrDefault(ReportingClass.B2CS, List.of())));
            rows.addAll(nilExemptBuilder.build(workflow, union(byClass,
                    ReportingClass.B2CS, ReportingClass.B2CL)));
            rows.addAll(exportBuilder.build(workflow, byClass.getOrDefault(ReportingClass.EXP, List.of())));
            rows.addAll(advanceBuilder.build(workflow, union(byClass,
                    ReportingClass.ADVANCE, ReportingClass.ADV_ADJ)));
        }
        rows.addAll(cdnBuilder.build(workflow, union(byClass,
                ReportingClass.CDNR, ReportingClass.CDNUR)));
        if (!iff) {
            rows.addAll(hsnBuilder.build(workflow, reportable));
            rows.addAll(docsIssuedBuilder.build(workflow, views));
        }
        // amendment/late rows (T20): folded in after the normal builders
        rows.addAll(amendmentBuilder.build(workflow, rows));
        rows.forEach(row -> row.setPreparedReturnId(preparedReturnId));
        return rows;
    }

    /**
     * T22: row keys (doc keys) of frozen IFF prepared returns whose period falls
     * inside this workflow's period. UNVERIFIED: whether the portal keys documents
     * the same way across IFF and quarterly GSTR-1 files; matching uses this
     * application's row keys (b2b:&lt;voucher key&gt;, cdn:&lt;voucher key&gt;).
     */
    private Set<String> iffReportedDocKeys(Workflow workflow) {
        if (!workflow.getType().equals(WorkflowType.GSTR1)) {
            return Set.of();
        }
        Set<String> keys = new java.util.HashSet<>();
        for (Workflow candidate : workflowRepository.findAllByOrganizationIdOrderByCreatedAtDesc(
                workflow.getOrganizationId())) {
            if (candidate.getType() != WorkflowType.IFF) {
                continue;
            }
            if (candidate.getPeriodStart() == null || candidate.getPeriodEnd() == null
                    || candidate.getPeriodEnd().isBefore(workflow.getPeriodStart())
                    || candidate.getPeriodStart().isAfter(workflow.getPeriodEnd())) {
                continue;
            }
            Optional<PreparedReturn> iffReturn = preparedReturnRepository
                    .findByWorkflowIdAndReturnKind(candidate.getId(), "IFF");
            if (iffReturn.isEmpty() || iffReturn.get().getFrozenAt() == null) {
                continue; // only a FROZEN (finalized) IFF counts as reported
            }
            rowRepository.findByPreparedReturnId(iffReturn.get().getId()).stream()
                    .filter(row -> !row.isAmendment())
                    .map(PreparedReturnRow::getRowKey)
                    .filter(java.util.Objects::nonNull)
                    .forEach(keys::add);
        }
        return keys;
    }

    /** Drops IFF-reported views from the B2B/CDNR class buckets by doc key. */
    private static void excludeIffReportedDocs(Map<ReportingClass, List<VoucherView>> byClass,
                                               Set<String> iffDocKeys) {
        for (ReportingClass cls : List.of(ReportingClass.B2B, ReportingClass.CDNR,
                ReportingClass.CDNUR)) {
            List<VoucherView> views = byClass.get(cls);
            if (views == null) {
                continue;
            }
            views.removeIf(v -> iffDocKeys.contains("b2b:" + v.getVoucherKey())
                    || iffDocKeys.contains("cdn:" + v.getVoucherKey()));
        }
    }

    private static List<VoucherView> union(Map<ReportingClass, List<VoucherView>> byClass,
                                           ReportingClass first, ReportingClass second) {
        List<VoucherView> result = new ArrayList<>();
        result.addAll(byClass.getOrDefault(first, List.of()));
        result.addAll(byClass.getOrDefault(second, List.of()));
        return result;
    }

    // ---------- helpers ----------

    private Optional<PreparedReturn> existing(Workflow workflow) {
        return preparedReturnRepository.findByWorkflowIdAndReturnKind(
                workflow.getId(), returnKind(workflow));
    }

    private PreparedReturn create(Workflow workflow) {
        PreparedReturn prepared = new PreparedReturn();
        prepared.setWorkflowId(workflow.getId());
        prepared.setReturnKind(returnKind(workflow));
        prepared.setBuiltAt(Instant.now());
        return preparedReturnRepository.save(prepared);
    }

    /** T22: IFF workflows freeze their own prepared return with returnKind=IFF. */
    public static String returnKind(Workflow workflow) {
        return workflow != null && workflow.getType() == WorkflowType.IFF ? "IFF" : "GSTR1";
    }

    private void lockWorkflow(Workflow workflow) {
        workflowRepository.findByIdForUpdate(workflow.getId())
                .orElseThrow(() -> new WorkflowConflictException("Workflow no longer exists"));
    }

    private void requireGstr1(Workflow workflow) {
        if (workflow == null || (workflow.getType() != WorkflowType.GSTR1
                && workflow.getType() != WorkflowType.IFF)) {
            throw new WorkflowConflictException(
                    "The prepared-return pipeline applies to GSTR1 and IFF only");
        }
    }
}
