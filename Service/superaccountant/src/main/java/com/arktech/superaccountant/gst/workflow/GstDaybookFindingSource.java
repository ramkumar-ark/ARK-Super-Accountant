package com.arktech.superaccountant.gst.workflow;

import com.arktech.superaccountant.gst.models.GstDaybookFinding;
import com.arktech.superaccountant.gst.repository.GstDaybookFindingRepository;
import com.arktech.superaccountant.masters.models.FindingSeverity;
import com.arktech.superaccountant.masters.models.ResolveStatus;
import com.arktech.superaccountant.review.FindingApplier;
import com.arktech.superaccountant.review.NoteOnlyApplier;
import com.arktech.superaccountant.review.ReviewableFinding;
import com.arktech.superaccountant.workflow.findings.WorkflowFindingSource;
import com.arktech.superaccountant.workflow.findings.WorkflowFindingView;
import com.arktech.superaccountant.workflow.models.Workflow;
import com.arktech.superaccountant.workflow.models.WorkflowType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The GST module's workflow-facing finding source (C2(a)): a day-book finding blocks only
 * when its severity is HIGH and it is not terminal (APPROVED, RESOLVED or DISCARDED).
 * GSTR1 workflows see the outward (sales) side, GSTR3B the inward (purchase/other) side —
 * whichever the analyzers stored for the workflow. GST fixes happen in Tally and re-upload,
 * so review is note-only with no value proposals.
 */
@Component
public class GstDaybookFindingSource implements WorkflowFindingSource {

    static final Set<ResolveStatus> TERMINAL =
            EnumSet.of(ResolveStatus.APPROVED, ResolveStatus.RESOLVED, ResolveStatus.DISCARDED);

    @Autowired
    GstDaybookFindingRepository findingRepository;

    public GstDaybookFindingSource(GstDaybookFindingRepository findingRepository) {
        this.findingRepository = findingRepository;
    }

    public GstDaybookFindingSource() {
    }

    @Override
    public String sourceCode() {
        return "GST";
    }

    @Override
    public boolean appliesTo(Workflow w) {
        return w.getType() == WorkflowType.GSTR1 || w.getType() == WorkflowType.GSTR3B
                || w.getType() == WorkflowType.IFF;
    }

    @Override
    public long blockingCount(Workflow w) {
        if (!appliesTo(w)) {
            return 0;
        }
        return findingRepository.countByWorkflowIdAndSupersededAtIsNullAndSeverityAndResolveStatusNotIn(
                w.getId(), FindingSeverity.HIGH, TERMINAL);
    }

    @Override
    public List<WorkflowFindingView> list(Workflow w) {
        if (!appliesTo(w)) {
            return List.of();
        }
        return findingRepository
                .findByWorkflowIdAndSupersededAtIsNullOrderByVoucherDateAscCreatedAtAsc(w.getId())
                .stream().map(this::view)
                .toList();
    }

    @Override
    public Optional<ReviewableFinding> lockForUpdate(Workflow w, UUID findingId) {
        return findingRepository.findByIdForUpdate(findingId)
                .filter(f -> w.getId().equals(f.getWorkflowId()))
                .filter(f -> f.getSupersededAt() == null)
                .map(f -> (ReviewableFinding) f);
    }

    @Override
    public FindingApplier<ReviewableFinding> applier(Workflow w) {
        return new NoteOnlyApplier<>();
    }

    @Override
    public void save(ReviewableFinding f) {
        findingRepository.save((GstDaybookFinding) f);
    }

    @Override
    public WorkflowFindingView view(ReviewableFinding rf) {
        GstDaybookFinding f = (GstDaybookFinding) rf;
        boolean blocking = f.getSeverity() == FindingSeverity.HIGH
                && !TERMINAL.contains(f.getResolveStatus());
        return new WorkflowFindingView(
                f.getId(), sourceCode(), f.getFindingType() == null ? null : f.getFindingType().name(),
                f.getSeverity() == null ? null : f.getSeverity().name(), blocking,
                f.getMessage(), f.getExpectedValue(), f.getActualValue(),
                f.getVoucherNumber(), f.getVoucherDate(), f.getLedgerName(),
                f.getResolveStatus(), f.getResolveNote(), f.getProposedValue(),
                f.getSubmittedBy(), f.getSubmittedAt(),
                f.getReviewedBy(), f.getReviewedAt(), f.getReviewNote(),
                f.getResolvedBy(), f.getResolvedAt(), f.getCarriedFromFindingId());
    }
}
