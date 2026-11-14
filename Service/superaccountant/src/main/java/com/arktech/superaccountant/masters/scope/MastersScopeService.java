package com.arktech.superaccountant.masters.scope;

import com.arktech.superaccountant.masters.models.UploadJob;
import com.arktech.superaccountant.masters.models.UploadJobStatus;
import com.arktech.superaccountant.masters.models.UploadLedgerSnapshot;
import com.arktech.superaccountant.masters.models.ValidationFinding;
import com.arktech.superaccountant.masters.repository.UploadJobRepository;
import com.arktech.superaccountant.masters.repository.UploadLedgerSnapshotRepository;
import com.arktech.superaccountant.masters.util.LedgerNames;
import com.arktech.superaccountant.workflow.models.WorkflowType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;

/**
 * Builds the latest/previous snapshot context a finding's scope depends on, and answers
 * whether one finding is in scope for one workflow type.
 */
@Service
public class MastersScopeService {

    /** latest: snapshots of the job itself; previous: the newest completed job created before it. */
    public record ScopeContext(Map<String, UploadLedgerSnapshot> latest,
                               Map<String, UploadLedgerSnapshot> previous) {
    }

    private static final EnumSet<UploadJobStatus> COMPLETED_STATUSES =
            EnumSet.of(UploadJobStatus.COMPLETED, UploadJobStatus.COMPLETED_WITH_MISMATCHES);

    @Autowired
    private UploadLedgerSnapshotRepository snapshotRepository;

    @Autowired
    private UploadJobRepository uploadJobRepository;

    public ScopeContext contextFor(UUID mastersJobId) {
        Map<String, UploadLedgerSnapshot> latest =
                byName(snapshotRepository.findByUploadJobId(mastersJobId));
        Map<String, UploadLedgerSnapshot> previous = Map.of();
        var job = uploadJobRepository.findById(mastersJobId);
        if (job.isPresent()) {
            var prev = uploadJobRepository
                    .findTopByOrganizationIdAndStatusInAndCreatedAtBeforeOrderByCreatedAtDesc(
                            job.get().getOrganizationId(), COMPLETED_STATUSES, job.get().getCreatedAt());
            if (prev.isPresent()) {
                previous = byName(snapshotRepository.findByUploadJobId(prev.get().getId()));
            }
        }
        return new ScopeContext(latest, previous);
    }

    public boolean inScope(ValidationFinding f, WorkflowType type, ScopeContext ctx) {
        return MastersScope.typesFor(f, ctx.latest(), ctx.previous()).contains(type);
    }

    private Map<String, UploadLedgerSnapshot> byName(List<UploadLedgerSnapshot> snapshots) {
        Map<String, UploadLedgerSnapshot> byName = new HashMap<>();
        for (UploadLedgerSnapshot s : snapshots) {
            byName.putIfAbsent(LedgerNames.normalize(s.getLedgerName()), s);
        }
        return byName;
    }
}
