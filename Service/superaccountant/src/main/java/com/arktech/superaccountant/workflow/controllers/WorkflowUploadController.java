package com.arktech.superaccountant.workflow.controllers;

import com.arktech.superaccountant.daybook.services.DayBookIntakeException;
import com.arktech.superaccountant.login.security.services.UserDetailsImpl;
import com.arktech.superaccountant.workflow.payload.request.UseUploadRequest;
import com.arktech.superaccountant.workflow.payload.response.WorkflowResponse;
import com.arktech.superaccountant.workflow.payload.response.WorkflowUploadResponse;
import com.arktech.superaccountant.workflow.service.UploadBindResult;
import com.arktech.superaccountant.workflow.service.UploadBindingService;
import com.arktech.superaccountant.workflow.service.UploadCoverageException;
import com.arktech.superaccountant.workflow.service.UploadNotFoundException;
import com.arktech.superaccountant.workflow.service.WorkflowBadRequestException;
import com.arktech.superaccountant.workflow.service.WorkflowConflictException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/v1/workflows")
public class WorkflowUploadController {

    @Autowired
    private UploadBindingService uploadBindingService;

    @PostMapping("/{id}/upload")
    @PreAuthorize("hasRole('ACCOUNTANT') or hasRole('OPERATOR')")
    public ResponseEntity<?> uploadAndBind(
            @AuthenticationPrincipal UserDetailsImpl principal,
            @PathVariable UUID id,
            @RequestParam("file") MultipartFile file,
            @RequestParam("coverageFrom") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate coverageFrom,
            @RequestParam("coverageTo") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate coverageTo) {
        UUID orgId = principal.getOrganizationId();
        if (orgId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "User is not linked to an organization."));
        }
        try {
            UploadBindResult result = uploadBindingService.uploadAndBind(orgId, id, file,
                    coverageFrom, coverageTo, principal.getUsername());
            return ResponseEntity.ok(new WorkflowUploadResponse(
                    WorkflowResponse.from(result.workflow()), result.warnings()));
        } catch (WorkflowBadRequestException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (WorkflowConflictException e) {
            return ResponseEntity.status(409).body(Map.of("error", e.getMessage()));
        } catch (UploadCoverageException e) {
            return ResponseEntity.status(422).body(Map.of("error", e.getMessage()));
        } catch (DayBookIntakeException e) {
            return ResponseEntity.status(e.getStatus()).body(e.getBody());
        }
    }

    @PostMapping("/{id}/use-upload")
    @PreAuthorize("hasRole('ACCOUNTANT') or hasRole('OPERATOR')")
    public ResponseEntity<?> useUpload(
            @AuthenticationPrincipal UserDetailsImpl principal,
            @PathVariable UUID id,
            @jakarta.validation.Valid @RequestBody UseUploadRequest request) {
        UUID orgId = principal.getOrganizationId();
        if (orgId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "User is not linked to an organization."));
        }
        try {
            return ResponseEntity.ok(WorkflowResponse.from(
                    uploadBindingService.useUpload(orgId, id, request.getUploadJobId(),
                            principal.getUsername())));
        } catch (UploadNotFoundException e) {
            return ResponseEntity.status(404).body(Map.of("error", "Upload not found"));
        } catch (WorkflowBadRequestException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (WorkflowConflictException e) {
            return ResponseEntity.status(409).body(Map.of("error", e.getMessage()));
        } catch (UploadCoverageException e) {
            return ResponseEntity.status(422).body(Map.of("error", e.getMessage()));
        }
    }
}
