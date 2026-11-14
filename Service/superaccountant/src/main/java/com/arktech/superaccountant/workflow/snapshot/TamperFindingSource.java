package com.arktech.superaccountant.workflow.snapshot;

import com.arktech.superaccountant.masters.models.ResolveStatus;
import com.arktech.superaccountant.review.FindingApplier;
import com.arktech.superaccountant.review.FindingReviewException;
import com.arktech.superaccountant.review.ReviewableFinding;
import com.arktech.superaccountant.workflow.findings.WorkflowFindingSource;
import com.arktech.superaccountant.workflow.findings.WorkflowFindingView;
import com.arktech.superaccountant.workflow.models.Workflow;
import com.arktech.superaccountant.workflow.models.WorkflowStage;
import com.arktech.superaccountant.workflow.models.WorkflowType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The TAMPER findings source: a tamper change blocks every open workflow of an
 * affected type whose period covers the change date, plus every workflow named
 * in the change's affected workflow list.
 */
@Component
public class TamperFindingSource implements WorkflowFindingSource {

    private static final Set<ResolveStatus> TERMINAL =
            EnumSet.of(ResolveStatus.APPROVED, ResolveStatus.RESOLVED, ResolveStatus.DISCARDED);

    @Autowired
    private TamperFindingRepository tamperFindingRepository;

    @Autowired
    private TamperAcceptApplier tamperAcceptApplier;

    @Override
    public String sourceCode() {
        return "TAMPER";
    }

    @Override
    public boolean appliesTo(Workflow w) {
        return true;
    }

    @Override
    public long blockingCount(Workflow w) {
        return relevantFindings(w).size();
    }

    @Override
    public List<WorkflowFindingView> list(Workflow w) {
        List<TamperFinding> findings = new ArrayList<>(relevantFindings(w));
        for (TamperFinding f : tamperFindingRepository.findByOrganizationId(w.getOrganizationId())) {
            if (TERMINAL.contains(f.getResolveStatus()) && !affectsWorkflow(f, w)) {
                continue;
            }
            if (!findings.contains(f) && affectsWorkflow(f, w)) {
                findings.add(f);
            }
        }
        return findings.stream().map(f -> view(f, w)).toList();
    }

    @Override
    public Optional<? extends ReviewableFinding> lockForUpdate(Workflow w, UUID findingId) {
        return tamperFindingRepository.findLockedById(findingId)
                .map(f -> relevantFindings(w).contains(f) || affectsWorkflow(f, w) ? f : null)
                .filter(java.util.Objects::nonNull);
    }

    @Override
    public FindingApplier<ReviewableFinding> applier(Workflow w) {
        return tamperAcceptApplier;
    }

    @Override
    public void save(ReviewableFinding f) {
        tamperFindingRepository.save((TamperFinding) f);
    }

    @Override
    public WorkflowFindingView view(ReviewableFinding f) {
        return view((TamperFinding) f, null);
    }

    private List<TamperFinding> relevantFindings(Workflow w) {
        return tamperFindingRepository
                .findByOrganizationIdAndResolveStatusNotIn(w.getOrganizationId(), TERMINAL)
                .stream()
                .filter(f -> affectedTypes(f).contains(w.getType())
                        && w.getStage() != WorkflowStage.FINALIZED
                        && w.getStage() != WorkflowStage.FILED
                        && f.getChangeDate() != null
                        && !w.getPeriodEnd().isBefore(f.getChangeDate()))
                .toList();
    }

    private boolean affectsWorkflow(TamperFinding f, Workflow w) {
        if (f.getAffectedWorkflowIds() == null || f.getAffectedWorkflowIds().isBlank()) {
            return false;
        }
        return Arrays.asList(f.getAffectedWorkflowIds().split(",")).contains(w.getId().toString());
    }

    private Set<WorkflowType> affectedTypes(TamperFinding f) {
        if (f.getAffectedTypes() == null || f.getAffectedTypes().isBlank()) {
            return Set.of();
        }
        return Arrays.stream(f.getAffectedTypes().split(","))
                .map(String::trim)
                .map(WorkflowType::valueOf)
                .collect(Collectors.toSet());
    }

    private WorkflowFindingView view(TamperFinding f, Workflow w) {
        boolean blocking = w != null
                && affectedTypes(f).contains(w.getType())
                && w.getStage() != WorkflowStage.FINALIZED
                && w.getStage() != WorkflowStage.FILED
                && f.getChangeDate() != null
                && !w.getPeriodEnd().isBefore(f.getChangeDate());
        return new WorkflowFindingView(f.getId(), "TAMPER", "TAMPER_" + f.getChangeKind(), "HIGH", blocking,
                f.getMessage(), f.getOldHash(), f.getNewHash(), f.getVoucherNumber(), f.getChangeDate(),
                f.getPartyLedgerName(), f.getResolveStatus(), f.getResolveNote(), f.getProposedValue(),
                f.getSubmittedBy(), f.getSubmittedAt(), f.getReviewedBy(), f.getReviewedAt(), f.getReviewNote(),
                f.getResolvedBy(), f.getResolvedAt(), f.getCarriedFromFindingId());
    }
}
