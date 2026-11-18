package com.arktech.superaccountant.workflow.snapshot;

import com.arktech.superaccountant.daybook.models.DayBookUploadJob;
import com.arktech.superaccountant.daybook.models.ParsedVoucher;
import com.arktech.superaccountant.daybook.repository.DayBookUploadJobRepository;
import com.arktech.superaccountant.daybook.repository.ParsedVoucherRepository;
import com.arktech.superaccountant.masters.models.ResolveStatus;
import com.arktech.superaccountant.masters.models.UploadJobStatus;
import com.arktech.superaccountant.masters.models.UploadLedgerSnapshot;
import com.arktech.superaccountant.masters.repository.UploadJobRepository;
import com.arktech.superaccountant.masters.repository.UploadLedgerSnapshotRepository;
import com.arktech.superaccountant.review.EvidenceHash;
import com.arktech.superaccountant.workflow.models.Workflow;
import com.arktech.superaccountant.workflow.models.WorkflowStage;
import com.arktech.superaccountant.workflow.models.WorkflowType;
import com.arktech.superaccountant.workflow.repository.WorkflowRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Compares every completed day-book upload against the live frozen_vouchers
 * rows and records tamper changes plus blocking findings.
 */
@Service
public class TamperComparisonService {

    @Autowired
    private TamperChangeRepository tamperChangeRepository;
    @Autowired
    private TamperFindingRepository tamperFindingRepository;
    @Autowired
    private FrozenVoucherRepository frozenVoucherRepository;
    @Autowired
    private DayBookUploadJobRepository dayBookUploadJobRepository;
    @Autowired
    private ParsedVoucherRepository parsedVoucherRepository;
    @Autowired
    private UploadJobRepository uploadJobRepository;
    @Autowired
    private UploadLedgerSnapshotRepository uploadLedgerSnapshotRepository;
    @Autowired
    private WorkflowRepository workflowRepository;
    @Autowired
    private FrozenThrough frozenThrough;
    @Autowired
    private VoucherScopeClassifier scopeClassifier;
    @Autowired
    private List<RetroFlagSource> retroFlagSources = List.of();

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void compare(UUID uploadJobId) {
        if (tamperChangeRepository.existsByUploadJobId(uploadJobId)) {
            return;
        }
        DayBookUploadJob job = dayBookUploadJobRepository.findById(uploadJobId).orElse(null);
        if (job == null || (job.getStatus() != UploadJobStatus.COMPLETED
                && job.getStatus() != UploadJobStatus.COMPLETED_WITH_MISMATCHES)) {
            return;
        }
        UUID orgId = job.getOrganizationId();

        Map<String, UploadLedgerSnapshot> ledgersByName = ledgersByName(orgId);
        Map<String, FrozenVoucher> frozenByKey = new HashMap<>();
        for (FrozenVoucher row : frozenVoucherRepository.findByOrganizationIdAndReleasedAtIsNull(orgId)) {
            frozenByKey.put(row.getVoucherKey(), row);
        }
        Map<String, ParsedVoucher> uploadedByKey = VoucherKeys.keyAll(
                parsedVoucherRepository.findByUploadJobId(uploadJobId).stream()
                        .filter(v -> v.getVoucherDate() != null)
                        .toList());
        Optional<LocalDate> throughDate = frozenThrough.of(orgId);
        List<Workflow> activeWorkflows = workflowRepository.findAllByOrganizationIdOrderByCreatedAtDesc(orgId)
                .stream().filter(Workflow::isActive).toList();
        Map<String, TamperChange> changesByKey = new HashMap<>();

        for (Map.Entry<String, FrozenVoucher> e : frozenByKey.entrySet()) {
            FrozenVoucher frozen = e.getValue();
            ParsedVoucher uploaded = uploadedByKey.get(e.getKey());
            TamperChange change;
            if (uploaded != null) {
                if (uploaded.getContentHash() != null && uploaded.getContentHash().equals(frozen.getContentHash())) {
                    continue;
                }
                change = change(orgId, uploadJobId, e.getKey(), TamperChangeKind.MODIFIED,
                        frozen.getContentHash(), uploaded.getContentHash(),
                        frozen.getVoucherDate(), uploaded.getVoucherDate(),
                        uploaded.getVoucherTypeName(), uploaded.getVoucherNumber(),
                        uploaded.getPartyLedgerName());
                change.setTouchedScopes(touched(
                        VoucherPayload.fromJson(frozen.getPayload()), VoucherPayload.of(uploaded), ledgersByName));
            } else {
                LocalDate date = frozen.getVoucherDate();
                if (date == null || job.getCoverageFrom() == null || job.getCoverageTo() == null
                        || date.isBefore(job.getCoverageFrom()) || date.isAfter(job.getCoverageTo())) {
                    continue;
                }
                change = change(orgId, uploadJobId, e.getKey(), TamperChangeKind.DELETED,
                        frozen.getContentHash(), null, date, null,
                        frozen.getVoucherTypeName(), frozen.getVoucherNumber(), null);
                VoucherPayload oldPayload = VoucherPayload.fromJson(frozen.getPayload());
                change.setPartyLedgerName(oldPayload == null ? null : oldPayload.party());
                change.setTouchedScopes(touched(oldPayload, null, ledgersByName));
            }
            saveChangeAndFinding(change, activeWorkflows, orgId, job, changesByKey);
        }

        for (Map.Entry<String, ParsedVoucher> e : uploadedByKey.entrySet()) {
            if (frozenByKey.containsKey(e.getKey())) {
                continue;
            }
            ParsedVoucher uploaded = e.getValue();
            LocalDate date = uploaded.getVoucherDate();
            if (throughDate.isEmpty() || date.isAfter(throughDate.get())) {
                continue;
            }
            TamperChange change = change(orgId, uploadJobId, e.getKey(), TamperChangeKind.INSERTED,
                    null, uploaded.getContentHash(), null, date,
                    uploaded.getVoucherTypeName(), uploaded.getVoucherNumber(), uploaded.getPartyLedgerName());
            change.setTouchedScopes(touched(null, VoucherPayload.of(uploaded), ledgersByName));
            saveChangeAndFinding(change, activeWorkflows, orgId, job, changesByKey);
        }

        for (RetroFlagSource source : retroFlagSources) {
            for (RetroFlagSource.Flag flag : source.retroFlags(orgId, uploadJobId)) {
                ParsedVoucher uploaded = uploadedByKey.get(flag.voucherKey());
                if (uploaded == null) {
                    continue;
                }
                TamperChange change = change(orgId, uploadJobId, flag.voucherKey(),
                        TamperChangeKind.RETRO_FLAGGED, null, null,
                        flag.voucherDate(), flag.voucherDate(),
                        uploaded.getVoucherTypeName(), uploaded.getVoucherNumber(),
                        flag.deducteeLedger());
                change.setTouchedScopes("TDS_26Q,TDS_PAYMENT");
                change.setRetroSection(flag.section());
                change.setRetroShortfall(flag.shortfall());
                saveChangeAndFinding(change, activeWorkflows, orgId, job, changesByKey);
            }
        }

        reconcile(orgId, job, changesByKey);
    }

    /**
     * Reconciles open findings against this upload: a finding whose change no
     * longer appears (the voucher is back to its frozen form) and whose change
     * date falls inside this upload's coverage is auto-resolved; one whose
     * evidence moved is reset to OPEN with the new evidence.
     */
    private void reconcile(UUID orgId, DayBookUploadJob job, Map<String, TamperChange> changesByKey) {
        for (TamperFinding f : tamperFindingRepository.findByOrganizationIdAndResolveStatusNotIn(orgId,
                EnumSet.of(ResolveStatus.APPROVED, ResolveStatus.RESOLVED, ResolveStatus.DISCARDED))) {
            if (changesByKey.containsKey(f.getFindingKey())) {
                continue;
            }
            LocalDate cd = f.getChangeDate();
            if (cd == null || job.getCoverageFrom() == null || job.getCoverageTo() == null
                    || cd.isBefore(job.getCoverageFrom()) || cd.isAfter(job.getCoverageTo())) {
                continue;
            }
            f.setResolveStatus(ResolveStatus.RESOLVED);
            f.setResolvedBy("system");
            f.setResolvedAt(java.time.Instant.now());
            f.setResolveNote("Restored: no longer found in upload " + job.getId());
            tamperFindingRepository.save(f);
        }
    }

    private Map<String, UploadLedgerSnapshot> ledgersByName(UUID orgId) {
        return uploadJobRepository
                .findTopByOrganizationIdAndStatusInOrderByCreatedAtDesc(
                        orgId, List.of(UploadJobStatus.COMPLETED, UploadJobStatus.COMPLETED_WITH_MISMATCHES))
                .map(mastersJob -> uploadLedgerSnapshotRepository.findByUploadJobId(mastersJob.getId()))
                .orElse(List.of())
                .stream()
                .collect(Collectors.toMap(UploadLedgerSnapshot::getLedgerName, s -> s, (a, b) -> a));
    }

    private TamperChange change(UUID orgId, UUID uploadJobId, String voucherKey, TamperChangeKind kind,
                                String oldHash, String newHash, LocalDate oldDate, LocalDate newDate,
                                String voucherTypeName, String voucherNumber, String partyLedgerName) {
        TamperChange change = new TamperChange();
        change.setOrganizationId(orgId);
        change.setUploadJobId(uploadJobId);
        change.setVoucherKey(voucherKey);
        change.setChangeKind(kind);
        change.setOldHash(oldHash);
        change.setNewHash(newHash);
        change.setOldDate(oldDate);
        change.setNewDate(newDate);
        change.setVoucherTypeName(voucherTypeName);
        change.setVoucherNumber(voucherNumber);
        change.setPartyLedgerName(partyLedgerName);
        change.setCreatedAt(java.time.Instant.now());
        return change;
    }

    private String touched(VoucherPayload oldPayload, VoucherPayload newPayload,
                           Map<String, UploadLedgerSnapshot> ledgersByName) {
        Set<WorkflowType> scopes = new HashSet<>();
        if (oldPayload != null) {
            scopes.addAll(scopeClassifier.scopes(oldPayload, ledgersByName));
        }
        if (newPayload != null) {
            scopes.addAll(scopeClassifier.scopes(newPayload, ledgersByName));
        }
        return VoucherScopeClassifier.touchedScopesText(scopes);
    }

    private static final Set<ResolveStatus> TERMINAL_STATUSES =
            EnumSet.of(ResolveStatus.APPROVED, ResolveStatus.RESOLVED, ResolveStatus.DISCARDED);
    private static final Set<ResolveStatus> HUMAN_DECIDED_STATUSES =
            EnumSet.of(ResolveStatus.APPROVED, ResolveStatus.DISCARDED);

    void saveChangeAndFinding(TamperChange change, List<Workflow> activeWorkflows, UUID orgId,
                              DayBookUploadJob job, Map<String, TamperChange> changesByKey) {
        List<Workflow> blockingWorkflows = blockingWorkflows(change, activeWorkflows);
        if (change.getChangeKind() == TamperChangeKind.RETRO_FLAGGED && blockingWorkflows.isEmpty()) {
            return;
        }
        String findingKey = change.getChangeKind().name() + "|" + change.getVoucherKey();
        boolean retro = change.getChangeKind() == TamperChangeKind.RETRO_FLAGGED;
        String evidenceHash = EvidenceHash.of("TAMPER_" + change.getChangeKind(), change.getVoucherKey(),
                retro ? change.getRetroSection() : change.getOldHash(),
                retro ? (change.getRetroShortfall() == null ? null : change.getRetroShortfall().toPlainString())
                        : change.getNewHash(),
                change.getNewDate() == null ? null : change.getNewDate().toString());
        if (retro && tamperFindingRepository.findByOrganizationId(orgId).stream()
                .anyMatch(f -> findingKey.equals(f.getFindingKey())
                        && evidenceHash.equals(f.getEvidenceHash())
                        && HUMAN_DECIDED_STATUSES.contains(f.getResolveStatus()))) {
            return;
        }
        change.setBlocking(!blockingWorkflows.isEmpty());
        change.setBlockingWorkflowIds(blockingWorkflows.stream()
                .map(w -> w.getId().toString()).collect(Collectors.joining(",")));
        tamperChangeRepository.save(change);

        if (!change.isBlocking()) {
            return;
        }
        changesByKey.put(findingKey, change);
        List<TamperFinding> existing = tamperFindingRepository
                .findByOrganizationIdAndResolveStatusNotIn(orgId,
                        TERMINAL_STATUSES);
        TamperFinding tracked = existing.stream()
                .filter(f -> findingKey.equals(f.getFindingKey()))
                .findFirst()
                .orElse(null);
        if (tracked != null) {
            if (!evidenceHash.equals(tracked.getEvidenceHash())) {
                updateEvidence(tracked, change, evidenceHash);
            }
            return;
        }

        TamperFinding finding = new TamperFinding();
        finding.setOrganizationId(orgId);
        finding.setTamperChangeId(change.getId());
        finding.setFindingKey(findingKey);
        finding.setEvidenceHash(evidenceHash);
        finding.setChangeKind(change.getChangeKind());
        finding.setVoucherKey(change.getVoucherKey());
        finding.setVoucherTypeName(change.getVoucherTypeName());
        finding.setVoucherNumber(change.getVoucherNumber());
        finding.setPartyLedgerName(change.getPartyLedgerName());
        finding.setOldDate(change.getOldDate());
        finding.setNewDate(change.getNewDate());
        finding.setChangeDate(changeDate(change.getOldDate(), change.getNewDate()));
        finding.setOldHash(change.getOldHash());
        finding.setNewHash(change.getNewHash());
        finding.setDetectedInUploadJobId(change.getUploadJobId());
        finding.setAffectedTypes(affectedTypes(change, activeWorkflows));
        finding.setAffectedWorkflowIds(change.getBlockingWorkflowIds());
        finding.setMessage(message(change, blockingWorkflows));
        finding.setCreatedAt(java.time.Instant.now());
        finding.setResolveStatus(ResolveStatus.OPEN);
        tamperFindingRepository.save(finding);
    }

    private void updateEvidence(TamperFinding tracked, TamperChange change, String evidenceHash) {
        tracked.setTamperChangeId(change.getId());
        tracked.setOldHash(change.getOldHash());
        tracked.setNewHash(change.getNewHash());
        tracked.setOldDate(change.getOldDate());
        tracked.setNewDate(change.getNewDate());
        tracked.setChangeDate(changeDate(change.getOldDate(), change.getNewDate()));
        tracked.setVoucherTypeName(change.getVoucherTypeName());
        tracked.setVoucherNumber(change.getVoucherNumber());
        tracked.setPartyLedgerName(change.getPartyLedgerName());
        tracked.setDetectedInUploadJobId(change.getUploadJobId());
        tracked.setEvidenceHash(evidenceHash);
        tracked.setResolveStatus(ResolveStatus.OPEN);
        tracked.setProposedValue(null);
        tracked.setSubmittedBy(null);
        tracked.setSubmittedAt(null);
        tracked.setReviewedBy(null);
        tracked.setReviewedAt(null);
        tracked.setReviewNote(null);
        tracked.setCarriedFromFindingId(null);
        tamperFindingRepository.save(tracked);
    }

    private List<Workflow> blockingWorkflows(TamperChange change, List<Workflow> activeWorkflows) {
        Set<WorkflowType> touched = touchedTypes(change);
        return activeWorkflows.stream()
                .filter(w -> w.getStage() == WorkflowStage.FINALIZED || w.getStage() == WorkflowStage.FILED
                        || (w.getStage() == WorkflowStage.BASELINE && w.getBaselineConfirmedAt() != null))
                .filter(w -> touched.contains(w.getType()))
                .filter(w -> covers(w, change.getOldDate()) || covers(w, change.getNewDate()))
                .toList();
    }

    private String affectedTypes(TamperChange change, List<Workflow> activeWorkflows) {
        Set<WorkflowType> touched = touchedTypes(change);
        return activeWorkflows.stream()
                .filter(w -> touched.contains(w.getType()))
                .filter(w -> covers(w, change.getOldDate()) || covers(w, change.getNewDate()))
                .map(Workflow::getType)
                .distinct()
                .map(Enum::name)
                .sorted()
                .collect(Collectors.joining(","));
    }

    private Set<WorkflowType> touchedTypes(TamperChange change) {
        Set<WorkflowType> touched = new HashSet<>();
        if (change.getTouchedScopes() != null && !change.getTouchedScopes().isBlank()) {
            for (String name : change.getTouchedScopes().split(",")) {
                touched.add(WorkflowType.valueOf(name.trim()));
            }
        }
        return touched;
    }

    private boolean covers(Workflow w, LocalDate date) {
        return date != null && !date.isBefore(w.getPeriodStart()) && !date.isAfter(w.getPeriodEnd());
    }

    private LocalDate changeDate(LocalDate oldDate, LocalDate newDate) {
        if (oldDate == null) {
            return newDate;
        }
        if (newDate == null) {
            return oldDate;
        }
        return oldDate.isBefore(newDate) ? oldDate : newDate;
    }

    private String message(TamperChange change, List<Workflow> blockingWorkflows) {
        String where = blockingWorkflows.stream()
                .map(w -> w.getType().name() + " " + w.getPeriodKey())
                .distinct()
                .collect(Collectors.joining(", "));
        LocalDate date = changeDate(change.getOldDate(), change.getNewDate());
        if (change.getChangeKind() == TamperChangeKind.RETRO_FLAGGED) {
            return change.getVoucherTypeName() + " " + change.getVoucherNumber() + " dated " + date
                    + " is retro-flagged under section " + change.getRetroSection()
                    + " for deductee " + change.getPartyLedgerName() + ": TDS shortfall "
                    + change.getRetroShortfall() + " now due because " + where
                    + " crossed the annual threshold";
        }
        String verb = switch (change.getChangeKind()) {
            case MODIFIED -> "modified";
            case DELETED -> "deleted";
            case INSERTED -> "inserted";
            case RETRO_FLAGGED, MASTER_MODIFIED, MASTER_REMOVED ->
                    throw new IllegalStateException("unexpected change kind in voucher comparison");
        };
        return change.getVoucherTypeName() + " " + change.getVoucherNumber() + " dated " + date
                + " was " + verb + " after " + where + " was finalized";
    }
}
