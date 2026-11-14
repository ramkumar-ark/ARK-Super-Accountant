package com.arktech.superaccountant.workflow.controllers;

import com.arktech.superaccountant.login.security.services.UserDetailsImpl;
import com.arktech.superaccountant.masters.services.FindingOverrideException;
import com.arktech.superaccountant.review.FindingReviewException;
import com.arktech.superaccountant.review.FindingReviewService;
import com.arktech.superaccountant.review.ReviewActor;
import com.arktech.superaccountant.review.ReviewableFinding;
import com.arktech.superaccountant.workflow.models.Workflow;
import com.arktech.superaccountant.workflow.payload.request.WorkflowBulkActionRequest;
import com.arktech.superaccountant.workflow.payload.request.WorkflowFindingActionRequest;
import com.arktech.superaccountant.workflow.findings.WorkflowBulkActionException;
import com.arktech.superaccountant.workflow.findings.WorkflowFindingSource;
import com.arktech.superaccountant.workflow.findings.WorkflowFindingView;
import com.arktech.superaccountant.workflow.repository.WorkflowRepository;
import com.arktech.superaccountant.workflow.seam.WorkflowGate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Workflow-facing findings API. Every module's findings are surfaced through its
 * {@link WorkflowFindingSource}; role gating stays inside the shared review service, so
 * AUDITOR_CA and OWNER can read but get 403 on actions.
 */
@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/v1/workflows")
public class WorkflowFindingsController {

    @Autowired
    private WorkflowRepository workflowRepository;

    @Autowired
    private WorkflowGate workflowGate;

    @Autowired
    private FindingReviewService findingReviewService;

    @Autowired
    private org.springframework.beans.factory.ObjectProvider<WorkflowFindingSource> sourcesProvider;

    private List<WorkflowFindingSource> sources() {
        return sourcesProvider.stream().toList();
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{id}/findings")
    public ResponseEntity<?> list(@PathVariable UUID id,
                                  @AuthenticationPrincipal UserDetailsImpl principal) {
        Optional<Workflow> workflow = loadForOrg(id, principal);
        if (workflow.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Workflow not found"));
        }
        Workflow w = workflow.get();
        List<WorkflowFindingView> findings = new ArrayList<>();
        for (WorkflowFindingSource source : sources()) {
            findings.addAll(source.list(w));
        }
        findings.sort(Comparator.comparing((WorkflowFindingView f) -> !f.blocking())
                .thenComparing(f -> f.voucherDate() == null ? java.time.LocalDate.MAX : f.voucherDate())
                .thenComparing(f -> f.id() == null ? new UUID(0, 0) : f.id()));
        return ResponseEntity.ok(Map.of(
                "findings", findings,
                "blockingCount", workflowGate.blockingFindingCount(id)));
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/{id}/findings/{fid}/action")
    @Transactional
    public ResponseEntity<?> action(@PathVariable UUID id,
                                    @PathVariable UUID fid,
                                    @RequestBody WorkflowFindingActionRequest request,
                                    @AuthenticationPrincipal UserDetailsImpl principal) {
        Optional<Workflow> workflow = loadForOrg(id, principal);
        if (workflow.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Workflow not found"));
        }
        Workflow w = workflow.get();
        ReviewableFinding finding = null;
        WorkflowFindingView view = null;
        WorkflowFindingSource owning = null;
        for (WorkflowFindingSource source : sources()) {
            Optional<? extends ReviewableFinding> locked = source.lockForUpdate(w, fid);
            if (locked.isPresent()) {
                finding = locked.get();
                owning = source;
                break;
            }
        }
        if (finding == null) {
            return ResponseEntity.status(404).body(Map.of("error", "Finding not found"));
        }
        try {
            findingReviewService.act(finding, request.getAction(), request.getValue(), request.getNote(),
                    ReviewActor.of(principal), Instant.now(), owning.applier(w));
        } catch (FindingReviewException e) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return ResponseEntity.status(e.getStatus()).body(Map.of("error", e.getMessage()));
        } catch (FindingOverrideException e) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return ResponseEntity.status(e.getStatus()).body(Map.of("error", e.getMessage()));
        }
        owning.save(finding);
        return ResponseEntity.ok(owning.view(finding));
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/{id}/findings/bulk-action")
    @Transactional
    public ResponseEntity<?> bulkAction(@PathVariable UUID id,
                                        @RequestBody WorkflowBulkActionRequest request,
                                        @AuthenticationPrincipal UserDetailsImpl principal) {
        Optional<Workflow> workflow = loadForOrg(id, principal);
        if (workflow.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Workflow not found"));
        }
        Workflow w = workflow.get();
        String action = request.getAction();
        if (!"ACKNOWLEDGE".equals(action) && !"APPROVE".equals(action)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Unknown bulk action. Must be ACKNOWLEDGE or APPROVE."));
        }
        if (request.getFindingIds() == null || request.getFindingIds().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Select findings to resolve."));
        }

        ReviewActor actor = ReviewActor.of(principal);
        List<WorkflowBulkActionException.Failure> failures = new ArrayList<>();
        List<ReviewableFinding> processed = new ArrayList<>();
        record LockedAction(WorkflowFindingSource source, ReviewableFinding finding) {
        }
        List<LockedAction> locked = new ArrayList<>();
        for (UUID fid : new LinkedHashSet<>(request.getFindingIds())) {
            ReviewableFinding finding = null;
            WorkflowFindingSource owning = null;
            for (WorkflowFindingSource source : sources()) {
                Optional<? extends ReviewableFinding> found = source.lockForUpdate(w, fid);
                if (found.isPresent()) {
                    finding = found.get();
                    owning = source;
                    break;
                }
            }
            if (finding == null) {
                failures.add(new WorkflowBulkActionException.Failure(fid, "Finding not found."));
                continue;
            }
            try {
                findingReviewService.act(finding, action, null, request.getNote(), actor, Instant.now(),
                        owning.applier(w));
                locked.add(new LockedAction(owning, finding));
                processed.add(finding);
            } catch (FindingReviewException | FindingOverrideException e) {
                failures.add(new WorkflowBulkActionException.Failure(fid, e.getMessage()));
            }
        }
        // All-or-nothing: nothing is saved unless every finding resolved.
        if (failures.isEmpty()) {
            for (LockedAction l : locked) {
                l.source().save(l.finding());
            }
        }
        if (!failures.isEmpty()) {
            throw new WorkflowBulkActionException(failures.size() + " finding"
                    + (failures.size() == 1 ? "" : "s") + " cannot be resolved; nothing was changed.", failures);
        }
        return ResponseEntity.ok(Map.of("processed", processed.size(), "action", action));
    }

    @ExceptionHandler(WorkflowBulkActionException.class)
    public ResponseEntity<?> handleBulkFailure(WorkflowBulkActionException e) {
        return ResponseEntity.status(409).body(Map.of("error", e.getMessage(), "failures", e.getFailures()));
    }

    private Optional<Workflow> loadForOrg(UUID id, UserDetailsImpl principal) {
        return workflowRepository.findById(id)
                .filter(w -> principal.getOrganizationId() != null
                        && w.getOrganizationId().equals(principal.getOrganizationId()));
    }
}
