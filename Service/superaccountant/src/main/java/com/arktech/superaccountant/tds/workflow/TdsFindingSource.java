package com.arktech.superaccountant.tds.workflow;

import com.arktech.superaccountant.masters.models.ResolveStatus;
import com.arktech.superaccountant.masters.pan.PanOverrideGateway;
import com.arktech.superaccountant.tds.models.TdsFinding;
import com.arktech.superaccountant.tds.repository.TdsFindingRepository;
import com.arktech.superaccountant.workflow.findings.WorkflowFindingSource;
import com.arktech.superaccountant.workflow.findings.WorkflowFindingView;
import com.arktech.superaccountant.workflow.models.Workflow;
import com.arktech.superaccountant.workflow.models.WorkflowType;
import com.arktech.superaccountant.review.FindingApplier;
import com.arktech.superaccountant.review.ReviewableFinding;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

@Component
public class TdsFindingSource implements WorkflowFindingSource {

    private static final Set<ResolveStatus> TERMINAL =
            EnumSet.of(ResolveStatus.APPROVED, ResolveStatus.RESOLVED, ResolveStatus.DISCARDED);

    private final TdsFindingRepository findingRepository;
    private final PanOverrideGateway panOverrides;

    public TdsFindingSource(TdsFindingRepository findingRepository,
                            PanOverrideGateway panOverrides) {
        this.findingRepository = findingRepository;
        this.panOverrides = panOverrides;
    }

    @Override
    public String sourceCode() {
        return "TDS";
    }

    @Override
    public boolean appliesTo(Workflow w) {
        return w.getType() == WorkflowType.TDS_PAYMENT;
    }

    @Override
    public List<WorkflowFindingView> list(Workflow w) {
        List<TdsFinding> current = findingRepository
                .findByWorkflowIdAndSupersededAtIsNullOrderByVoucherDateAsc(w.getId());
        List<TdsFinding> autoResolved = findingRepository
                .findAutoResolvedByReupload(w.getId(), w.getSupersedesWorkflowId());
        return Stream.concat(current.stream(), autoResolved.stream()).map(this::view).toList();
    }

    @Override
    public long blockingCount(Workflow w) {
        return findingRepository.countByWorkflowIdAndSupersededAtIsNullAndResolveStatusNotIn(
                w.getId(), TERMINAL);
    }

    @Override
    public Optional<? extends ReviewableFinding> lockForUpdate(Workflow w, UUID findingId) {
        return findingRepository.findByIdForUpdate(findingId)
                .filter(f -> w.getId().equals(f.getWorkflowId()))
                .filter(f -> f.getSupersededAt() == null);
    }

    @Override
    public FindingApplier<ReviewableFinding> applier(Workflow w) {
        return new TdsFindingApplier(panOverrides, w.getOrganizationId());
    }

    @Override
    public void save(ReviewableFinding f) {
        findingRepository.save((TdsFinding) f);
    }

    @Override
    public WorkflowFindingView view(ReviewableFinding rf) {
        TdsFinding f = (TdsFinding) rf;
        return new WorkflowFindingView(
                f.getId(),
                "TDS",
                f.getFindingType().name(),
                f.getSeverity().name(),
                !TERMINAL.contains(f.getResolveStatus()),
                f.getMessage(),
                plain(f.getExpectedAmount()),
                plain(f.getActualAmount()),
                f.getVoucherNumber(),
                f.getVoucherDate(),
                f.getPartyLedgerName(),
                f.getResolveStatus(),
                f.getResolveNote(),
                f.getProposedValue(),
                f.getSubmittedBy(),
                f.getSubmittedAt(),
                f.getReviewedBy(),
                f.getReviewedAt(),
                f.getReviewNote(),
                f.getResolvedBy(),
                f.getResolvedAt(),
                f.getCarriedFromFindingId());
    }

    private static String plain(BigDecimal v) {
        return v == null ? null : v.toPlainString();
    }
}
