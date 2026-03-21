package com.arktech.superaccountant.masters.controllers;

import com.arktech.superaccountant.login.security.services.UserDetailsImpl;
import com.arktech.superaccountant.masters.models.LedgerCategory;
import com.arktech.superaccountant.masters.models.PreconfiguredMaster;
import com.arktech.superaccountant.masters.payload.request.BulkImportRequest;
import com.arktech.superaccountant.masters.payload.request.CreatePreconfiguredMasterRequest;
import com.arktech.superaccountant.masters.payload.request.OnboardRequest;
import com.arktech.superaccountant.masters.payload.request.UpdatePreconfiguredMasterRequest;
import com.arktech.superaccountant.masters.payload.response.BulkImportResponse;
import com.arktech.superaccountant.masters.payload.response.PreconfiguredMasterResponse;
import com.arktech.superaccountant.masters.repository.PreconfiguredMasterRepository;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/v1/preconfigured-masters")
public class PreconfiguredMastersController {

    @Autowired
    private PreconfiguredMasterRepository masterRepository;

    @GetMapping
    public ResponseEntity<?> list(
            @RequestParam(required = false) LedgerCategory category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserDetailsImpl principal) {

        UUID orgId = requireOrgId(principal);
        if (orgId == null) {
            return ResponseEntity.badRequest().body("User is not linked to an organization.");
        }

        int cappedSize = Math.min(size, 100);
        PageRequest pageable = PageRequest.of(page, cappedSize);

        Page<PreconfiguredMaster> results = category != null
                ? masterRepository.findByOrganizationIdAndActiveTrueAndCategory(orgId, category, pageable)
                : masterRepository.findByOrganizationIdAndActiveTrue(orgId, pageable);

        return ResponseEntity.ok(results.map(this::toResponse));
    }

    @PostMapping
    public ResponseEntity<?> create(
            @Valid @RequestBody CreatePreconfiguredMasterRequest request,
            @AuthenticationPrincipal UserDetailsImpl principal) {

        UUID orgId = requireOrgId(principal);
        if (orgId == null) {
            return ResponseEntity.badRequest().body("User is not linked to an organization.");
        }

        PreconfiguredMaster master = new PreconfiguredMaster();
        master.setOrganizationId(orgId);
        master.setLedgerName(request.getLedgerName());
        master.setCategory(request.getCategory());
        master.setExpectedParentGroup(request.getExpectedParentGroup());
        master.setExpectedGstApplicable(request.getExpectedGstApplicable());
        master.setExpectedTdsApplicable(request.getExpectedTdsApplicable());
        master = masterRepository.save(master);

        return ResponseEntity.status(201).body(toResponse(master));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(
            @PathVariable UUID id,
            @RequestBody UpdatePreconfiguredMasterRequest request,
            @AuthenticationPrincipal UserDetailsImpl principal) {

        UUID orgId = requireOrgId(principal);
        if (orgId == null) {
            return ResponseEntity.badRequest().body("User is not linked to an organization.");
        }

        return masterRepository.findById(id)
                .filter(m -> m.getOrganizationId().equals(orgId))
                .map(master -> {
                    if (request.getLedgerName() != null) master.setLedgerName(request.getLedgerName());
                    if (request.getCategory() != null) master.setCategory(request.getCategory());
                    if (request.getExpectedParentGroup() != null) master.setExpectedParentGroup(request.getExpectedParentGroup());
                    if (request.getExpectedGstApplicable() != null) master.setExpectedGstApplicable(request.getExpectedGstApplicable());
                    if (request.getExpectedTdsApplicable() != null) master.setExpectedTdsApplicable(request.getExpectedTdsApplicable());
                    master.setUpdatedAt(Instant.now());
                    return ResponseEntity.ok(toResponse(masterRepository.save(master)));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetailsImpl principal) {

        UUID orgId = requireOrgId(principal);
        if (orgId == null) {
            return ResponseEntity.badRequest().body("User is not linked to an organization.");
        }

        return masterRepository.findById(id)
                .filter(m -> m.getOrganizationId().equals(orgId))
                .map(master -> {
                    master.setActive(false);
                    master.setUpdatedAt(Instant.now());
                    masterRepository.save(master);
                    return ResponseEntity.noContent().build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/bulk")
    public ResponseEntity<?> bulkImport(
            @Valid @RequestBody BulkImportRequest request,
            @AuthenticationPrincipal UserDetailsImpl principal) {

        UUID orgId = requireOrgId(principal);
        if (orgId == null) {
            return ResponseEntity.badRequest().body("User is not linked to an organization.");
        }

        List<PreconfiguredMaster> masters = new ArrayList<>();
        for (CreatePreconfiguredMasterRequest r : request.getMasters()) {
            PreconfiguredMaster m = new PreconfiguredMaster();
            m.setOrganizationId(orgId);
            m.setLedgerName(r.getLedgerName());
            m.setCategory(r.getCategory());
            m.setExpectedParentGroup(r.getExpectedParentGroup());
            m.setExpectedGstApplicable(r.getExpectedGstApplicable());
            m.setExpectedTdsApplicable(r.getExpectedTdsApplicable());
            masters.add(m);
        }

        masterRepository.saveAll(masters);

        return ResponseEntity.status(201).body(BulkImportResponse.builder()
                .created(masters.size())
                .message(masters.size() + " masters imported successfully.")
                .build());
    }

    @PostMapping("/onboard")
    public ResponseEntity<?> onboard(
            @RequestBody OnboardRequest request,
            @AuthenticationPrincipal UserDetailsImpl principal) {

        UUID orgId = requireOrgId(principal);
        if (orgId == null) {
            return ResponseEntity.badRequest().body("User is not linked to an organization.");
        }

        if (masterRepository.existsByOrganizationId(orgId)) {
            return ResponseEntity.badRequest().body("Organization already has pre-configured masters. Onboarding can only be done once.");
        }

        if (request.isUseTemplate()) {
            List<PreconfiguredMaster> templates = masterRepository.findByTemplateTrue();
            List<PreconfiguredMaster> copies = new ArrayList<>();
            for (PreconfiguredMaster t : templates) {
                PreconfiguredMaster copy = new PreconfiguredMaster();
                copy.setOrganizationId(orgId);
                copy.setLedgerName(t.getLedgerName());
                copy.setCategory(t.getCategory());
                copy.setExpectedParentGroup(t.getExpectedParentGroup());
                copy.setExpectedGstApplicable(t.getExpectedGstApplicable());
                copy.setExpectedTdsApplicable(t.getExpectedTdsApplicable());
                copy.setTemplate(false);
                copies.add(copy);
            }
            masterRepository.saveAll(copies);
            return ResponseEntity.ok(java.util.Map.of(
                    "message", "Construction/Works Contractor template applied. " + copies.size() + " masters configured.",
                    "count", copies.size()
            ));
        } else {
            return ResponseEntity.ok(java.util.Map.of(
                    "message", "Custom setup selected. Add your masters via POST /api/v1/preconfigured-masters.",
                    "count", 0
            ));
        }
    }

    @GetMapping("/validation-rules")
    public ResponseEntity<?> listValidationRules() {
        // Delegation to rule config repo not injected here to keep controller lean.
        // This endpoint is served by UploadController instead. Return 404 hint.
        return ResponseEntity.status(404).body("Use GET /api/v1/validation-rules");
    }

    private UUID requireOrgId(UserDetailsImpl principal) {
        return principal.getOrganizationId();
    }

    private PreconfiguredMasterResponse toResponse(PreconfiguredMaster m) {
        return PreconfiguredMasterResponse.builder()
                .id(m.getId())
                .ledgerName(m.getLedgerName())
                .category(m.getCategory())
                .expectedParentGroup(m.getExpectedParentGroup())
                .expectedGstApplicable(m.getExpectedGstApplicable())
                .expectedTdsApplicable(m.getExpectedTdsApplicable())
                .active(m.isActive())
                .createdAt(m.getCreatedAt())
                .updatedAt(m.getUpdatedAt())
                .build();
    }
}
