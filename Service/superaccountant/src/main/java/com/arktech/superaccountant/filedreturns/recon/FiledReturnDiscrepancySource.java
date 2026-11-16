package com.arktech.superaccountant.filedreturns.recon;

import com.arktech.superaccountant.filedreturns.FiledFormType;
import com.arktech.superaccountant.filedreturns.recon.DiscrepancyKind;
import com.arktech.superaccountant.filedreturns.recon.FiledReturnDiscrepancy;
import com.arktech.superaccountant.filedreturns.recon.FiledReturnDiscrepancyRepository;
import com.arktech.superaccountant.filedreturns.recon.HeadAmounts;
import com.arktech.superaccountant.review.FindingApplier;
import com.arktech.superaccountant.review.NoteOnlyApplier;
import com.arktech.superaccountant.review.ReviewableFinding;
import com.arktech.superaccountant.workflow.findings.WorkflowFindingSource;
import com.arktech.superaccountant.workflow.findings.WorkflowFindingView;
import com.arktech.superaccountant.workflow.models.Workflow;
import com.arktech.superaccountant.workflow.models.WorkflowStage;
import com.arktech.superaccountant.workflow.models.WorkflowType;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The filed-returns module's workflow-facing finding source: baseline discrepancies are
 * informational, so the blocking count is always zero and review is note-only.
 */
@Component
public class FiledReturnDiscrepancySource implements WorkflowFindingSource {

    private final FiledReturnDiscrepancyRepository repository;

    public FiledReturnDiscrepancySource(FiledReturnDiscrepancyRepository repository) {
        this.repository = repository;
    }

    @Override
    public String sourceCode() {
        return "FILED_RETURN";
    }

    @Override
    public boolean appliesTo(Workflow w) {
        return w.isActive() && w.getStage() == WorkflowStage.BASELINE
                && (w.getType() == WorkflowType.GSTR1 || w.getType() == WorkflowType.GSTR3B);
    }

    @Override
    public long blockingCount(Workflow w) {
        return 0;
    }

    @Override
    public List<WorkflowFindingView> list(Workflow w) {
        if (!appliesTo(w)) {
            return List.of();
        }
        return repository.findByWorkflowIdAndSupersededAtIsNull(w.getId()).stream()
                .map(this::view)
                .toList();
    }

    @Override
    public Optional<? extends com.arktech.superaccountant.review.ReviewableFinding> lockForUpdate(
            Workflow w, UUID findingId) {
        return repository.findByIdAndWorkflowIdForUpdate(findingId)
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
        repository.save((FiledReturnDiscrepancy) f);
    }

    @Override
    public WorkflowFindingView view(ReviewableFinding rf) {
        FiledReturnDiscrepancy f = (FiledReturnDiscrepancy) rf;
        String prefix = "[" + formLabel(f.getFormType()) + " " + f.getTableCode() + "] ";
        String expected = Gstr3bComparator.amountsString(new HeadAmounts(
                f.getFiledTaxable(), f.getFiledIgst(), f.getFiledCgst(), f.getFiledSgst(), f.getFiledCess()));
        String actual = Gstr3bComparator.amountsString(new HeadAmounts(
                f.getBooksTaxable(), f.getBooksIgst(), f.getBooksCgst(), f.getBooksSgst(), f.getBooksCess()));
        String voucherNumber = f.getVoucherNumber() != null ? f.getVoucherNumber() : f.getDocNumber();
        LocalDate voucherDate = f.getBooksDate() != null ? f.getBooksDate() : f.getDocDate();
        return new WorkflowFindingView(
                f.getId(), sourceCode(), f.getKind() == null ? null : f.getKind().name(),
                f.getSeverity() == null ? null : f.getSeverity().name(), false,
                prefix + (f.getMessage() == null ? "" : f.getMessage()), expected, actual,
                voucherNumber, voucherDate, f.getCounterpartyGstin(),
                f.getResolveStatus(), f.getResolveNote(), f.getProposedValue(),
                f.getSubmittedBy(), f.getSubmittedAt(),
                f.getReviewedBy(), f.getReviewedAt(), f.getReviewNote(),
                f.getResolvedBy(), f.getResolvedAt(), f.getCarriedFromFindingId());
    }

    private static String formLabel(FiledFormType formType) {
        return formType == FiledFormType.GSTR3B ? "GSTR-3B" : "GSTR-1";
    }
}
