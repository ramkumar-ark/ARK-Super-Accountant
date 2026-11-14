package com.arktech.superaccountant.workflow.controllers;

import com.arktech.superaccountant.login.security.services.UserDetailsImpl;
import com.arktech.superaccountant.workflow.models.Workflow;
import com.arktech.superaccountant.workflow.models.WorkflowEvent;
import com.arktech.superaccountant.workflow.models.WorkflowStage;
import com.arktech.superaccountant.workflow.models.WorkflowType;
import com.arktech.superaccountant.workflow.payload.request.FilingRequest;
import com.arktech.superaccountant.workflow.payload.request.StartWorkflowRequest;
import com.arktech.superaccountant.workflow.payload.request.UnfinalizeRequest;
import com.arktech.superaccountant.workflow.payload.response.WorkflowDetailResponse;
import com.arktech.superaccountant.workflow.payload.response.WorkflowResponse;
import com.arktech.superaccountant.workflow.service.WorkflowBadRequestException;
import com.arktech.superaccountant.workflow.service.WorkflowConflictException;
import com.arktech.superaccountant.workflow.service.WorkflowService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.function.Supplier;
import java.util.Map;
import java.util.UUID;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/v1/workflows")
public class WorkflowController {

    @Autowired
    private WorkflowService workflowService;

    @GetMapping
    public ResponseEntity<?> list(@AuthenticationPrincipal UserDetailsImpl principal,
                                  @RequestParam(required = false) String type,
                                  @RequestParam(required = false) Integer fy,
                                  @RequestParam(required = false) String stage) {
        UUID orgId = principal.getOrganizationId();
        if (orgId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "User is not linked to an organization."));
        }
        return ResponseEntity.ok(workflowService.list(orgId, type, fy, stage)
                .stream().map(WorkflowResponse::from).toList());
    }

    @PostMapping
    @PreAuthorize("hasRole('ACCOUNTANT') or hasRole('OPERATOR')")
    public ResponseEntity<?> start(@AuthenticationPrincipal UserDetailsImpl principal,
                                   @Valid @RequestBody StartWorkflowRequest request) {
        UUID orgId = principal.getOrganizationId();
        if (orgId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "User is not linked to an organization."));
        }
        try {
            WorkflowType type = WorkflowType.valueOf(request.getType());
            Workflow w = workflowService.start(orgId, principal.getUsername(), type,
                    request.getPeriodKey());
            return ResponseEntity.ok(WorkflowResponse.from(w));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (WorkflowBadRequestException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (WorkflowConflictException e) {
            return ResponseEntity.status(409).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@AuthenticationPrincipal UserDetailsImpl principal,
                                 @PathVariable UUID id) {
        UUID orgId = principal.getOrganizationId();
        if (orgId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "User is not linked to an organization."));
        }
        try {
            Workflow w = workflowService.get(orgId, id);
            return ResponseEntity.ok(new WorkflowDetailResponse(
                    WorkflowResponse.from(w),
                    workflowService.evaluateDependencies(w),
                    workflowService.blockingFindingCount(w.getId()),
                    w.getBaselineConfirmedAt() != null,
                    w.getId()));
        } catch (WorkflowBadRequestException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/{id}/events")
    public ResponseEntity<?> events(@AuthenticationPrincipal UserDetailsImpl principal,
                                    @PathVariable UUID id) {
        UUID orgId = principal.getOrganizationId();
        if (orgId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "User is not linked to an organization."));
        }
        try {
            List<WorkflowEvent> events = workflowService.events(orgId, id);
            return ResponseEntity.ok(events);
        } catch (WorkflowBadRequestException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/{id}/advance")
    @PreAuthorize("hasRole('ACCOUNTANT')")
    public ResponseEntity<?> advance(@AuthenticationPrincipal UserDetailsImpl principal,
                                     @PathVariable UUID id) {
        return handle(principal, () -> WorkflowResponse.from(
                workflowService.advanceToPrepare(principal.getOrganizationId(),
                        principal.getUsername(), id)));
    }

    @PostMapping("/{id}/finalize")
    @PreAuthorize("hasRole('ACCOUNTANT')")
    public ResponseEntity<?> finalizeWorkflow(@AuthenticationPrincipal UserDetailsImpl principal,
                                              @PathVariable UUID id) {
        return handle(principal, () -> WorkflowResponse.from(
                workflowService.finalize(principal.getOrganizationId(),
                        principal.getUsername(), id)));
    }

    @PostMapping("/{id}/unfinalize")
    @PreAuthorize("hasRole('ACCOUNTANT')")
    public ResponseEntity<?> unfinalize(@AuthenticationPrincipal UserDetailsImpl principal,
                                        @PathVariable UUID id,
                                        @Valid @RequestBody UnfinalizeRequest request) {
        return handle(principal, () -> WorkflowResponse.from(
                workflowService.unfinalize(principal.getOrganizationId(),
                        principal.getUsername(), id, request.getReason())));
    }

    @PostMapping("/{id}/filing")
    @PreAuthorize("hasRole('ACCOUNTANT')")
    public ResponseEntity<?> recordFiling(@AuthenticationPrincipal UserDetailsImpl principal,
                                          @PathVariable UUID id,
                                          @Valid @RequestBody FilingRequest request) {
        return handle(principal, () -> WorkflowResponse.from(
                workflowService.recordFiling(principal.getOrganizationId(),
                        principal.getUsername(), id, request.getRef(), request.getDate())));
    }

    private ResponseEntity<?> handle(UserDetailsImpl principal, Supplier<Object> action) {
        UUID orgId = principal.getOrganizationId();
        if (orgId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "User is not linked to an organization."));
        }
        try {
            return ResponseEntity.ok(action.get());
        } catch (WorkflowBadRequestException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (WorkflowConflictException e) {
            return ResponseEntity.status(409).body(Map.of("error", e.getMessage()));
        }
    }
}
