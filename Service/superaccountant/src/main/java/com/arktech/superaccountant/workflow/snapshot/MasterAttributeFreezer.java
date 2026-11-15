package com.arktech.superaccountant.workflow.snapshot;

import com.arktech.superaccountant.masters.models.UploadLedgerSnapshot;
import com.arktech.superaccountant.masters.repository.UploadLedgerSnapshotRepository;
import com.arktech.superaccountant.masters.scope.MastersScope;
import com.arktech.superaccountant.masters.scope.MastersScopeService;
import com.arktech.superaccountant.masters.util.LedgerNames;
import com.arktech.superaccountant.workflow.models.Workflow;
import com.arktech.superaccountant.workflow.models.WorkflowStage;
import com.arktech.superaccountant.workflow.models.WorkflowType;
import com.arktech.superaccountant.workflow.repository.WorkflowRepository;
import com.arktech.superaccountant.workflow.seam.MastersJobGate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Freezes the masters attributes each finalized workflow relied on (addendum
 * item 1): one live row per (organization, ledger, attribute, workflow type,
 * validFrom). Values are never rewritten here — a later difference is the
 * comparison's job. Release clamps periodEnd down to the type's current
 * frozen-through, releasing rows when nothing of the type stays frozen.
 */
@Service
public class MasterAttributeFreezer {

    @Autowired
    private FrozenMasterAttributeRepository repository;
    @Autowired
    private UploadLedgerSnapshotRepository snapshotRepository;
    @Autowired
    private MastersScopeService mastersScopeService;
    @Autowired
    private MastersJobGate mastersJobGate;
    @Autowired
    private WorkflowRepository workflowRepository;

    /** Creates or extends live rows for w.type; returns the number of rows created. */
    public int freeze(Workflow w, String actor) {
        WorkflowType type = w.getType();
        UUID mastersJobId = w.getMastersJobId() != null
                ? w.getMastersJobId()
                : mastersJobGate.latestCompletedMastersJob(w.getOrganizationId()).orElse(null);
        if (mastersJobId == null) {
            return 0;
        }
        List<UploadLedgerSnapshot> snapshots = snapshotRepository.findByUploadJobId(mastersJobId);
        if (snapshots.isEmpty()) {
            return 0;
        }
        Map<String, UploadLedgerSnapshot> byKey = snapshots.stream().collect(
                Collectors.toMap(s -> LedgerNames.normalize(s.getLedgerName()), Function.identity(),
                        (a, b) -> a));
        Map<String, UploadLedgerSnapshot> previous = mastersScopeService.contextFor(mastersJobId).previous();
        Instant now = Instant.now();
        LocalDate periodEnd = w.getPeriodEnd();
        int created = 0;

        for (Map.Entry<String, UploadLedgerSnapshot> entry : byKey.entrySet()) {
            UploadLedgerSnapshot snap = entry.getValue();
            if (!MastersScope.typesForLedger(snap, previous.get(entry.getKey())).contains(type)) {
                continue;
            }
            Map<String, String> values = MasterAttributes.valuesFor(type, snap);
            List<FrozenMasterAttribute> live = repository
                    .findByOrganizationIdAndLedgerKeyAndWorkflowTypeAndReleasedAtIsNull(
                            w.getOrganizationId(), entry.getKey(), type);
            for (Map.Entry<String, String> attribute : values.entrySet()) {
                Optional<FrozenMasterAttribute> existing = live.stream()
                        .filter(r -> r.getAttribute().equals(attribute.getKey()))
                        .filter(r -> r.getValidFrom() == null || !r.getValidFrom().isAfter(periodEnd))
                        .max(Comparator.comparing(r ->
                                r.getValidFrom() == null ? LocalDate.MIN : r.getValidFrom()));
                if (existing.isPresent()) {
                    FrozenMasterAttribute row = existing.get();
                    if (row.getPeriodEnd() == null || periodEnd.isAfter(row.getPeriodEnd())) {
                        row.setPeriodEnd(periodEnd);
                    }
                    row.setFrozenByWorkflowId(w.getId());
                    repository.save(row);
                } else {
                    FrozenMasterAttribute row = new FrozenMasterAttribute();
                    row.setOrganizationId(w.getOrganizationId());
                    row.setLedgerName(snap.getLedgerName());
                    row.setLedgerKey(entry.getKey());
                    row.setAttribute(attribute.getKey());
                    row.setValue(attribute.getValue());
                    row.setWorkflowType(type);
                    row.setPeriodEnd(periodEnd);
                    row.setFrozenByWorkflowId(w.getId());
                    row.setValidFrom(null);
                    row.setFrozenAt(now);
                    repository.save(row);
                    created++;
                }
            }
        }
        return created;
    }

    /** Clamps or releases live rows of every type after an un-finalize; returns released rows. */
    public int release(UUID organizationId) {
        Instant now = Instant.now();
        int released = 0;
        for (WorkflowType type : WorkflowType.values()) {
            List<FrozenMasterAttribute> live = repository
                    .findByOrganizationIdAndWorkflowTypeAndReleasedAtIsNull(organizationId, type);
            if (live.isEmpty()) {
                continue;
            }
            LocalDate typeFrozenThrough = typeFrozenThrough(organizationId, type);
            if (typeFrozenThrough == null) {
                for (FrozenMasterAttribute row : live) {
                    row.setReleasedAt(now);
                    repository.save(row);
                    released++;
                }
            } else {
                for (FrozenMasterAttribute row : live) {
                    if (row.getPeriodEnd() != null && row.getPeriodEnd().isAfter(typeFrozenThrough)) {
                        row.setPeriodEnd(typeFrozenThrough);
                        repository.save(row);
                    }
                }
            }
        }
        return released;
    }

    private LocalDate typeFrozenThrough(UUID organizationId, WorkflowType type) {
        return workflowRepository.findAllByOrganizationIdOrderByCreatedAtDesc(organizationId).stream()
                .filter(w -> w.getType() == type)
                .filter(Workflow::isActive)
                .filter(w -> w.getStage() == WorkflowStage.FINALIZED
                        || w.getStage() == WorkflowStage.FILED
                        || (w.getStage() == WorkflowStage.BASELINE
                                && w.getBaselineConfirmedAt() != null))
                .map(Workflow::getPeriodEnd)
                .filter(Objects::nonNull)
                .max(LocalDate::compareTo)
                .orElse(null);
    }
}
