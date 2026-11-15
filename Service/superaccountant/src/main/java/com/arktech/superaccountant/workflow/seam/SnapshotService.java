package com.arktech.superaccountant.workflow.seam;

import com.arktech.superaccountant.daybook.models.ParsedVoucher;
import com.arktech.superaccountant.daybook.models.ParsedVoucherEntry;
import com.arktech.superaccountant.daybook.models.DayBookUploadJob;
import com.arktech.superaccountant.daybook.repository.DayBookUploadJobRepository;
import com.arktech.superaccountant.daybook.repository.ParsedVoucherRepository;
import com.arktech.superaccountant.workflow.models.Workflow;
import com.arktech.superaccountant.workflow.models.WorkflowEvent;
import com.arktech.superaccountant.workflow.models.WorkflowEventKind;
import com.arktech.superaccountant.workflow.repository.WorkflowEventRepository;
import com.arktech.superaccountant.workflow.repository.WorkflowRepository;
import com.arktech.superaccountant.workflow.snapshot.FrozenVoucher;
import com.arktech.superaccountant.workflow.snapshot.FrozenVoucherRepository;
import com.arktech.superaccountant.workflow.snapshot.VoucherKeys;
import com.arktech.superaccountant.workflow.snapshot.VoucherPayload;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Seam for the snapshot worker (S): freezes the company day book when a
 * workflow finalizes, and releases rows when a workflow is un-finalized.
 */
@Service
public class SnapshotService {

    @Autowired
    private WorkflowRepository workflowRepository;
    @Autowired
    private WorkflowEventRepository workflowEventRepository;
    @Autowired
    private ParsedVoucherRepository parsedVoucherRepository;
    @Autowired
    private DayBookUploadJobRepository dayBookUploadJobRepository;
    @Autowired
    private FrozenVoucherRepository frozenVoucherRepository;
    @Autowired
    private com.arktech.superaccountant.workflow.snapshot.FrozenThrough frozenThrough;
    @Autowired
    private com.arktech.superaccountant.workflow.snapshot.MasterAttributeFreezer masterAttributeFreezer;

    @Transactional
    public void freeze(UUID workflowId, UUID organizationId, String actor) {
        Workflow w = workflowRepository.findById(workflowId).orElse(null);
        if (w != null && w.getPeriodEnd() != null) {
            masterAttributeFreezer.freeze(w, actor);
        }
        if (w == null || w.getUploadJobId() == null || w.getPeriodEnd() == null) {
            return;
        }
        DayBookUploadJob job = dayBookUploadJobRepository.findById(w.getUploadJobId()).orElse(null);
        List<ParsedVoucher> vouchers = parsedVoucherRepository
                .findByUploadJobId(w.getUploadJobId()).stream()
                .filter(v -> v.getVoucherDate() != null && !v.getVoucherDate().isAfter(w.getPeriodEnd()))
                .toList();
        Map<String, ParsedVoucher> keyed = VoucherKeys.keyAll(vouchers);
        Instant now = Instant.now();
        int rewritten = 0;
        int released = 0;

        for (Map.Entry<String, ParsedVoucher> entry : keyed.entrySet()) {
            ParsedVoucher v = entry.getValue();
            VoucherPayload payload = VoucherPayload.of(v);
            FrozenVoucher live = frozenVoucherRepository
                    .findByOrganizationIdAndVoucherKeyAndReleasedAtIsNull(organizationId, entry.getKey())
                    .orElse(null);
            if (live == null) {
                FrozenVoucher row = new FrozenVoucher();
                row.setOrganizationId(organizationId);
                row.setVoucherKey(entry.getKey());
                row.setVoucherDate(v.getVoucherDate());
                row.setVoucherTypeName(v.getVoucherTypeName());
                row.setVoucherNumber(v.getVoucherNumber());
                row.setContentHash(v.getContentHash());
                row.setPayload(payload.toJson());
                row.setFrozenByWorkflowId(workflowId);
                row.setFrozenAt(now);
                frozenVoucherRepository.save(row);
            } else if (!java.util.Objects.equals(live.getContentHash(), v.getContentHash())) {
                live.setContentHash(v.getContentHash());
                live.setPayload(payload.toJson());
                live.setVoucherDate(v.getVoucherDate());
                live.setVoucherTypeName(v.getVoucherTypeName());
                live.setVoucherNumber(v.getVoucherNumber());
                live.setFrozenByWorkflowId(workflowId);
                live.setFrozenAt(now);
                frozenVoucherRepository.save(live);
                rewritten++;
            }
        }

        if (job != null && job.getCoverageFrom() != null) {
            List<FrozenVoucher> liveRows = frozenVoucherRepository
                    .findByOrganizationIdAndReleasedAtIsNullAndVoucherDateBetween(
                            organizationId, job.getCoverageFrom(), w.getPeriodEnd());
            for (FrozenVoucher row : liveRows) {
                if (!keyed.containsKey(row.getVoucherKey())) {
                    row.setReleasedAt(now);
                    frozenVoucherRepository.save(row);
                    released++;
                }
            }
        }

        if (rewritten + released > 0) {
            WorkflowEvent event = new WorkflowEvent();
            event.setWorkflowId(workflowId);
            event.setActor(actor);
            event.setKind(WorkflowEventKind.SNAPSHOT_ABSORBED);
            event.setReason(rewritten + " rewritten, " + released + " released");
            workflowEventRepository.save(event);
        }
    }

    /**
     * Releases live frozen rows dated after the organization's current frozen-through
     * date (all rows when nothing is frozen). Rows are never deleted; amendment
     * follow-ups are untouched. Runs after the workflow is already back in PREPARE.
     */
    @Transactional
    public int release(UUID workflowId, UUID organizationId, String actor) {
        LocalDate frozenThrough = this.frozenThrough.of(organizationId).orElse(null);
        List<FrozenVoucher> liveRows = frozenThrough == null
                ? frozenVoucherRepository.findByOrganizationIdAndReleasedAtIsNull(organizationId)
                : frozenVoucherRepository.findByOrganizationIdAndReleasedAtIsNullAndVoucherDateAfter(
                        organizationId, frozenThrough);
        Instant now = Instant.now();
        for (FrozenVoucher row : liveRows) {
            row.setReleasedAt(now);
            frozenVoucherRepository.save(row);
        }
        int masterRows = masterAttributeFreezer.release(organizationId);
        return liveRows.size() + masterRows;
    }
}
