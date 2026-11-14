package com.arktech.superaccountant.workflow.findings;

import com.arktech.superaccountant.masters.models.ResolveStatus;
import com.arktech.superaccountant.masters.models.ValidationFinding;
import com.arktech.superaccountant.masters.repository.ValidationFindingRepository;
import com.arktech.superaccountant.masters.scope.MastersScopeService;
import com.arktech.superaccountant.masters.services.FindingOverrideMasterService;
import com.arktech.superaccountant.masters.services.MastersFindingApplier;
import com.arktech.superaccountant.review.FindingApplier;
import com.arktech.superaccountant.review.ReviewableFinding;
import com.arktech.superaccountant.workflow.models.Workflow;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The masters module's workflow-facing finding source. Findings block only the workflow
 * types their ledger is in scope for (see {@code MastersScope}), at every severity, until
 * they are terminal (APPROVED, RESOLVED or DISCARDED). A workflow without a bound masters
 * job lists nothing and blocks nothing.
 */
@Component
public class ScopedMastersFindingSource implements WorkflowFindingSource {

    static final Set<ResolveStatus> TERMINAL =
            EnumSet.of(ResolveStatus.APPROVED, ResolveStatus.RESOLVED, ResolveStatus.DISCARDED);

    @Autowired
    ValidationFindingRepository findingRepository;

    @Autowired
    MastersScopeService scopeService;

    @Autowired
    FindingOverrideMasterService overrideService;

    @Override
    public String sourceCode() {
        return "MASTERS";
    }

    @Override
    public boolean appliesTo(Workflow w) {
        return true;
    }

    @Override
    public long blockingCount(Workflow w) {
        return list(w).stream().filter(WorkflowFindingView::blocking).count();
    }

    @Override
    public List<WorkflowFindingView> list(Workflow w) {
        if (w.getMastersJobId() == null) {
            return List.of();
        }
        MastersScopeService.ScopeContext ctx = scopeService.contextFor(w.getMastersJobId());
        List<WorkflowFindingView> out = new ArrayList<>();
        for (ValidationFinding f : findingRepository.findByUploadJobId(w.getMastersJobId())) {
            if (scopeService.inScope(f, w.getType(), ctx)) {
                out.add(view(f));
            }
        }
        return out;
    }

    @Override
    public Optional<ReviewableFinding> lockForUpdate(Workflow w, UUID findingId) {
        if (w.getMastersJobId() == null) {
            return Optional.empty();
        }
        return findingRepository.findByIdForUpdate(findingId)
                .filter(f -> w.getMastersJobId().equals(f.getUploadJobId()))
                .filter(f -> scopeService.inScope(f, w.getType(),
                        scopeService.contextFor(w.getMastersJobId())))
                .map(f -> (ReviewableFinding) f);
    }

    @Override
    public FindingApplier<ReviewableFinding> applier(Workflow w) {
        MastersFindingApplier delegate = new MastersFindingApplier(
                overrideService, w.getOrganizationId(), null, null);
        return new FindingApplier<>() {
            @Override
            public void validate(ReviewableFinding finding, String proposedValue, String note) {
                delegate.validate((ValidationFinding) finding, proposedValue, note);
            }

            @Override
            public void apply(ReviewableFinding finding, String approver) {
                delegate.apply((ValidationFinding) finding, approver);
            }
        };
    }

    @Override
    public void save(ReviewableFinding f) {
        findingRepository.save((ValidationFinding) f);
    }

    @Override
    public WorkflowFindingView view(ReviewableFinding rf) {
        ValidationFinding f = (ValidationFinding) rf;
        String findingType = "MISMATCH_DETECTION".equals(f.getRuleCode()) && f.getMismatchType() != null
                ? f.getMismatchType().name()
                : f.getRuleCode();
        return new WorkflowFindingView(
                f.getId(), sourceCode(), findingType,
                f.getSeverity() == null ? null : f.getSeverity().name(),
                !TERMINAL.contains(f.getResolveStatus()),
                f.getMessage(), f.getExpectedValue(), f.getActualValue(),
                null, null, f.getLedgerName(),
                f.getResolveStatus(), f.getResolveNote(), f.getProposedValue(),
                f.getSubmittedBy(), f.getSubmittedAt(),
                f.getReviewedBy(), f.getReviewedAt(), f.getReviewNote(),
                f.getResolvedBy(), f.getResolvedAt(), null);
    }
}
