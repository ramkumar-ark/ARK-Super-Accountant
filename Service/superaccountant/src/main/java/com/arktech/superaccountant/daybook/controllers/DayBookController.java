package com.arktech.superaccountant.daybook.controllers;

import com.arktech.superaccountant.daybook.models.DayBookUploadJob;
import com.arktech.superaccountant.daybook.payload.response.DayBookUploadJobResponse;
import com.arktech.superaccountant.daybook.payload.response.VoucherTypeSummaryResponse;
import com.arktech.superaccountant.daybook.repository.DayBookUploadJobRepository;
import com.arktech.superaccountant.daybook.repository.ParsedVoucherRepository;
import com.arktech.superaccountant.daybook.services.DayBookIntakeException;
import com.arktech.superaccountant.daybook.services.DayBookIntakeResult;
import com.arktech.superaccountant.daybook.services.DayBookIntakeService;
import com.arktech.superaccountant.daybook.services.DayBookMasterValidationService;
import com.arktech.superaccountant.daybook.services.DayBookParserService;
import com.arktech.superaccountant.login.security.services.UserDetailsImpl;
import com.arktech.superaccountant.masters.models.GateResult;
import com.arktech.superaccountant.masters.models.UploadJobStatus;
import com.arktech.superaccountant.masters.services.MastersGateService;
import com.arktech.superaccountant.tally.models.TallyMessage;
import com.arktech.superaccountant.tally.services.TallyParserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * REST controller for day book upload, summary retrieval, and masters gate status.
 *
 * Security model:
 * - orgId is ALWAYS sourced from JWT principal — never from client-supplied parameters (T-4-01, T-4-05)
 * - Summary endpoint verifies job.organizationId == principal.orgId before returning data (T-4-04)
 * - Gate status returns HTTP 200 with structured payload — never 403 (D-11)
 */
@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/v1")
public class DayBookController {

    @Autowired
    private TallyParserService tallyParserService;

    @Autowired
    private DayBookIntakeService dayBookIntakeService;

    @Autowired
    private DayBookUploadJobRepository dayBookUploadJobRepository;

    @Autowired
    private ParsedVoucherRepository parsedVoucherRepository;

    @Autowired
    private MastersGateService mastersGateService;

    @Autowired
    private DayBookMasterValidationService dayBookMasterValidationService;

    /**
     * POST /api/v1/day-book/upload
     *
     * Accepts a .json multipart file with a declared coverage range, creates a
     * DayBookUploadJob scoped to the caller's org, parses and persists all vouchers
     * synchronously, then returns the job result. Validation lives in DayBookIntakeService.
     *
     * On success: job is saved with status=COMPLETED, returns HTTP 201 with DayBookUploadJobResponse.
     * Rejections map DayBookIntakeException to its own status and body.
     */
    @PreAuthorize("hasRole('ACCOUNTANT') or hasRole('OPERATOR')")
    @PostMapping("/day-book/upload")
    public ResponseEntity<?> uploadDayBook(
            @RequestParam("file") MultipartFile file,
            @RequestParam("coverageFrom") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate coverageFrom,
            @RequestParam("coverageTo") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate coverageTo,
            @AuthenticationPrincipal UserDetailsImpl principal) {

        UUID orgId = principal.getOrganizationId();
        if (orgId == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "User is not linked to an organization. Call POST /api/organizations first."));
        }

        try {
            DayBookIntakeResult result = dayBookIntakeService.intake(
                    file, coverageFrom, coverageTo, orgId, principal.getUsername(), null);
            return ResponseEntity.status(201).body(dayBookIntakeService.toResponse(result.job(), result.warnings()));
        } catch (DayBookIntakeException e) {
            return ResponseEntity.status(e.getStatus()).body(e.getBody());
        }
    }
    /**
     * GET /api/v1/day-book/uploads?page=0&size=20
     *
     * Paged upload history for the caller's organization, newest first.
     * Voucher summaries are omitted in list items.
     */
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/day-book/uploads")
    public ResponseEntity<?> listUploads(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserDetailsImpl principal) {
        UUID orgId = principal.getOrganizationId();
        if (orgId == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "User is not linked to an organization."));
        }
        var pageable = org.springframework.data.domain.PageRequest.of(page, size,
                org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt"));
        var jobs = dayBookUploadJobRepository.findByOrganizationId(orgId, pageable);
        return ResponseEntity.ok(jobs.map(job ->
                com.arktech.superaccountant.daybook.payload.response.DayBookUploadJobResponseMapper
                        .toResponse(job, null, null)));
    }

    /**
     * GET /api/v1/day-book/summary/{jobId}
     *
     * Returns voucher type breakdown for a specific upload job.
     * Security: verifies job.organizationId == caller's orgId — returns 404 if not found or cross-tenant (T-4-04).
     */
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/day-book/summary/{jobId}")
    public ResponseEntity<?> getSummary(
            @PathVariable UUID jobId,
            @AuthenticationPrincipal UserDetailsImpl principal) {

        UUID orgId = principal.getOrganizationId();
        if (orgId == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "User is not linked to an organization."));
        }

        // Security: verify job belongs to caller's org — never return another org's data (T-4-01)
        Optional<DayBookUploadJob> jobOpt = dayBookUploadJobRepository.findByIdAndOrganizationId(jobId, orgId);
        if (jobOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        DayBookUploadJob job = jobOpt.get();
        List<Object[]> summaryRows = parsedVoucherRepository.findVoucherTypeSummary(jobId);
        List<VoucherTypeSummaryResponse> summary = toSummaryList(summaryRows);

        return ResponseEntity.ok(DayBookUploadJobResponse.builder()
                .id(job.getId())
                .fileName(job.getFileName())
                .status(job.getStatus())
                .totalVouchersParsed(job.getTotalVouchersParsed())
                .uploadedBy(job.getUploadedBy())
                .createdAt(job.getCreatedAt())
                .completedAt(job.getCompletedAt())
                .voucherSummary(summary)
                .build());
    }

    /**
     * GET /api/v1/masters/gate-status
     *
     * Returns gate status: { gated: false } if org has no unresolved masters findings;
     * { gated: true, reason, unresolvedCount } otherwise.
     *
     * Always returns HTTP 200 — gated state is business logic, not an auth error (D-11, T-4-06).
     * Security: orgId sourced from JWT principal only — never from client parameters (T-4-05).
     */
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/masters/gate-status")
    public ResponseEntity<?> getGateStatus(
            @AuthenticationPrincipal UserDetailsImpl principal) {

        UUID orgId = principal.getOrganizationId();
        if (orgId == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "User is not linked to an organization."));
        }

        // Security: orgId comes from JWT — not from request params (T-4-01 mitigated)
        GateResult gate = mastersGateService.checkGate(orgId);

        if (gate.gated()) {
            String reason = gate.unresolvedCount() == 0
                    ? "Masters validation has not been run. Upload and validate masters to unlock compliance features."
                    : "Unresolved masters findings must be resolved before accessing compliance features.";
            return ResponseEntity.ok(Map.of(
                    "gated", true,
                    "reason", reason,
                    "unresolvedCount", gate.unresolvedCount()
            ));
        }
        return ResponseEntity.ok(Map.of("gated", false));
    }

    // --- private helpers ---

    private List<VoucherTypeSummaryResponse> toSummaryList(List<Object[]> rows) {
        List<VoucherTypeSummaryResponse> result = new ArrayList<>();
        for (Object[] row : rows) {
            result.add(VoucherTypeSummaryResponse.builder()
                    .voucherTypeName((String) row[0])
                    .count((Long) row[1])
                    .totalDebit(row[2] != null ? (BigDecimal) row[2] : BigDecimal.ZERO)
                    .totalCredit(row[3] != null ? (BigDecimal) row[3] : BigDecimal.ZERO)
                    .minDate(row[4] != null ? (LocalDate) row[4] : null)
                    .maxDate(row[5] != null ? (LocalDate) row[5] : null)
                    .build());
        }
        return result;
    }
}
