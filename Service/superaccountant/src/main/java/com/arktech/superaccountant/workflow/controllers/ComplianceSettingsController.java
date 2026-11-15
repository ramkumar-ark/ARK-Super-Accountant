package com.arktech.superaccountant.workflow.controllers;

import com.arktech.superaccountant.login.security.services.UserDetailsImpl;
import com.arktech.superaccountant.workflow.models.WorkflowType;
import com.arktech.superaccountant.workflow.payload.request.CloseFinancialYearRequest;
import com.arktech.superaccountant.workflow.payload.request.ConfirmBaselineRequest;
import com.arktech.superaccountant.workflow.payload.request.FilerProfileRequest;
import com.arktech.superaccountant.workflow.payload.request.ResponsibilityStartRequest;
import com.arktech.superaccountant.workflow.service.UploadNotFoundException;
import com.arktech.superaccountant.workflow.service.WorkflowBadRequestException;
import com.arktech.superaccountant.workflow.service.WorkflowConflictException;
import com.arktech.superaccountant.workflow.service.compliance.BaselineConfirmationService;
import com.arktech.superaccountant.workflow.service.compliance.FilerProfileSettingsService;
import com.arktech.superaccountant.workflow.service.compliance.FinancialYearCloseService;
import com.arktech.superaccountant.workflow.service.compliance.ResponsibilityStartSettingsService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/v1/compliance")
public class ComplianceSettingsController {

    @Autowired
    private ResponsibilityStartSettingsService responsibilityStartSettingsService;

    @Autowired
    private BaselineConfirmationService baselineConfirmationService;

    @Autowired
    private FilerProfileSettingsService filerProfileSettingsService;

    @Autowired
    private FinancialYearCloseService financialYearCloseService;

    @GetMapping("/responsibility-start")
    public ResponseEntity<?> listResponsibilityStarts(@AuthenticationPrincipal UserDetailsImpl principal) {
        UUID orgId = principal.getOrganizationId();
        if (orgId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "User is not linked to an organization."));
        }
        return ResponseEntity.ok(responsibilityStartSettingsService.list(orgId));
    }

    @PutMapping("/responsibility-start")
    @PreAuthorize("hasRole('ACCOUNTANT')")
    public ResponseEntity<?> putResponsibilityStart(@AuthenticationPrincipal UserDetailsImpl principal,
            @Valid @RequestBody ResponsibilityStartRequest request) {
        UUID orgId = principal.getOrganizationId();
        if (orgId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "User is not linked to an organization."));
        }
        try {
            WorkflowType type = WorkflowType.valueOf(request.getType());
            return ResponseEntity.ok(responsibilityStartSettingsService
                    .set(orgId, principal.getUsername(), type, request.getFirstPeriodStart()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Unknown type: " + request.getType()));
        } catch (WorkflowBadRequestException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (WorkflowConflictException e) {
            return ResponseEntity.status(409).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/baseline")
    public ResponseEntity<?> baselineStatus(@AuthenticationPrincipal UserDetailsImpl principal) {
        UUID orgId = principal.getOrganizationId();
        if (orgId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "User is not linked to an organization."));
        }
        return ResponseEntity.ok(baselineConfirmationService.status(orgId));
    }

    @PostMapping("/baseline/confirm")
    @PreAuthorize("hasRole('ACCOUNTANT')")
    public ResponseEntity<?> confirmBaseline(@AuthenticationPrincipal UserDetailsImpl principal,
            @RequestBody(required = false) ConfirmBaselineRequest request) {
        UUID orgId = principal.getOrganizationId();
        if (orgId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "User is not linked to an organization."));
        }
        try {
            return ResponseEntity.ok(baselineConfirmationService.confirm(orgId, principal.getUsername(),
                    request == null ? null : request.getUploadJobId()));
        } catch (UploadNotFoundException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Upload not found"));
        } catch (WorkflowConflictException e) {
            return ResponseEntity.status(409).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/filer-profile")
    public ResponseEntity<?> listFilerProfiles(@AuthenticationPrincipal UserDetailsImpl principal) {
        UUID orgId = principal.getOrganizationId();
        if (orgId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "User is not linked to an organization."));
        }
        return ResponseEntity.ok(filerProfileSettingsService.list(orgId));
    }

    @PutMapping("/filer-profile")
    @PreAuthorize("hasRole('ACCOUNTANT')")
    public ResponseEntity<?> putFilerProfile(@AuthenticationPrincipal UserDetailsImpl principal,
            @Valid @RequestBody FilerProfileRequest request) {
        UUID orgId = principal.getOrganizationId();
        if (orgId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "User is not linked to an organization."));
        }
        try {
            return ResponseEntity.ok(filerProfileSettingsService.put(orgId, principal.getUsername(), request));
        } catch (WorkflowBadRequestException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (WorkflowConflictException e) {
            return ResponseEntity.status(409).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/financial-years")
    public ResponseEntity<?> listFinancialYears(@AuthenticationPrincipal UserDetailsImpl principal) {
        UUID orgId = principal.getOrganizationId();
        if (orgId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "User is not linked to an organization."));
        }
        return ResponseEntity.ok(financialYearCloseService.listYears(orgId));
    }

    @PostMapping("/financial-years/{fy}/close")
    @PreAuthorize("hasRole('ACCOUNTANT')")
    public ResponseEntity<?> closeFinancialYear(@AuthenticationPrincipal UserDetailsImpl principal,
            @PathVariable int fy,
            @RequestBody(required = false) CloseFinancialYearRequest request) {
        UUID orgId = principal.getOrganizationId();
        if (orgId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "User is not linked to an organization."));
        }
        try {
            return ResponseEntity.ok(financialYearCloseService.close(orgId, principal.getUsername(), fy,
                    request == null ? new CloseFinancialYearRequest() : request));
        } catch (WorkflowBadRequestException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (WorkflowConflictException e) {
            return ResponseEntity.status(409).body(Map.of("error", e.getMessage()));
        }
    }
}
