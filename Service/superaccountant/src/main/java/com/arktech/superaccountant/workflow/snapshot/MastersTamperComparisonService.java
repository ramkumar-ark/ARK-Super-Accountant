package com.arktech.superaccountant.workflow.snapshot;

import com.arktech.superaccountant.masters.models.ResolveStatus;
import com.arktech.superaccountant.masters.models.UploadJob;
import com.arktech.superaccountant.masters.models.UploadJobStatus;
import com.arktech.superaccountant.masters.models.UploadLedgerSnapshot;
import com.arktech.superaccountant.masters.repository.UploadJobRepository;
import com.arktech.superaccountant.masters.repository.UploadLedgerSnapshotRepository;
import com.arktech.superaccountant.masters.scope.MastersScope;
import com.arktech.superaccountant.masters.util.LedgerNames;
import com.arktech.superaccountant.review.EvidenceHash;
import com.arktech.superaccountant.workflow.models.Workflow;
import com.arktech.superaccountant.workflow.models.WorkflowStage;
import com.arktech.superaccountant.workflow.repository.WorkflowRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Compares every completed masters upload against the live
 * frozen_master_attributes rows and records masters tamper changes plus
 * findings (addendum items 2 and 3). Idempotent per masters job.
 */
@Service
public class MastersTamperComparisonService {

    private static final EnumSet<ResolveStatus> TERMINAL =
            EnumSet.of(ResolveStatus.APPROVED, ResolveStatus.RESOLVED, ResolveStatus.DISCARDED);

    @Autowired
    private TamperChangeRepository tamperChangeRepository;
    @Autowired
    private TamperFindingRepository tamperFindingRepository;
    @Autowired
    private FrozenMasterAttributeRepository frozenMasterAttributeRepository;
    @Autowired
    private UploadJobRepository uploadJobRepository;
    @Autowired
    private UploadLedgerSnapshotRepository uploadLedgerSnapshotRepository;
    @Autowired
    private WorkflowRepository workflowRepository;
    @Autowired
    private AppliedChangeLookup appliedChangeLookup;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void compare(UUID mastersJobId) {
        if (tamperChangeRepository.existsByUploadJobId(mastersJobId)) {
            return;
        }
        UploadJob job = uploadJobRepository.findById(mastersJobId).orElse(null);
        if (job == null || (job.getStatus() != UploadJobStatus.COMPLETED
                && job.getStatus() != UploadJobStatus.COMPLETED_WITH_MISMATCHES)) {
            return;
        }
        UUID orgId = job.getOrganizationId();

        Map<String, AppliedPanChange> appliedPan = appliedChangeLookup
                .appliedPanChanges(mastersJobId).stream()
                .collect(Collectors.toMap(AppliedPanChange::ledgerKey, Function.identity(),
                        (a, b) -> a));

        Map<String, UploadLedgerSnapshot> latest = uploadLedgerSnapshotRepository
                .findByUploadJobId(mastersJobId).stream()
                .collect(Collectors.toMap(s -> LedgerNames.normalize(s.getLedgerName()),
                        s -> s, (a, b) -> a, LinkedHashMap::new));

        // Effective row per (ledgerKey, attribute, workflowType): greatest validFrom, null = min.
        Map<String, FrozenMasterAttribute> effective = new LinkedHashMap<>();
        for (FrozenMasterAttribute row : frozenMasterAttributeRepository
                .findByOrganizationIdAndReleasedAtIsNull(orgId)) {
            String key = row.getLedgerKey() + "|" + row.getAttribute() + "|" + row.getWorkflowType();
            FrozenMasterAttribute current = effective.get(key);
            if (current == null || greater(row.getValidFrom(), current.getValidFrom())) {
                effective.put(key, row);
            }
        }

        // Group effective rows by (ledgerKey, attribute).
        Map<String, List<FrozenMasterAttribute>> groups = new LinkedHashMap<>();
        for (FrozenMasterAttribute row : effective.values()) {
            groups.computeIfAbsent(row.getLedgerKey() + "|" + row.getAttribute(),
                    k -> new ArrayList<>()).add(row);
        }

        List<Workflow> activeWorkflows = workflowRepository
                .findAllByOrganizationIdOrderByCreatedAtDesc(orgId).stream()
                .filter(Workflow::isActive).toList();
        Map<String, TamperChange> changesByKey = new HashMap<>();
        Instant now = Instant.now();

        for (Map.Entry<String, List<FrozenMasterAttribute>> group : groups.entrySet()) {
            List<FrozenMasterAttribute> rows = group.getValue();
            String ledgerKey = rows.get(0).getLedgerKey();
            String attribute = rows.get(0).getAttribute();
            UploadLedgerSnapshot snap = latest.get(ledgerKey);
            if ("pan".equals(attribute)) {
                // An APPLIED PAN change is expected: rewrite the frozen row so it
                // no longer differs. Any other drift still follows the normal path.
                suppressAppliedPanChanges(rows, snap, appliedPan);
            }
            TamperChangeKind kind = snap == null ? TamperChangeKind.MASTER_REMOVED
                    : TamperChangeKind.MASTER_MODIFIED;
            List<FrozenMasterAttribute> differing = rows.stream()
                    .filter(row -> snap == null
                            ? !(row.getValue() == null && row.getValidFrom() != null)
                            : !Objects.equals(row.getValue(),
                                    MasterAttributes.valuesFor(row.getWorkflowType(), snap).get(attribute)))
                    .toList();
            if (differing.isEmpty()) {
                continue;
            }

            FrozenMasterAttribute first = differing.get(0);
            String displayLedger = snap != null && snap.getLedgerName() != null
                    ? snap.getLedgerName() : first.getLedgerName();
            String newValue = snap == null ? null
                    : MasterAttributes.valuesFor(first.getWorkflowType(), snap).get(attribute);

            Set<String> affected = new java.util.LinkedHashSet<>();
            differing.forEach(row -> affected.add(row.getWorkflowType().name()));
            if (snap != null && "category".equals(attribute)) {
                // Rule 5: a category move is scoped under the old and the new
                // category; the old one comes from the frozen row value itself.
                com.arktech.superaccountant.masters.models.UploadLedgerSnapshot previous =
                        new com.arktech.superaccountant.masters.models.UploadLedgerSnapshot();
                previous.setLedgerName(displayLedger);
                try {
                    previous.setCategory(
                            com.arktech.superaccountant.masters.models.LedgerCategory.valueOf(first.getValue()));
                } catch (IllegalArgumentException e) {
                    previous.setCategory(null);
                }
                previous.setTdsSection(snap.getTdsSection());
                previous.setTdsApplicable(snap.getTdsApplicable());
                MastersScope.typesForLedger(snap, previous).forEach(t -> affected.add(t.name()));
            }
            String affectedTypes = String.join(",", affected.stream().sorted().toList());

            LocalDate changeDate = differing.stream().map(FrozenMasterAttribute::getPeriodEnd)
                    .filter(Objects::nonNull).min(LocalDate::compareTo).orElse(null);
            LocalDate frozenPeriodEnd = differing.stream().map(FrozenMasterAttribute::getPeriodEnd)
                    .filter(Objects::nonNull).max(LocalDate::compareTo).orElse(null);

            List<Workflow> blockingWorkflows = blockingWorkflows(affectedTypes, activeWorkflows,
                    changeDate, frozenPeriodEnd);
            String affectedWorkflowIds = blockingWorkflows.stream().map(w -> w.getId().toString())
                    .collect(Collectors.joining(","));

            TamperChange change = new TamperChange();
            change.setOrganizationId(orgId);
            change.setUploadJobId(mastersJobId);
            change.setChangeKind(kind);
            change.setLedgerName(displayLedger);
            change.setAttributeName(attribute);
            change.setOldValue(first.getValue());
            change.setNewValue(newValue);
            change.setTouchedScopes(affectedTypes);
            change.setBlocking(true);
            change.setBlockingWorkflowIds(affectedWorkflowIds);
            change.setCreatedAt(now);
            tamperChangeRepository.save(change);

            String findingKey = kind.name() + "|" + ledgerKey + "|" + attribute;
            changesByKey.put(findingKey, change);
            upsertFinding(change, findingKey, ledgerKey, attribute, affectedTypes, changeDate,
                    frozenPeriodEnd, affectedWorkflowIds, displayLedger, first.getValue(), newValue,
                    blockingWorkflows, now);
        }

        resolveRestored(orgId, changesByKey);
    }

    /**
     * Suppresses the MASTER_MODIFIED noise a masters upload would otherwise raise
     * for a PAN it just applied through a write-back item. Only a row whose current
     * value still equals the item baseline and whose snapshot value equals the
     * item's new value is rewritten; anything else is real tamper.
     */
    private void suppressAppliedPanChanges(List<FrozenMasterAttribute> rows,
                                           UploadLedgerSnapshot snap,
                                           Map<String, AppliedPanChange> appliedPan) {
        if (snap == null) {
            return;
        }
        for (FrozenMasterAttribute row : rows) {
            AppliedPanChange change = appliedPan.get(row.getLedgerKey());
            if (change == null) {
                continue;
            }
            String snapshotValue = MasterAttributes.valuesFor(row.getWorkflowType(), snap).get("pan");
            if (canonicalEquals(row.getValue(), change.baselineValue())
                    && canonicalEquals(snapshotValue, change.newValue())) {
                row.setValue(snapshotValue);
                frozenMasterAttributeRepository.save(row);
            }
        }
    }

    /** Empty-PAN rule: trim, and blank (including "") is the same as null. */
    private static boolean canonicalEquals(String a, String b) {
        return Objects.equals(canonical(a), canonical(b));
    }

    private static String canonical(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private void upsertFinding(TamperChange change, String findingKey, String ledgerKey,
                               String attribute, String affectedTypes, LocalDate changeDate,
                               LocalDate frozenPeriodEnd, String affectedWorkflowIds,
                               String displayLedger, String oldValue, String newValue,
                               List<Workflow> blockingWorkflows, Instant now) {
        String evidenceHash = EvidenceHash.of("TAMPER_" + change.getChangeKind().name(), ledgerKey,
                attribute, oldValue, newValue);
        TamperFinding tracked = tamperFindingRepository
                .findByOrganizationIdAndResolveStatusNotIn(change.getOrganizationId(), TERMINAL).stream()
                .filter(f -> findingKey.equals(f.getFindingKey()))
                .findFirst().orElse(null);
        if (tracked != null) {
            if (!evidenceHash.equals(tracked.getEvidenceHash())) {
                updateEvidence(tracked, change, evidenceHash, affectedTypes, changeDate,
                        frozenPeriodEnd, affectedWorkflowIds, oldValue, newValue);
            }
            return;
        }
        TamperFinding finding = new TamperFinding();
        finding.setOrganizationId(change.getOrganizationId());
        finding.setTamperChangeId(change.getId());
        finding.setFindingKey(findingKey);
        finding.setEvidenceHash(evidenceHash);
        finding.setChangeKind(change.getChangeKind());
        finding.setLedgerName(displayLedger);
        finding.setAttributeName(attribute);
        finding.setOldValue(oldValue);
        finding.setNewValue(newValue);
        finding.setChangeDate(changeDate);
        finding.setFrozenPeriodEnd(frozenPeriodEnd);
        finding.setPartyLedgerName(displayLedger);
        finding.setDetectedInUploadJobId(change.getUploadJobId());
        finding.setAffectedTypes(affectedTypes);
        finding.setAffectedWorkflowIds(affectedWorkflowIds);
        finding.setMessage(message(change, displayLedger, attribute, oldValue, newValue,
                blockingWorkflows));
        finding.setCreatedAt(now);
        finding.setResolveStatus(ResolveStatus.OPEN);
        tamperFindingRepository.save(finding);
    }

    private void updateEvidence(TamperFinding tracked, TamperChange change, String evidenceHash,
                                String affectedTypes, LocalDate changeDate, LocalDate frozenPeriodEnd,
                                String affectedWorkflowIds, String oldValue, String newValue) {
        tracked.setTamperChangeId(change.getId());
        tracked.setEvidenceHash(evidenceHash);
        tracked.setOldValue(oldValue);
        tracked.setNewValue(newValue);
        tracked.setChangeDate(changeDate);
        tracked.setFrozenPeriodEnd(frozenPeriodEnd);
        tracked.setAffectedTypes(affectedTypes);
        tracked.setAffectedWorkflowIds(affectedWorkflowIds);
        tracked.setDetectedInUploadJobId(change.getUploadJobId());
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

    /** Masters back to the frozen value: open masters findings not re-produced resolve. */
    private void resolveRestored(UUID orgId, Map<String, TamperChange> changesByKey) {
        for (TamperFinding f : tamperFindingRepository
                .findByOrganizationIdAndResolveStatusNotIn(orgId, TERMINAL)) {
            if (f.getChangeKind() != TamperChangeKind.MASTER_MODIFIED
                    && f.getChangeKind() != TamperChangeKind.MASTER_REMOVED) {
                continue;
            }
            if (changesByKey.containsKey(f.getFindingKey())) {
                continue;
            }
            f.setResolveStatus(ResolveStatus.RESOLVED);
            f.setResolvedBy("system");
            f.setResolvedAt(Instant.now());
            f.setResolveNote("Masters back to the frozen value.");
            tamperFindingRepository.save(f);
        }
    }

    /** Same stage filter as the voucher comparison's blockingWorkflows, period-clamped. */
    private List<Workflow> blockingWorkflows(String affectedTypes, List<Workflow> activeWorkflows,
                                             LocalDate changeDate, LocalDate frozenPeriodEnd) {
        Set<String> types = java.util.Arrays.stream(affectedTypes.split(","))
                .map(String::trim).collect(Collectors.toSet());
        return activeWorkflows.stream()
                .filter(w -> w.getStage() == WorkflowStage.FINALIZED
                        || w.getStage() == WorkflowStage.FILED
                        || (w.getStage() == WorkflowStage.BASELINE
                                && w.getBaselineConfirmedAt() != null))
                .filter(w -> types.contains(w.getType().name()))
                .filter(w -> w.getPeriodEnd() != null && changeDate != null
                        && !w.getPeriodEnd().isBefore(changeDate)
                        && (frozenPeriodEnd == null || !w.getPeriodEnd().isAfter(frozenPeriodEnd)))
                .toList();
    }

    private String message(TamperChange change, String ledgerName, String attribute,
                           String oldValue, String newValue, List<Workflow> blockingWorkflows) {
        String where = blockingWorkflows.stream()
                .map(w -> w.getType().name() + " " + w.getPeriodKey())
                .distinct()
                .collect(Collectors.joining(", "));
        if (change.getChangeKind() == TamperChangeKind.MASTER_REMOVED) {
            return "Ledger " + ledgerName + " was removed from masters after " + where
                    + " was finalized (attribute " + attribute + ")";
        }
        return "Masters attribute " + attribute + " of " + ledgerName + " changed from "
                + orDash(oldValue) + " to " + orDash(newValue) + " after " + where + " was finalized";
    }

    private String orDash(String value) {
        return value == null || value.isBlank() ? "(blank)" : value;
    }

    private boolean greater(LocalDate a, LocalDate b) {
        if (a == null) {
            return false;
        }
        if (b == null) {
            return true;
        }
        return a.isAfter(b);
    }
}
