package com.arktech.superaccountant.filedreturns.recon;

import com.arktech.superaccountant.daybook.models.DayBookUploadJob;
import com.arktech.superaccountant.daybook.models.ParsedVoucher;
import com.arktech.superaccountant.daybook.repository.DayBookUploadJobRepository;
import com.arktech.superaccountant.daybook.repository.ParsedVoucherRepository;
import com.arktech.superaccountant.filedreturns.FiledFormType;
import com.arktech.superaccountant.filedreturns.FiledReturnLine;
import com.arktech.superaccountant.filedreturns.FiledReturnLineRepository;
import com.arktech.superaccountant.filedreturns.FiledReturnSnapshot;
import com.arktech.superaccountant.filedreturns.FiledReturnSnapshotRepository;
import com.arktech.superaccountant.gst.mapping.TaxLedgerMapping;
import com.arktech.superaccountant.gst.mapping.TaxLedgerMappingGate;
import com.arktech.superaccountant.gst.mapping.TaxLedgerMappingService;
import com.arktech.superaccountant.masters.models.UploadJobStatus;
import com.arktech.superaccountant.review.FindingLifecycleService;
import com.arktech.superaccountant.workflow.models.Workflow;
import com.arktech.superaccountant.workflow.models.WorkflowStage;
import com.arktech.superaccountant.workflow.models.WorkflowType;
import com.arktech.superaccountant.workflow.models.BaselineImport;
import com.arktech.superaccountant.workflow.repository.BaselineImportRepository;
import com.arktech.superaccountant.workflow.repository.WorkflowRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Reconciles the net filed position of an active BASELINE row against the books inside the row
 * period, supersedes the previous discrepancy set (lifecycle carry-forward included), and keeps
 * the row's {@link BaselineImport} up to date. Runs in its own transaction so listeners that
 * fire after an outer commit still get one.
 */
@Service
public class FiledReturnReconService {

    private static final Set<String> AMENDMENT_TABLES =
            Set.of("B2BA", "CDNRA", "B2CLA", "EXPA", "CDNURA");

    @Autowired
    private WorkflowRepository workflowRepository;

    @Autowired
    private FiledReturnSnapshotRepository snapshotRepository;

    @Autowired
    private FiledReturnLineRepository lineRepository;

    @Autowired
    private FiledReturnDiscrepancyRepository discrepancyRepository;

    @Autowired
    private TaxLedgerMappingGate mappingGate;

    @Autowired
    private TaxLedgerMappingService mappingService;

    @Autowired
    private ParsedVoucherRepository parsedVoucherRepository;

    @Autowired
    private DayBookUploadJobRepository dayBookUploadJobRepository;

    @Autowired
    private BaselineImportRepository baselineImportRepository;

    @Autowired
    private FindingLifecycleService lifecycle;

    @Autowired
    private BooksGstExtractor booksExtractor;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ReconOutcome reconcile(UUID organizationId, UUID baselineWorkflowId, String actor) {
        Workflow row = workflowRepository.findById(baselineWorkflowId)
                .filter(w -> w.getOrganizationId().equals(organizationId))
                .filter(w -> w.getStage() == WorkflowStage.BASELINE)
                .filter(w -> w.isActive())
                .filter(w -> w.getType() == WorkflowType.GSTR1 || w.getType() == WorkflowType.GSTR3B)
                .orElseThrow(BaselineRowNotFoundException::new);

        if (!mappingGate.isComplete(organizationId)) {
            return outcome(row, ReconStatus.MAPPING_INCOMPLETE, 0, 0);
        }

        List<FiledReturnSnapshot> snapshots = snapshotsFor(organizationId, row);
        if (snapshots.isEmpty()) {
            return outcome(row, ReconStatus.NOT_IMPORTED, 0, 0);
        }

        DayBookUploadJob upload = booksUpload(row);
        if (upload == null) {
            return outcome(row, ReconStatus.NO_BOOKS, 0, 0);
        }

        Map<String, TaxLedgerMapping> mappings = mappingService.mappingsByKey(organizationId);
        List<ParsedVoucher> vouchers = parsedVoucherRepository.findByUploadJobId(upload.getId());
        var books = booksExtractor.extract(vouchers, mappings, row.getPeriodStart(), row.getPeriodEnd());

        List<FiledReturnDiscrepancy> fresh = buildFindings(organizationId, row, snapshots, books);
        List<FiledReturnDiscrepancy> previous =
                discrepancyRepository.findByWorkflowIdAndSupersededAtIsNull(row.getId());

        Instant now = Instant.now();
        lifecycle.reconcile(previous, fresh, now);
        for (FiledReturnDiscrepancy p : previous) {
            p.setSupersededAt(now);
        }
        discrepancyRepository.saveAll(previous);
        discrepancyRepository.saveAll(fresh);

        upsertBaselineImport(row, snapshots, fresh);

        int material = (int) fresh.stream().filter(FiledReturnDiscrepancy::isMaterial).count();
        return outcome(row, ReconStatus.RECONCILED, material, fresh.size() - material);
    }

    public List<ReconOutcome> reconcileAll(UUID organizationId, String actor) {
        List<ReconOutcome> outcomes = new ArrayList<>();
        for (Workflow row : reconRows(organizationId)) {
            try {
                outcomes.add(perRowTx().execute(
                        status -> reconcile(organizationId, row.getId(), actor)));
            } catch (RuntimeException e) {
                outcomes.add(outcome(row, ReconStatus.FAILED, 0, 0));
            }
        }
        return outcomes;
    }

    private TransactionTemplate perRowTx() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }

    private List<Workflow> reconRows(UUID organizationId) {
        return workflowRepository
                .findAllByOrganizationIdAndStageAndActiveTrue(organizationId, WorkflowStage.BASELINE)
                .stream()
                .filter(w -> w.getType() == WorkflowType.GSTR1 || w.getType() == WorkflowType.GSTR3B)
                .toList();
    }

    private List<FiledReturnSnapshot> snapshotsFor(UUID organizationId, Workflow row) {
        List<FiledReturnSnapshot> covering = snapshotRepository
                .findAllByOrganizationIdAndBaselineWorkflowIdOrderByFilingPeriodAscImportedAtAsc(
                        organizationId, row.getId()).stream()
                .filter(s -> s.getSupersededAt() == null)
                .filter(s -> !s.getCoverStart().isBefore(row.getPeriodStart())
                        && !s.getCoverEnd().isAfter(row.getPeriodEnd()))
                .toList();
        if (row.getType() == WorkflowType.GSTR3B) {
            return covering;
        }
        List<FiledReturnSnapshot> amendments = snapshotRepository
                .findAllByOrganizationId(organizationId).stream()
                .filter(s -> s.getSupersededAt() == null)
                .filter(s -> s.getFormType() == FiledFormType.GSTR1)
                .filter(s -> YearMonth.parse(s.getFilingPeriod()).atDay(1).isAfter(row.getPeriodEnd()))
                .filter(this::holdsAmendmentLines)
                .toList();
        List<FiledReturnSnapshot> all = new ArrayList<>(covering);
        all.addAll(amendments);
        all.sort(Comparator.comparing(FiledReturnSnapshot::getFilingPeriod)
                .thenComparing(FiledReturnSnapshot::getImportedAt));
        return all;
    }

    private boolean holdsAmendmentLines(FiledReturnSnapshot snapshot) {
        return lineRepository.findBySnapshotIdOrderByLineNo(snapshot.getId()).stream()
                .anyMatch(l -> l.getTableCode() != null && AMENDMENT_TABLES.contains(l.getTableCode()));
    }

    private List<FiledReturnDiscrepancy> buildFindings(UUID organizationId, Workflow row,
                                                       List<FiledReturnSnapshot> snapshots,
                                                       BooksGst books) {
        List<NetFiledPosition.SnapshotWithLines> withLines = snapshots.stream()
                .map(s -> new NetFiledPosition.SnapshotWithLines(s,
                        lineRepository.findBySnapshotIdOrderByLineNo(s.getId())))
                .toList();
        if (row.getType() == WorkflowType.GSTR3B) {
            FiledReturnSnapshot newest = snapshots.stream()
                    .max(Comparator.comparing(FiledReturnSnapshot::getImportedAt)).orElseThrow();
            return Gstr3bComparator.compare(organizationId, row.getId(), newest,
                    lineRepository.findBySnapshotIdOrderByLineNo(newest.getId()), books);
        }
        NetFiledPosition filed = NetFiledPosition.build(withLines, row.getPeriodStart(), row.getPeriodEnd());
        return Gstr1Reconciler.reconcile(organizationId, row.getId(), filed, books,
                row.getPeriodStart(), row.getPeriodEnd());
    }

    private DayBookUploadJob booksUpload(Workflow row) {
        if (row.getUploadJobId() != null) {
            return dayBookUploadJobRepository.findById(row.getUploadJobId())
                    .filter(u -> u.getStatus() == UploadJobStatus.COMPLETED)
                    .orElse(null);
        }
        List<DayBookUploadJob> completed = dayBookUploadJobRepository
                .findAllByOrganizationIdAndStatusOrderByCreatedAtDesc(
                        row.getOrganizationId(), UploadJobStatus.COMPLETED);
        for (DayBookUploadJob job : completed) {
            if (job.getCoverageFrom() != null && job.getCoverageTo() != null
                    && !job.getCoverageFrom().isAfter(row.getPeriodStart())
                    && !job.getCoverageTo().isBefore(row.getPeriodEnd())) {
                return job;
            }
        }
        return null;
    }

    private void upsertBaselineImport(Workflow row, List<FiledReturnSnapshot> snapshots,
                                      List<FiledReturnDiscrepancy> fresh) {
        String source = snapshots.stream()
                .map(s -> s.getSourceKind().name())
                .collect(Collectors.toCollection(LinkedHashSet::new))
                .stream().collect(Collectors.joining(","));
        FiledReturnSnapshot newest = snapshots.stream()
                .max(Comparator.comparing(FiledReturnSnapshot::getImportedAt)).orElseThrow();
        int material = (int) fresh.stream().filter(FiledReturnDiscrepancy::isMaterial).count();

        BaselineImport baselineImport = baselineImportRepository.findByWorkflowId(row.getId())
                .orElseGet(() -> {
                    BaselineImport bi = new BaselineImport();
                    bi.setWorkflowId(row.getId());
                    return bi;
                });
        baselineImport.setSource(source);
        baselineImport.setFiledReturnRef(newest.getId().toString());
        baselineImport.setImportedAt(newest.getImportedAt());
        baselineImport.setDiscrepancyCount(material);
        baselineImportRepository.save(baselineImport);
    }

    private ReconOutcome outcome(Workflow row, ReconStatus status, int material, int info) {
        return new ReconOutcome(row.getId(), status, material, info, Instant.now());
    }
}
