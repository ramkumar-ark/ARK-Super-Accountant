package com.arktech.superaccountant.filedreturns.recon;

import com.arktech.superaccountant.filedreturns.FiledReturnImportService;
import com.arktech.superaccountant.filedreturns.FiledReturnLineRepository;
import com.arktech.superaccountant.filedreturns.FiledReturnSnapshot;
import com.arktech.superaccountant.filedreturns.payload.FiledReturnSnapshotSummary;
import com.arktech.superaccountant.filedreturns.recon.payload.BaselinePeriodView;
import com.arktech.superaccountant.gst.mapping.TaxLedgerMappingGate;
import com.arktech.superaccountant.login.security.services.UserDetailsImpl;
import com.arktech.superaccountant.masters.models.ResolveStatus;
import com.arktech.superaccountant.workflow.models.Workflow;
import com.arktech.superaccountant.workflow.models.WorkflowStage;
import com.arktech.superaccountant.workflow.models.WorkflowType;
import com.arktech.superaccountant.workflow.repository.BaselineImportRepository;
import com.arktech.superaccountant.workflow.repository.WorkflowRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The per-baseline-period overview of filed-return reconciliation: one row per active GSTR1 or
 * GSTR3B baseline period with its import/recon status, open discrepancy counts and the filed vs
 * books variance per tax head.
 */
@CrossOrigin(origins = "*")
@RestController
public class FiledReturnBaselineController {

    @Autowired
    private WorkflowRepository workflowRepository;

    @Autowired
    private FiledReturnImportService importService;

    @Autowired
    private FiledReturnLineRepository lineRepository;

    @Autowired
    private FiledReturnDiscrepancyRepository discrepancyRepository;

    @Autowired
    private BaselineImportRepository baselineImportRepository;

    @Autowired
    private TaxLedgerMappingGate mappingGate;

    @GetMapping("/api/v1/filed-returns/baseline")
    public ResponseEntity<?> baseline(@AuthenticationPrincipal UserDetailsImpl principal) {
        if (principal.getOrganizationId() == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "User is not linked to an organization."));
        }
        List<Workflow> rows = workflowRepository
                .findAllByOrganizationIdAndStageAndActiveTrue(principal.getOrganizationId(),
                        WorkflowStage.BASELINE)
                .stream()
                .filter(w -> w.getType() == WorkflowType.GSTR1 || w.getType() == WorkflowType.GSTR3B)
                .sorted(Comparator.comparing(Workflow::getType)
                        .thenComparing(Workflow::getPeriodStart))
                .toList();

        boolean mappingComplete = mappingGate.isComplete(principal.getOrganizationId());
        List<BaselinePeriodView> views = new ArrayList<>();
        for (Workflow row : rows) {
            views.add(view(row, mappingComplete));
        }
        return ResponseEntity.ok(views);
    }

    private BaselinePeriodView view(Workflow row, boolean mappingComplete) {
        List<FiledReturnSnapshot> snapshots = importService.currentSnapshots(
                row.getOrganizationId(), row.getId());
        List<FiledReturnSnapshotSummary> snapshotSummaries = snapshots.stream()
                .map(s -> FiledReturnSnapshotSummary.from(s,
                        lineRepository.findBySnapshotIdOrderByLineNo(s.getId()).size()))
                .toList();
        boolean imported = baselineImportRepository.findByWorkflowId(row.getId()).isPresent();

        String status;
        if (!mappingComplete) {
            status = "MAPPING_INCOMPLETE";
        } else if (snapshots.isEmpty()) {
            status = "NOT_IMPORTED";
        } else if (!imported) {
            status = "NOT_RECONCILED";
        } else {
            status = "RECONCILED";
        }

        List<FiledReturnDiscrepancy> current = discrepancyRepository
                .findByWorkflowIdAndSupersededAtIsNull(row.getId());
        int materialOpen = (int) current.stream()
                .filter(f -> f.isMaterial() && !FiledReturnDiscrepancyCounter.EXPLAINED
                        .contains(f.getResolveStatus()))
                .count();
        int materialExplained = (int) current.stream()
                .filter(f -> f.isMaterial() && FiledReturnDiscrepancyCounter.EXPLAINED
                        .contains(f.getResolveStatus()))
                .count();
        int info = (int) current.stream().filter(f -> !f.isMaterial()).count();

        Map<String, BigDecimal> filed = blankHeads();
        Map<String, BigDecimal> books = blankHeads();
        for (FiledReturnDiscrepancy f : current) {
            filed.merge("IGST", nvl(f.getFiledIgst()), BigDecimal::add);
            filed.merge("CGST", nvl(f.getFiledCgst()), BigDecimal::add);
            filed.merge("SGST", nvl(f.getFiledSgst()), BigDecimal::add);
            filed.merge("CESS", nvl(f.getFiledCess()), BigDecimal::add);
            books.merge("IGST", nvl(f.getBooksIgst()), BigDecimal::add);
            books.merge("CGST", nvl(f.getBooksCgst()), BigDecimal::add);
            books.merge("SGST", nvl(f.getBooksSgst()), BigDecimal::add);
            books.merge("CESS", nvl(f.getBooksCess()), BigDecimal::add);
        }
        Map<String, BaselinePeriodView.HeadVariance> variance = new HashMap<>();
        for (String head : filed.keySet()) {
            BigDecimal f = filed.get(head);
            BigDecimal b = books.get(head);
            variance.put(head, new BaselinePeriodView.HeadVariance(f, b, f.subtract(b)));
        }

        return new BaselinePeriodView(row.getId(), row.getType().name(), row.getPeriodKey(),
                row.getPeriodStart(), row.getPeriodEnd(),
                row.getBaselineConfirmedAt() != null, status, snapshotSummaries,
                materialOpen, materialExplained, info, variance);
    }

    private static Map<String, BigDecimal> blankHeads() {
        Map<String, BigDecimal> map = new HashMap<>();
        map.put("IGST", BigDecimal.ZERO);
        map.put("CGST", BigDecimal.ZERO);
        map.put("SGST", BigDecimal.ZERO);
        map.put("CESS", BigDecimal.ZERO);
        return map;
    }

    private static BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
