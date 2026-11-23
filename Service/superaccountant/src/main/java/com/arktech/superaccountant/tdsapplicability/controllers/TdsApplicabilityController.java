package com.arktech.superaccountant.tdsapplicability.controllers;

import com.arktech.superaccountant.login.payload.response.MessageResponse;
import com.arktech.superaccountant.login.security.services.UserDetailsImpl;
import com.arktech.superaccountant.masters.models.Organization;
import com.arktech.superaccountant.masters.repository.OrganizationRepository;
import com.arktech.superaccountant.masters.repository.UserOrganizationRepository;
import com.arktech.superaccountant.tdsapplicability.TdsApplicabilityService;
import com.arktech.superaccountant.tdsapplicability.EntityClass;
import com.arktech.superaccountant.tdsapplicability.models.TdsApplicability;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/v1/organizations")
public class TdsApplicabilityController {

    @Autowired
    private TdsApplicabilityService service;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private UserOrganizationRepository userOrganizationRepository;

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
}
