package com.arktech.superaccountant.masters.controllers;

import com.arktech.superaccountant.login.security.services.UserDetailsImpl;
import com.arktech.superaccountant.masters.classifier.LedgerCategoryClassifier;
import com.arktech.superaccountant.masters.classifier.ParsedLedger;
import com.arktech.superaccountant.masters.models.*;
import com.arktech.superaccountant.masters.payload.request.FindingActionRequest;
import com.arktech.superaccountant.masters.payload.request.ResolveRequest;
import com.arktech.superaccountant.masters.payload.request.UpdateLedgerSnapshotRequest;
import com.arktech.superaccountant.masters.repository.LedgerEditLogRepository;
import com.arktech.superaccountant.masters.services.FindingAutoResolveService;
import com.arktech.superaccountant.masters.payload.response.FindingResponse;
import com.arktech.superaccountant.masters.payload.response.UploadJobResponse;
import com.arktech.superaccountant.masters.repository.PreconfiguredMasterRepository;
import com.arktech.superaccountant.masters.repository.UploadJobRepository;
import com.arktech.superaccountant.masters.payload.response.ResolveResponse;
import com.arktech.superaccountant.masters.repository.ValidationFindingRepository;
import com.arktech.superaccountant.masters.repository.UploadLedgerSnapshotRepository;
import com.arktech.superaccountant.masters.repository.ValidationRuleConfigRepository;
import com.arktech.superaccountant.masters.rules.ValidationContext;
import com.arktech.superaccountant.masters.services.ValidationOrchestrator;
import com.arktech.superaccountant.tally.services.TallyParserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
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

    @Autowired
    private UploadLedgerSnapshotRepository uploadLedgerSnapshotRepository;

    @Autowired
    private LedgerEditLogRepository ledgerEditLogRepository;

    @Autowired
    private FindingAutoResolveService findingAutoResolveService;

    @Transactional
    @PreAuthorize("hasRole('ACCOUNTANT') or hasRole('OPERATOR')")
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

        // Discard-on-reupload: check if there's a previous job with unresolved findings
        Optional<UploadJob> previousJob = uploadJobRepository
            .findTopByOrganizationIdAndStatusNotInOrderByCreatedAtDesc(orgId,
                java.util.List.of(UploadJobStatus.DISCARDED, UploadJobStatus.FAILED));

        if (previousJob.isPresent()) {
            UploadJob prev = previousJob.get();
            long unresolvedCount = findingRepository.countUnresolvedByUploadJobId(prev.getId());
            if (unresolvedCount > 0) {
                // Discard the previous job
                // 1. Set all OPEN/ACKNOWLEDGED findings → DISCARDED
                findingRepository.discardOpenFindingsByUploadJobId(prev.getId());
                // 2. Hard-delete all upload_ledger_snapshots for this job
                uploadLedgerSnapshotRepository.deleteByUploadJobId(prev.getId());
                // 3. Set job status → DISCARDED
                prev.setStatus(UploadJobStatus.DISCARDED);
                uploadJobRepository.save(prev);
                // Note: ledger_edit_log rows are NOT deleted (retained for audit)
            }
            // If the previous job is fully validated (all findings APPROVED or RESOLVED): leave it intact
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

        // Populate upload_ledger_snapshots from parsed ledgers
        final UUID jobId = job.getId();
        List<UploadLedgerSnapshot> snapshots = parsedLedgers.stream().map(pl -> {
            UploadLedgerSnapshot snap = new UploadLedgerSnapshot();
            snap.setUploadJobId(jobId);
            snap.setOrganizationId(orgId);
            snap.setLedgerName(pl.getName());
            snap.setParentGroup(pl.getParentGroup());
            snap.setGstApplicable(pl.getGstApplicable());
            snap.setTdsApplicable(pl.getTdsApplicable());
            snap.setGstin(pl.getGstin());
            snap.setTdsSection(pl.getTdsSection());
            snap.setHsnSacCode(pl.getHsnSacCode());
            return snap;
        }).collect(Collectors.toList());
        uploadLedgerSnapshotRepository.saveAll(snapshots);

        if (!parsedLedgers.isEmpty()) {
            if (!masterRepository.existsByOrganizationId(orgId)) {
                // Auto-seed preconfigured masters from parsed ledgers on first upload
                List<PreconfiguredMaster> seeded = parsedLedgers.stream().map(pl -> {
                    PreconfiguredMaster m = new PreconfiguredMaster();
                    m.setOrganizationId(orgId);
                    m.setLedgerName(pl.getName());
                    m.setCategory(pl.getCategory());
                    m.setExpectedParentGroup(pl.getParentGroup());
                    m.setExpectedGstApplicable(pl.getGstApplicable());
                    m.setExpectedTdsApplicable(pl.getTdsApplicable());
                    if (pl.getGstApplicabilityType() != null) {
                        try {
                            m.setGstApplicabilityType(
                                    GstApplicabilityType.valueOf(pl.getGstApplicabilityType()));
                        } catch (IllegalArgumentException ignored) {}
                    }
                    return m;
                }).collect(Collectors.toList());
                masterRepository.saveAll(seeded);
            } else {
                // Backfill null fields on existing masters from parsed data
                List<PreconfiguredMaster> existing = masterRepository.findByOrganizationIdAndActiveTrue(orgId);
                Map<String, ParsedLedger> parsedByName = parsedLedgers.stream()
                        .collect(Collectors.toMap(
                                pl -> pl.getName().trim().toLowerCase(),
                                pl -> pl,
                                (a, b) -> a));
                List<PreconfiguredMaster> updated = new ArrayList<>();
                for (PreconfiguredMaster m : existing) {
                    ParsedLedger pl = parsedByName.get(m.getLedgerName().trim().toLowerCase());
                    if (pl == null) continue;
                    boolean changed = false;
                    if (pl.getCategory() != null && pl.getCategory() != m.getCategory()) {
                        m.setCategory(pl.getCategory());
                        changed = true;
                    }
                    if (m.getGstApplicabilityType() == null && pl.getGstApplicabilityType() != null) {
                        try {
                            m.setGstApplicabilityType(
                                    GstApplicabilityType.valueOf(pl.getGstApplicabilityType()));
                            changed = true;
                        } catch (IllegalArgumentException ignored) {}
                    }
                    if (m.getExpectedParentGroup() == null && pl.getParentGroup() != null) {
                        m.setExpectedParentGroup(pl.getParentGroup());
                        changed = true;
                    }
                    if (m.getExpectedGstApplicable() == null && pl.getGstApplicable() != null) {
                        m.setExpectedGstApplicable(pl.getGstApplicable());
                        changed = true;
                    }
                    if (m.getExpectedTdsApplicable() == null && pl.getTdsApplicable() != null) {
                        m.setExpectedTdsApplicable(pl.getTdsApplicable());
                        changed = true;
                    }
                    if (changed) updated.add(m);
                }
                if (!updated.isEmpty()) masterRepository.saveAll(updated);
            }
        }

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

    @PreAuthorize("hasRole('ACCOUNTANT') or hasRole('OPERATOR') or hasRole('OWNER') or hasRole('AUDITOR_CA')")
    @GetMapping("/uploads/latest/mismatches")
    public ResponseEntity<?> getLatestJobFindings(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) LedgerCategory category,
            @RequestParam(required = false) FindingSeverity severity,
            @RequestParam(defaultValue = "false") boolean showResolved,
            @AuthenticationPrincipal UserDetailsImpl principal) {

        UUID orgId = principal.getOrganizationId();
        if (orgId == null) {
            return ResponseEntity.badRequest().body("User is not linked to an organization.");
        }

        Optional<UploadJob> latestJob = uploadJobRepository
                .findTopByOrganizationIdAndStatusInOrderByCreatedAtDesc(orgId,
                        java.util.List.of(UploadJobStatus.COMPLETED, UploadJobStatus.COMPLETED_WITH_MISMATCHES));

        if (latestJob.isEmpty()) {
            return ResponseEntity.ok(org.springframework.data.domain.Page.empty());
        }

        UUID jobId = latestJob.get().getId();
        int cappedSize = Math.min(size, 100);
        PageRequest pageable = PageRequest.of(page, cappedSize);
        Page<ValidationFinding> findings = findingRepository.findFiltered(jobId, category, severity, showResolved, pageable);
        Page<FindingResponse> response = findings.map(this::toFindingResponse);
        return ResponseEntity.ok(response);
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

    @PreAuthorize("hasRole('ACCOUNTANT') or hasRole('OPERATOR')")
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

    @Transactional
    @PreAuthorize("hasRole('ACCOUNTANT') or hasRole('OPERATOR')")
    @PatchMapping("/uploads/{jobId}/ledgers/{snapshotId}")
    public ResponseEntity<?> updateLedgerSnapshot(
            @PathVariable UUID jobId,
            @PathVariable UUID snapshotId,
            @RequestBody UpdateLedgerSnapshotRequest request,
            @AuthenticationPrincipal UserDetailsImpl principal) {

        UUID orgId = principal.getOrganizationId();
        Optional<UploadJob> jobOpt = uploadJobRepository.findById(jobId);
        if (jobOpt.isEmpty() || !jobOpt.get().getOrganizationId().equals(orgId)) {
            return ResponseEntity.notFound().build();
        }

        Optional<UploadLedgerSnapshot> snapshotOpt =
                uploadLedgerSnapshotRepository.findByUploadJobIdAndId(jobId, snapshotId);
        if (snapshotOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        UploadLedgerSnapshot snapshot = snapshotOpt.get();
        String username = principal.getUsername();
        List<LedgerEditLog> logs = new ArrayList<>();

        if (request.getGstin() != null && !Objects.equals(request.getGstin(), snapshot.getGstin())) {
            LedgerEditLog log = new LedgerEditLog();
            log.setUploadJobId(jobId);
            log.setLedgerSnapshotId(snapshotId);
            log.setLedgerName(snapshot.getLedgerName());
            log.setFieldName("gstin");
            log.setOldValue(snapshot.getGstin());
            log.setNewValue(request.getGstin());
            log.setEditedBy(username);
            logs.add(log);
            snapshot.setGstin(request.getGstin());
        }

        if (request.getTdsSection() != null && !Objects.equals(request.getTdsSection(), snapshot.getTdsSection())) {
            LedgerEditLog log = new LedgerEditLog();
            log.setUploadJobId(jobId);
            log.setLedgerSnapshotId(snapshotId);
            log.setLedgerName(snapshot.getLedgerName());
            log.setFieldName("tdsSection");
            log.setOldValue(snapshot.getTdsSection());
            log.setNewValue(request.getTdsSection());
            log.setEditedBy(username);
            logs.add(log);
            snapshot.setTdsSection(request.getTdsSection());
        }

        if (request.getHsnSacCode() != null && !Objects.equals(request.getHsnSacCode(), snapshot.getHsnSacCode())) {
            LedgerEditLog log = new LedgerEditLog();
            log.setUploadJobId(jobId);
            log.setLedgerSnapshotId(snapshotId);
            log.setLedgerName(snapshot.getLedgerName());
            log.setFieldName("hsnSacCode");
            log.setOldValue(snapshot.getHsnSacCode());
            log.setNewValue(request.getHsnSacCode());
            log.setEditedBy(username);
            logs.add(log);
            snapshot.setHsnSacCode(request.getHsnSacCode());
        }

        if (request.getGstApplicable() != null && !Objects.equals(request.getGstApplicable(), snapshot.getGstApplicable())) {
            LedgerEditLog log = new LedgerEditLog();
            log.setUploadJobId(jobId);
            log.setLedgerSnapshotId(snapshotId);
            log.setLedgerName(snapshot.getLedgerName());
            log.setFieldName("gstApplicable");
            log.setOldValue(snapshot.getGstApplicable() != null ? snapshot.getGstApplicable().toString() : null);
            log.setNewValue(request.getGstApplicable().toString());
            log.setEditedBy(username);
            logs.add(log);
            snapshot.setGstApplicable(request.getGstApplicable());
        }

        if (request.getTdsApplicable() != null && !Objects.equals(request.getTdsApplicable(), snapshot.getTdsApplicable())) {
            LedgerEditLog log = new LedgerEditLog();
            log.setUploadJobId(jobId);
            log.setLedgerSnapshotId(snapshotId);
            log.setLedgerName(snapshot.getLedgerName());
            log.setFieldName("tdsApplicable");
            log.setOldValue(snapshot.getTdsApplicable() != null ? snapshot.getTdsApplicable().toString() : null);
            log.setNewValue(request.getTdsApplicable().toString());
            log.setEditedBy(username);
            logs.add(log);
            snapshot.setTdsApplicable(request.getTdsApplicable());
        }

        if (request.getParentGroup() != null && !Objects.equals(request.getParentGroup(), snapshot.getParentGroup())) {
            LedgerEditLog log = new LedgerEditLog();
            log.setUploadJobId(jobId);
            log.setLedgerSnapshotId(snapshotId);
            log.setLedgerName(snapshot.getLedgerName());
            log.setFieldName("parentGroup");
            log.setOldValue(snapshot.getParentGroup());
            log.setNewValue(request.getParentGroup());
            log.setEditedBy(username);
            logs.add(log);
            snapshot.setParentGroup(request.getParentGroup());
        }

        if (request.getGstApplicabilityType() != null && !Objects.equals(request.getGstApplicabilityType(), snapshot.getGstApplicabilityType())) {
            LedgerEditLog log = new LedgerEditLog();
            log.setUploadJobId(jobId);
            log.setLedgerSnapshotId(snapshotId);
            log.setLedgerName(snapshot.getLedgerName());
            log.setFieldName("gst_applicability_type");
            log.setOldValue(snapshot.getGstApplicabilityType() != null ? snapshot.getGstApplicabilityType().name() : null);
            log.setNewValue(request.getGstApplicabilityType().name());
            log.setEditedBy(username);
            logs.add(log);
            snapshot.setGstApplicabilityType(request.getGstApplicabilityType());
        }

        if (!logs.isEmpty()) {
            uploadLedgerSnapshotRepository.save(snapshot);
            ledgerEditLogRepository.saveAll(logs);
        }

        // Only trigger auto-resolve if any fields actually changed
        List<ValidationFinding> resolvedFindings = List.of();
        if (!logs.isEmpty()) {
            resolvedFindings = findingAutoResolveService.autoResolveForUpdatedSnapshot(jobId, snapshot, username);
        }

        return ResponseEntity.ok(Map.of(
                "snapshot", toSnapshotResponse(snapshot),
                "resolvedFindings", resolvedFindings.stream().map(this::toFindingResponse).collect(Collectors.toList())
        ));
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/uploads/{jobId}/findings/{findingId}/action")
    public ResponseEntity<?> findingAction(
            @PathVariable UUID jobId,
            @PathVariable UUID findingId,
            @RequestBody @Valid FindingActionRequest request,
            @AuthenticationPrincipal UserDetailsImpl principal) {

        UUID orgId = principal.getOrganizationId();
        // 1. Validate job belongs to org
        Optional<UploadJob> jobOpt = uploadJobRepository.findById(jobId);
        if (jobOpt.isEmpty() || !jobOpt.get().getOrganizationId().equals(orgId)) {
            return ResponseEntity.notFound().build();
        }

        // 2. Find finding
        Optional<ValidationFinding> findingOpt = findingRepository.findById(findingId);
        if (findingOpt.isEmpty() || !findingOpt.get().getUploadJobId().equals(jobId)) {
            return ResponseEntity.notFound().build();
        }
        ValidationFinding finding = findingOpt.get();

        // 3. Role + state machine enforcement
        Set<String> roles = principal.getAuthorities().stream()
                .map(a -> a.getAuthority()).collect(java.util.stream.Collectors.toSet());
        boolean isOperatorOrAccountant = roles.contains("ROLE_OPERATOR") || roles.contains("ROLE_ACCOUNTANT");
        boolean isAccountantOrAuditor = roles.contains("ROLE_ACCOUNTANT") || roles.contains("ROLE_AUDITOR_CA");

        String action = request.getAction();
        Instant now = Instant.now();
        String username = principal.getUsername();

        switch (action) {
            case "ACKNOWLEDGE" -> {
                if (!isOperatorOrAccountant) {
                    return ResponseEntity.status(403).body(Map.of("error", "Only OPERATOR or ACCOUNTANT can acknowledge findings."));
                }
                if (finding.getResolveStatus() != ResolveStatus.OPEN) {
                    return ResponseEntity.badRequest().body(Map.of("error", "Only OPEN findings can be acknowledged."));
                }
                finding.setResolveStatus(ResolveStatus.ACKNOWLEDGED);
                finding.setResolveNote(request.getNote());
                finding.setResolvedBy(username);
                finding.setResolvedAt(now);
            }
            case "APPROVE" -> {
                if (!isAccountantOrAuditor) {
                    return ResponseEntity.status(403).body(Map.of("error", "Only ACCOUNTANT or AUDITOR_CA can approve findings."));
                }
                if (finding.getResolveStatus() != ResolveStatus.ACKNOWLEDGED) {
                    return ResponseEntity.badRequest().body(Map.of("error", "Only ACKNOWLEDGED findings can be approved."));
                }
                finding.setResolveStatus(ResolveStatus.APPROVED);
                finding.setResolvedBy(username);
                finding.setResolvedAt(now);
            }
            case "REJECT" -> {
                if (!isAccountantOrAuditor) {
                    return ResponseEntity.status(403).body(Map.of("error", "Only ACCOUNTANT or AUDITOR_CA can reject findings."));
                }
                if (finding.getResolveStatus() != ResolveStatus.ACKNOWLEDGED) {
                    return ResponseEntity.badRequest().body(Map.of("error", "Only ACKNOWLEDGED findings can be rejected."));
                }
                // REJECT: back to OPEN, write rejection reason to resolve_note, clear resolved fields
                finding.setResolveStatus(ResolveStatus.OPEN);
                finding.setResolveNote(request.getNote());
                finding.setResolvedBy(null);
                finding.setResolvedAt(null);
            }
            default -> {
                return ResponseEntity.badRequest().body(Map.of("error", "Unknown action. Must be ACKNOWLEDGE, APPROVE, or REJECT."));
            }
        }

        findingRepository.save(finding);
        return ResponseEntity.ok(toFindingResponse(finding));
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
                .uploadJobId(f.getUploadJobId())
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

    private Map<String, Object> toSnapshotResponse(UploadLedgerSnapshot s) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("id", s.getId());
        m.put("uploadJobId", s.getUploadJobId());
        m.put("ledgerName", s.getLedgerName());
        m.put("parentGroup", s.getParentGroup());
        m.put("gstApplicable", s.getGstApplicable());
        m.put("tdsApplicable", s.getTdsApplicable());
        m.put("gstin", s.getGstin());
        m.put("tdsSection", s.getTdsSection());
        m.put("hsnSacCode", s.getHsnSacCode());
        m.put("gstApplicabilityType", s.getGstApplicabilityType() != null ? s.getGstApplicabilityType().name() : null);
        return m;
    }

    private String q(Object val) {
        if (val == null) return "";
        String s = val.toString().replace("\"", "\"\"");
        return s.contains(",") || s.contains("\n") || s.contains("\"") ? "\"" + s + "\"" : s;
    }
}
