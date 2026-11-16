package com.arktech.superaccountant.filedreturns;

import com.arktech.superaccountant.filedreturns.payload.FiledReturnSnapshotSummary;
import com.arktech.superaccountant.filedreturns.payload.FiledReturnSnapshotView;
import com.arktech.superaccountant.filedreturns.payload.Gstr3bEntryRequest;
import com.arktech.superaccountant.login.security.services.UserDetailsImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/v1/filed-returns")
public class FiledReturnController {

    @Autowired
    private FiledReturnImportService importService;

    @Autowired
    private FiledReturnLineRepository lineRepository;

    @Autowired
    private FiledReturnSnapshotRepository snapshotRepository;

    @PostMapping("/gstr3b")
    @PreAuthorize("hasRole('ACCOUNTANT') or hasRole('OPERATOR')")
    public ResponseEntity<?> saveGstr3b(
            @AuthenticationPrincipal UserDetailsImpl principal,
            @RequestBody Gstr3bEntryRequest request) {
        UUID orgId = principal.getOrganizationId();
        if (orgId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "User is not linked to an organization."));
        }
        importService.validate3bRows(request.rows());
        List<FiledReturnLine> lines = new ArrayList<>();
        for (Gstr3bEntryRequest.Row row : request.rows()) {
            FiledReturnLine line = new FiledReturnLine();
            line.setTableCode(row.rowCode());
            line.setTaxable(nvl(row.taxable()));
            line.setIgst(nvl(row.igst()));
            line.setCgst(nvl(row.cgst()));
            line.setSgst(nvl(row.sgst()));
            line.setCess(nvl(row.cess()));
            lines.add(line);
        }
        SnapshotDraft draft = new SnapshotDraft(FiledFormType.GSTR3B, request.filingPeriod(), null,
                request.fileId() == null ? FiledSourceKind.MANUAL_ENTRY : FiledSourceKind.PORTAL_PDF,
                request.fileId(), request.parserVersion(), lines);
        FiledReturnSnapshot snapshot = importService.save(orgId, principal.getUsername(), draft);
        return ResponseEntity.status(201).body(
                FiledReturnSnapshotView.from(snapshot, lineRepository.findBySnapshotIdOrderByLineNo(snapshot.getId())));
    }

    private BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    @GetMapping("/snapshots")
    public List<FiledReturnSnapshotSummary> snapshots(
            @AuthenticationPrincipal UserDetailsImpl principal,
            @RequestParam UUID workflowId) {
        UUID orgId = principal.getOrganizationId();
        List<FiledReturnSnapshot> snapshots = snapshotRepository
                .findAllByOrganizationIdAndBaselineWorkflowIdOrderByFilingPeriodAscImportedAtAsc(orgId, workflowId);
        return snapshots.stream()
                .map(s -> FiledReturnSnapshotSummary.from(s,
                        lineRepository.findBySnapshotIdOrderByLineNo(s.getId()).size()))
                .toList();
    }

    @GetMapping("/snapshots/{id}")
    public ResponseEntity<FiledReturnSnapshotView> snapshot(@PathVariable UUID id) {
        return snapshotRepository.findById(id)
                .map(s -> ResponseEntity.ok(FiledReturnSnapshotView.from(s,
                        lineRepository.findBySnapshotIdOrderByLineNo(s.getId()))))
                .orElse(ResponseEntity.notFound().build());
    }

    @ExceptionHandler({FiledReturnValidationException.class})
    public ResponseEntity<Map<String, String>> handleValidation(FiledReturnValidationException e) {
        return ResponseEntity.status(422).body(Map.of("message", e.getMessage()));
    }

    @ExceptionHandler({FiledReturnConflictException.class})
    public ResponseEntity<Map<String, String>> handleConflict(FiledReturnConflictException e) {
        return ResponseEntity.status(409).body(Map.of("message", e.getMessage()));
    }
}
