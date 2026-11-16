package com.arktech.superaccountant.filedreturns;

import com.arktech.superaccountant.filedreturns.payload.FiledReturnSnapshotSummary;
import com.arktech.superaccountant.filedreturns.payload.FiledReturnSnapshotView;
import com.arktech.superaccountant.filedreturns.payload.Gstr3bEntryRequest;
import com.arktech.superaccountant.filedreturns.parse.Gstr1JsonParser;
import com.arktech.superaccountant.filedreturns.parse.Gstr3bPdfParser;
import com.arktech.superaccountant.filedreturns.parse.Gstr1Parsed;
import com.arktech.superaccountant.filedreturns.parse.Gstr3bParsed;
import com.arktech.superaccountant.filedreturns.payload.Gstr3bParseResponse;
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
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
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
    private FiledReturnFileService fileService;

    @Autowired
    private FiledReturnFileRepository fileRepository;

    @Autowired
    private com.arktech.superaccountant.filedreturns.parse.Gstr1JsonParser gstr1JsonParser;

    @Autowired
    private FiledReturnLineRepository lineRepository;

    @Autowired
    private FiledReturnSnapshotRepository snapshotRepository;

    @PostMapping("/gstr1/json")
    @PreAuthorize("hasRole('ACCOUNTANT') or hasRole('OPERATOR')")
    public ResponseEntity<?> uploadGstr1Json(
            @AuthenticationPrincipal UserDetailsImpl principal,
            @RequestParam("file") MultipartFile file,
            @RequestParam("form") String form) throws IOException {
        UUID orgId = principal.getOrganizationId();
        if (orgId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "User is not linked to an organization."));
        }
        FiledFormType formType;
        try {
            formType = FiledFormType.valueOf(form);
        } catch (Exception e) {
            throw new FiledReturnValidationException("form must be GSTR1 or IFF");
        }
        if (formType != FiledFormType.GSTR1 && formType != FiledFormType.IFF) {
            throw new FiledReturnValidationException("form must be GSTR1 or IFF");
        }
        FiledReturnFile stored = fileService.store(orgId, principal.getUsername(), file);
        Gstr1Parsed parsed = gstr1JsonParser.parse(stored.getContent());
        SnapshotDraft draft = new SnapshotDraft(formType, parsed.filingPeriod(), parsed.gstin(),
                FiledSourceKind.PORTAL_JSON, stored.getId(), Gstr1JsonParser.PARSER_VERSION, parsed.lines());
        FiledReturnSnapshot snapshot = importService.save(orgId, principal.getUsername(), draft);
        return ResponseEntity.status(201).body(
                FiledReturnSnapshotView.from(snapshot, lineRepository.findBySnapshotIdOrderByLineNo(snapshot.getId())));
    }

    @Autowired
    private Gstr3bPdfParser gstr3bPdfParser;

    @PostMapping("/gstr3b/pdf")
    @PreAuthorize("hasRole('ACCOUNTANT') or hasRole('OPERATOR')")
    public ResponseEntity<?> parseGstr3bPdf(
            @AuthenticationPrincipal UserDetailsImpl principal,
            @RequestParam("file") MultipartFile file) throws IOException {
        UUID orgId = principal.getOrganizationId();
        if (orgId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "User is not linked to an organization."));
        }
        FiledReturnFile stored = fileService.store(orgId, principal.getUsername(), file);
        try {
            Gstr3bParsed parsed = gstr3bPdfParser.parse(stored.getContent());
            return ResponseEntity.ok(new Gstr3bParseResponse(stored.getId(), parsed.gstin(),
                    parsed.filingPeriod(), parsed.rows(), parsed.warnings(),
                    Gstr3bPdfParser.PARSER_VERSION));
        } catch (com.arktech.superaccountant.filedreturns.parse.FiledReturnParseException e) {
            return ResponseEntity.status(422).body(Map.of(
                    "message", "Could not read a GSTR-3B from this PDF; enter the values manually",
                    "fileId", stored.getId().toString()));
        }
    }

    @ExceptionHandler({com.arktech.superaccountant.filedreturns.parse.FiledReturnParseException.class})
    public ResponseEntity<Map<String, String>> handleParse(
            com.arktech.superaccountant.filedreturns.parse.FiledReturnParseException e) {
        return ResponseEntity.status(422).body(Map.of("message", e.getMessage()));
    }

    @PostMapping("/gstr3b")
    @PreAuthorize("hasRole('ACCOUNTANT') or hasRole('OPERATOR')")
    public ResponseEntity<?> saveGstr3b(
            @AuthenticationPrincipal UserDetailsImpl principal,
            @RequestBody Gstr3bEntryRequest request) {
        UUID orgId = principal.getOrganizationId();
        if (orgId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "User is not linked to an organization."));
        }
        if (request.fileId() != null) {
            boolean owned = fileRepository.findById(request.fileId())
                    .map(f -> orgId.equals(f.getOrganizationId())).orElse(false);
            if (!owned) {
                throw new FiledReturnValidationException("Unknown file");
            }
        }
        List<FiledReturnLine> lines = new ArrayList<>();
        for (Gstr3bEntryRequest.Row row : request.rows()) {
            FiledReturnLine line = new FiledReturnLine();
            line.setTableCode(row.rowCode());
            line.setTaxable(FiledReturnImportService.nvl(row.taxable()));
            line.setIgst(FiledReturnImportService.nvl(row.igst()));
            line.setCgst(FiledReturnImportService.nvl(row.cgst()));
            line.setSgst(FiledReturnImportService.nvl(row.sgst()));
            line.setCess(FiledReturnImportService.nvl(row.cess()));
            lines.add(line);
        }
        SnapshotDraft draft = new SnapshotDraft(FiledFormType.GSTR3B, request.filingPeriod(), request.gstin(),
                request.fileId() == null ? FiledSourceKind.MANUAL_ENTRY : FiledSourceKind.PORTAL_PDF,
                request.fileId(), request.parserVersion(), lines);
        FiledReturnSnapshot snapshot = importService.save(orgId, principal.getUsername(), draft);
        return ResponseEntity.status(201).body(
                FiledReturnSnapshotView.from(snapshot, lineRepository.findBySnapshotIdOrderByLineNo(snapshot.getId())));
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
    public ResponseEntity<FiledReturnSnapshotView> snapshot(
            @AuthenticationPrincipal UserDetailsImpl principal, @PathVariable UUID id) {
        return snapshotRepository.findByIdAndOrganizationId(id, principal.getOrganizationId())
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
