package com.arktech.superaccountant.masters.controllers;

import com.arktech.superaccountant.login.security.services.UserDetailsImpl;
import com.arktech.superaccountant.masters.classifier.LedgerCategoryClassifier;
import com.arktech.superaccountant.masters.classifier.ParsedLedger;
import com.arktech.superaccountant.masters.models.*;
import com.arktech.superaccountant.masters.payload.request.ResolveRequest;
import com.arktech.superaccountant.masters.payload.response.FindingResponse;
import com.arktech.superaccountant.masters.payload.response.UploadJobResponse;
import com.arktech.superaccountant.masters.repository.PreconfiguredMasterRepository;
import com.arktech.superaccountant.masters.repository.UploadJobRepository;
import com.arktech.superaccountant.masters.payload.response.ResolveResponse;
import com.arktech.superaccountant.masters.repository.ValidationFindingRepository;
import com.arktech.superaccountant.masters.repository.ValidationRuleConfigRepository;
import com.arktech.superaccountant.masters.rules.ValidationContext;
import com.arktech.superaccountant.masters.services.ValidationOrchestrator;
import com.arktech.superaccountant.tally.services.TallyParserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/v1")
public class UploadController {

    @Autowired
    private TallyParserService tallyParserService;

    @Autowired
    private LedgerCategoryClassifier categoryClassifier;

    @Autowired
    private ValidationOrchestrator orchestrator;

    @Autowired
    private UploadJobRepository uploadJobRepository;

    @Autowired
    private ValidationFindingRepository findingRepository;

    @Autowired
    private PreconfiguredMasterRepository masterRepository;

    @Autowired
    private ValidationRuleConfigRepository ruleConfigRepository;

    @PreAuthorize("hasRole('OWNER') or hasRole('ACCOUNTANT') or hasRole('DATA_ENTRY_OPERATOR')")
    @PostMapping("/uploads")
    public ResponseEntity<?> uploadMasters(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UserDetailsImpl principal) {

        UUID orgId = principal.getOrganizationId();
        if (orgId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "User is not linked to an organization. Call POST /api/organizations first."));
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename != null && !originalFilename.toLowerCase().endsWith(".json")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Only JSON files are accepted."));
        }

        // Create the upload job record
        UploadJob job = new UploadJob();
        job.setOrganizationId(orgId);
        job.setFileName(originalFilename);
        job.setUploadedBy(principal.getUsername());
        job.setStatus(UploadJobStatus.FAILED); // default; updated after processing
        job = uploadJobRepository.save(job);

        List<ParsedLedger> parsedLedgers;
        try {
            parsedLedgers = tallyParserService.parseMastersJson(file);
        } catch (IllegalArgumentException e) {
            job.setStatus(UploadJobStatus.FAILED);
            job.setErrorMessage(e.getMessage());
            job.setCompletedAt(Instant.now());
            uploadJobRepository.save(job);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage(), "uploadId", job.getId()));
        } catch (IOException e) {
            job.setStatus(UploadJobStatus.FAILED);
            job.setErrorMessage("Failed to parse file: " + e.getMessage());
            job.setCompletedAt(Instant.now());
            uploadJobRepository.save(job);
            return ResponseEntity.internalServerError().body(Map.of("error", "File parsing failed.", "uploadId", job.getId()));
        }

        job.setTotalLedgersParsed(parsedLedgers.size());

        List<PreconfiguredMaster> configuredMasters = masterRepository.findByOrganizationIdAndActiveTrue(orgId);

        ValidationContext context = new ValidationContext(
                orgId,
                principal.getUsername(),
                configuredMasters,
                Map.of()
        );

        job = orchestrator.runAndPersist(job, parsedLedgers, context);

        List<ValidationFinding> findings = findingRepository.findByUploadJobId(job.getId());

        return ResponseEntity.status(201).body(toUploadResponse(job, findings));
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/uploads")
    public ResponseEntity<?> listUploads(
            @RequestParam(required = false) UploadJobStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserDetailsImpl principal) {

        UUID orgId = principal.getOrganizationId();
        if (orgId == null) {
            return ResponseEntity.badRequest().body("User is not linked to an organization.");
        }

        int cappedSize = Math.min(size, 100);
        PageRequest pageable = PageRequest.of(page, cappedSize);

        Page<UploadJob> jobs = status != null
                ? uploadJobRepository.findByOrganizationIdAndStatus(orgId, status, pageable)
                : uploadJobRepository.findByOrganizationId(orgId, pageable);

        return ResponseEntity.ok(jobs.map(j -> toUploadResponse(j, null)));
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/uploads/{id}/mismatches")
    public ResponseEntity<?> listMismatches(
            @PathVariable UUID id,
            @RequestParam(required = false) LedgerCategory category,
            @RequestParam(required = false) FindingSeverity severity,
            @RequestParam(defaultValue = "false") boolean showResolved,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserDetailsImpl principal) {

        UUID orgId = principal.getOrganizationId();
        Optional<UploadJob> jobOpt = uploadJobRepository.findById(id);
        if (jobOpt.isEmpty() || !jobOpt.get().getOrganizationId().equals(orgId)) {
            return ResponseEntity.notFound().build();
        }

        int cappedSize = Math.min(size, 100);
        PageRequest pageable = PageRequest.of(page, cappedSize);
        Page<ValidationFinding> findings = findingRepository.findFiltered(id, category, severity, showResolved, pageable);

        return ResponseEntity.ok(findings.map(this::toFindingResponse));
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/uploads/{id}/mismatches/export")
    public ResponseEntity<?> exportMismatches(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetailsImpl principal) {

        UUID orgId = principal.getOrganizationId();
        Optional<UploadJob> jobOpt = uploadJobRepository.findById(id);
        if (jobOpt.isEmpty() || !jobOpt.get().getOrganizationId().equals(orgId)) {
            return ResponseEntity.notFound().build();
        }

        List<ValidationFinding> findings = findingRepository.findByUploadJobId(id);
        StringBuilder csv = new StringBuilder();
        csv.append("id,category,mismatch_type,ledger_name,expected_value,actual_value,severity,message,suggested_fix,resolve_status\n");
        for (ValidationFinding f : findings) {
            csv.append(String.join(",",
                    q(f.getId()), q(f.getCategory()), q(f.getMismatchType()),
                    q(f.getLedgerName()), q(f.getExpectedValue()), q(f.getActualValue()),
                    q(f.getSeverity()), q(f.getMessage()), q(f.getSuggestedFix()),
                    q(f.getResolveStatus())
            )).append("\n");
        }

        return ResponseEntity.ok()
                .header("Content-Type", "text/csv")
                .header("Content-Disposition", "attachment; filename=\"mismatches-" + id + ".csv\"")
                .body(csv.toString());
    }

    @PreAuthorize("hasRole('OWNER') or hasRole('ACCOUNTANT') or hasRole('DATA_ENTRY_OPERATOR')")
    @PatchMapping("/uploads/{jobId}/mismatches/{findingId}/resolve")
    public ResponseEntity<?> resolveFinding(
            @PathVariable UUID jobId,
            @PathVariable UUID findingId,
            @Valid @RequestBody ResolveRequest request,
            @AuthenticationPrincipal UserDetailsImpl principal) {

        UUID orgId = principal.getOrganizationId();
        Optional<UploadJob> jobOpt = uploadJobRepository.findById(jobId);
        if (jobOpt.isEmpty() || !jobOpt.get().getOrganizationId().equals(orgId)) {
            return ResponseEntity.notFound().build();
        }

        return findingRepository.findById(findingId)
                .filter(f -> f.getUploadJobId().equals(jobId))
                .map(finding -> {
                    if (request.getStatus() == ResolveStatus.OPEN) {
                        return ResponseEntity.badRequest().body((Object) "Cannot set status back to OPEN.");
                    }
                    finding.setResolveStatus(request.getStatus());
                    finding.setResolveNote(request.getNote());
                    finding.setResolvedBy(principal.getUsername());
                    finding.setResolvedAt(Instant.now());
                    findingRepository.save(finding);
                    return ResponseEntity.ok((Object) ResolveResponse.builder()
                            .id(finding.getId())
                            .status(finding.getResolveStatus())
                            .resolvedBy(finding.getResolvedBy())
                            .resolvedAt(finding.getResolvedAt())
                            .note(finding.getResolveNote())
                            .build());
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/validation-rules")
    public ResponseEntity<?> listValidationRules() {
        return ResponseEntity.ok(ruleConfigRepository.findByActiveTrueOrderByExecutionOrderAsc());
    }

    // --- helpers ---

    private UploadJobResponse toUploadResponse(UploadJob job, List<ValidationFinding> findings) {
        List<FindingResponse> findingResponses = findings == null ? null
                : findings.stream().map(this::toFindingResponse).collect(Collectors.toList());
        return UploadJobResponse.builder()
                .id(job.getId())
                .fileName(job.getFileName())
                .status(job.getStatus())
                .totalLedgersParsed(job.getTotalLedgersParsed())
                .totalMismatches(job.getTotalMismatches())
                .uploadedBy(job.getUploadedBy())
                .createdAt(job.getCreatedAt())
                .completedAt(job.getCompletedAt())
                .findings(findingResponses)
                .build();
    }

    private FindingResponse toFindingResponse(ValidationFinding f) {
        return FindingResponse.builder()
                .id(f.getId())
                .ruleCode(f.getRuleCode())
                .category(f.getCategory())
                .mismatchType(f.getMismatchType())
                .ledgerName(f.getLedgerName())
                .expectedValue(f.getExpectedValue())
                .actualValue(f.getActualValue())
                .severity(f.getSeverity())
                .message(f.getMessage())
                .suggestedFix(f.getSuggestedFix())
                .resolveStatus(f.getResolveStatus())
                .resolveNote(f.getResolveNote())
                .resolvedBy(f.getResolvedBy())
                .resolvedAt(f.getResolvedAt())
                .createdAt(f.getCreatedAt())
                .build();
    }

    private String q(Object val) {
        if (val == null) return "";
        String s = val.toString().replace("\"", "\"\"");
        return s.contains(",") || s.contains("\n") || s.contains("\"") ? "\"" + s + "\"" : s;
    }
}
