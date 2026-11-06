package com.arktech.superaccountant.masters.controllers;

import com.arktech.superaccountant.login.security.services.UserDetailsImpl;
import com.arktech.superaccountant.masters.models.OrganizationSetting;
import com.arktech.superaccountant.masters.services.OrganizationSettingsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Per-organization validation settings. Any member reads; only ACCOUNTANT and OWNER change. */
@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/v1/validation-settings")
public class ValidationSettingsController {

    public record SettingsResponse(boolean requireHsnSacOnExpense, String updatedBy, Instant updatedAt) {}

    public record UpdateRequest(Boolean requireHsnSacOnExpense) {}

    @Autowired
    private OrganizationSettingsService settingsService;

    @PreAuthorize("isAuthenticated()")
    @GetMapping
    public ResponseEntity<?> get(@AuthenticationPrincipal UserDetailsImpl principal) {
        UUID orgId = principal.getOrganizationId();
        if (orgId == null) {
            return ResponseEntity.badRequest().body("User is not linked to an organization.");
        }
        return ResponseEntity.ok(toResponse(orgId));
    }

    @PreAuthorize("hasRole('OWNER') or hasRole('ACCOUNTANT')")
    @PutMapping
    public ResponseEntity<?> update(@RequestBody UpdateRequest request,
                                    @AuthenticationPrincipal UserDetailsImpl principal) {
        UUID orgId = principal.getOrganizationId();
        if (orgId == null) {
            return ResponseEntity.badRequest().body("User is not linked to an organization.");
        }
        if (request == null || request.requireHsnSacOnExpense() == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "requireHsnSacOnExpense is required."));
        }
        settingsService.setRequireHsnSacOnExpense(orgId, request.requireHsnSacOnExpense(), principal.getUsername());
        return ResponseEntity.ok(toResponse(orgId));
    }

    private SettingsResponse toResponse(UUID orgId) {
        OrganizationSetting s = settingsService.find(orgId, OrganizationSettingsService.REQUIRE_HSN_SAC_ON_EXPENSE);
        return new SettingsResponse(settingsService.requireHsnSacOnExpense(orgId),
                s == null ? null : s.getUpdatedBy(), s == null ? null : s.getUpdatedAt());
    }
}
