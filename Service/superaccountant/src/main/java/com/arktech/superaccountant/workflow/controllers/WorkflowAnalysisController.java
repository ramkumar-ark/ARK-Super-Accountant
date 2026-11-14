package com.arktech.superaccountant.workflow.controllers;

import com.arktech.superaccountant.login.security.services.UserDetailsImpl;
import com.arktech.superaccountant.workflow.payload.response.WorkflowResponse;
import com.arktech.superaccountant.workflow.service.WorkflowBadRequestException;
import com.arktech.superaccountant.workflow.service.WorkflowConflictException;
import com.arktech.superaccountant.workflow.service.WorkflowService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Retry path for the analysis run. The normal trigger is the upload-bound event; a failed run is
 * re-run here (same service, same rules) and returns the workflow it advanced.
 */
@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/v1/workflows")
public class WorkflowAnalysisController {

    @Autowired
    private WorkflowService workflowService;

    @Autowired
    private com.arktech.superaccountant.workflow.analysis.WorkflowAnalysisService analysisService;

    @PreAuthorize("hasRole('ACCOUNTANT') or hasRole('OPERATOR')")
    @PostMapping("/{id}/analyze")
    public ResponseEntity<?> analyze(@PathVariable UUID id,
                                     @AuthenticationPrincipal UserDetailsImpl principal) {
        if (principal.getOrganizationId() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "User is not linked to an organization."));
        }
        try {
            return ResponseEntity.ok(WorkflowResponse.from(
                    analysisService.run(principal.getOrganizationId(), id, principal.getUsername())));
        } catch (WorkflowBadRequestException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (WorkflowConflictException e) {
            return ResponseEntity.status(409).body(Map.of("error", e.getMessage()));
        }
    }
}
