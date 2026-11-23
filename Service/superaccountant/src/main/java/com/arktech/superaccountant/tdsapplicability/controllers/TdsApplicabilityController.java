package com.arktech.superaccountant.tdsapplicability.controllers;

import com.arktech.superaccountant.login.payload.response.MessageResponse;
import com.arktech.superaccountant.login.security.services.UserDetailsImpl;
import com.arktech.superaccountant.masters.models.Organization;
import com.arktech.superaccountant.masters.repository.OrganizationRepository;
import com.arktech.superaccountant.masters.repository.UserOrganizationRepository;
import com.arktech.superaccountant.tdsapplicability.TdsApplicabilityPromptService;
import com.arktech.superaccountant.tdsapplicability.TdsApplicabilityService;
import com.arktech.superaccountant.tdsapplicability.EntityClass;
import com.arktech.superaccountant.tdsapplicability.PanOnlyTdsAdvisoryService;
import com.arktech.superaccountant.tdsapplicability.models.TdsApplicability;
import com.arktech.superaccountant.tdsapplicability.models.TdsApplicabilityBasis;
import com.arktech.superaccountant.tdsapplicability.models.TurnoverNature;
import com.arktech.superaccountant.workflow.models.WorkflowStage;
import com.arktech.superaccountant.workflow.models.WorkflowType;
import com.arktech.superaccountant.workflow.repository.WorkflowRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/v1/organizations")
public class TdsApplicabilityController {

    @Autowired
    private TdsApplicabilityService service;

    @Autowired
    private TdsApplicabilityPromptService promptService;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private UserOrganizationRepository userOrganizationRepository;

    @Autowired
    private WorkflowRepository workflowRepository;

    @Autowired
    private PanOnlyTdsAdvisoryService panOnlyAdvisoryService;

    /**
     * Read-only applicability view: the effective deductor code, its source,
     * the TAN and the resolved applicability for every year from coverage start.
     */
    public record TdsApplicabilityView(
            String entityCode,
            String entityCodeSource,
            String tan,
            List<TdsApplicability> years) {
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{id}/tds-applicability")
    public ResponseEntity<?> get(@PathVariable UUID id,
                                 @AuthenticationPrincipal UserDetailsImpl principal) {

        var membership = userOrganizationRepository.findByUserIdAndOrganizationId(principal.getId(), id);
        if (membership.isEmpty()) {
            return ResponseEntity.status(403).body(new MessageResponse("Access denied to this organization"));
        }

        Optional<Organization> orgOpt = organizationRepository.findById(id);
        if (orgOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Organization org = orgOpt.get();

        EntityClass.EffectiveCode effective = EntityClass.effectiveCode(org.getPan(), org.getDeductorType());
        return ResponseEntity.ok(new TdsApplicabilityView(
                effective.code(),
                effective.source(),
                org.getTan(),
                service.years(id)));
    }

    /** PUT body for answering or changing one year's applicability. */
    public record UpdateTdsApplicabilityRequest(
            Boolean applicable,
            BigDecimal priorYearTurnover,
            TurnoverNature turnoverNature,
            String note) {
    }

    private static final String INDIVIDUAL_HUF_CODE = "Q";

    /** FY 2025 renders as "FY 2025-26", matching the client's fyLabel. */
    private static String fyLabel(int financialYear) {
        return "FY " + financialYear + "-" + String.format("%02d", (financialYear + 1) % 100);
    }

    @PreAuthorize("hasRole('OWNER') or hasRole('ACCOUNTANT')")
    @PutMapping("/{id}/tds-applicability/{fy}")
    public ResponseEntity<?> update(@PathVariable UUID id, @PathVariable int fy,
                                    @RequestBody UpdateTdsApplicabilityRequest request,
                                    @AuthenticationPrincipal UserDetailsImpl principal) {

        var membership = userOrganizationRepository.findByUserIdAndOrganizationId(principal.getId(), id);
        if (membership.isEmpty()) {
            return ResponseEntity.status(403).body(new MessageResponse("Access denied to this organization"));
        }

        Optional<Organization> orgOpt = organizationRepository.findById(id);
        if (orgOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Organization org = orgOpt.get();

        EntityClass.EffectiveCode effective = EntityClass.effectiveCode(org.getPan(), org.getDeductorType());
        if (effective.code() != null && !effective.code().equals(INDIVIDUAL_HUF_CODE)) {
            return ResponseEntity.badRequest().body(Map.of("error",
                    "Entity type " + effective.code() + " always deducts TDS"));
        }

        boolean applicable = Boolean.TRUE.equals(request.applicable());
        if (!applicable && workflowRepository
                .existsByOrganizationIdAndTypeInAndFinancialYearAndStageInAndActiveTrue(id,
                        List.of(WorkflowType.TDS_PAYMENT, WorkflowType.TDS_26Q), fy,
                        List.of(WorkflowStage.FINALIZED, WorkflowStage.FILED))) {
            return ResponseEntity.status(409).body(new MessageResponse(
                    fyLabel(fy) + " has a finalized TDS workflow; unfinalize it first"));
        }

        return ResponseEntity.ok(service.recordYear(id, fy, applicable, TdsApplicabilityBasis.DECLARED,
                request.priorYearTurnover(), request.turnoverNature(), request.note(),
                principal.getUsername()));
    }

    // ---- Task 8: applicability prompts ----

    /** PAN-only 194M / 194-IB advisories for a non-deductor year of an individual/HUF org. */
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{id}/tds-applicability/{fy}/pan-only-advisories")
    public ResponseEntity<?> panOnlyAdvisories(@PathVariable UUID id, @PathVariable int fy,
                                               @AuthenticationPrincipal UserDetailsImpl principal) {
        var membership = userOrganizationRepository.findByUserIdAndOrganizationId(principal.getId(), id);
        if (membership.isEmpty()) {
            return ResponseEntity.status(403).body(new MessageResponse("Access denied to this organization"));
        }
        if (organizationRepository.findById(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(panOnlyAdvisoryService.advisories(id, fy));
    }


    /** POST body for confirming a prompt: whether TDS applies, with the books figure when known. */
    public record ConfirmPromptRequest(
            Boolean applicable,
            BigDecimal priorYearTurnover,
            TurnoverNature turnoverNature) {
    }

    /** POST body for dismissing a prompt; the reason must be non-blank. */
    public record DismissPromptRequest(String reason) {
    }

    /** Open prompts for the dashboard banner, plus the lazily created legacy note. */
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{id}/tds-applicability/prompts")
    public ResponseEntity<?> prompts(@PathVariable UUID id,
                                     @AuthenticationPrincipal UserDetailsImpl principal) {
        var membership = userOrganizationRepository.findByUserIdAndOrganizationId(principal.getId(), id);
        if (membership.isEmpty()) {
            return ResponseEntity.status(403).body(new MessageResponse("Access denied to this organization"));
        }
        if (organizationRepository.findById(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(promptService.openPrompts(id));
    }

    @PreAuthorize("hasRole('OWNER') or hasRole('ACCOUNTANT')")
    @PostMapping("/{id}/tds-applicability/prompts/{promptId}/confirm")
    public ResponseEntity<?> confirmPrompt(@PathVariable UUID id, @PathVariable UUID promptId,
                                           @RequestBody ConfirmPromptRequest request,
                                           @AuthenticationPrincipal UserDetailsImpl principal) {
        if (userOrganizationRepository
                .findByUserIdAndOrganizationId(principal.getId(), id).isEmpty()) {
            return ResponseEntity.status(403).body(new MessageResponse("Access denied to this organization"));
        }
        if (organizationRepository.findById(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        boolean applicable = Boolean.TRUE.equals(request.applicable());
        try {
            return ResponseEntity.ok(promptService.confirm(id, promptId, applicable,
                    request.priorYearTurnover(), request.turnoverNature(), principal.getUsername()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new MessageResponse(e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(new MessageResponse(e.getMessage()));
        }
    }

    @PreAuthorize("hasRole('OWNER') or hasRole('ACCOUNTANT')")
    @PostMapping("/{id}/tds-applicability/prompts/{promptId}/dismiss")
    public ResponseEntity<?> dismissPrompt(@PathVariable UUID id, @PathVariable UUID promptId,
                                           @RequestBody DismissPromptRequest request,
                                           @AuthenticationPrincipal UserDetailsImpl principal) {
        if (userOrganizationRepository
                .findByUserIdAndOrganizationId(principal.getId(), id).isEmpty()) {
            return ResponseEntity.status(403).body(new MessageResponse("Access denied to this organization"));
        }
        if (organizationRepository.findById(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        if (request == null || request.reason() == null || request.reason().isBlank()) {
            return ResponseEntity.badRequest().body(new MessageResponse("A reason is required to dismiss"));
        }
        try {
            return ResponseEntity.ok(promptService.dismiss(id, promptId, request.reason(),
                    principal.getUsername()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new MessageResponse(e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(new MessageResponse(e.getMessage()));
        }
    }
}
