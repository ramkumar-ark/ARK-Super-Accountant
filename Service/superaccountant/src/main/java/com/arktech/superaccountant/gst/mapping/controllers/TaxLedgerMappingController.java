package com.arktech.superaccountant.gst.mapping.controllers;

import com.arktech.superaccountant.gst.mapping.TaxLedgerMappingGate;
import com.arktech.superaccountant.gst.mapping.TaxLedgerMappingService;
import com.arktech.superaccountant.gst.mapping.payload.SaveTaxLedgerMappingRequest;
import com.arktech.superaccountant.gst.mapping.payload.TaxLedgerMappingView;
import com.arktech.superaccountant.login.security.services.UserDetailsImpl;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/gst/tax-ledger-mapping")
public class TaxLedgerMappingController {

    private final TaxLedgerMappingService mappingService;

    private final TaxLedgerMappingGate mappingGate;

    public TaxLedgerMappingController(TaxLedgerMappingService mappingService, TaxLedgerMappingGate mappingGate) {
        this.mappingService = mappingService;
        this.mappingGate = mappingGate;
    }

    @GetMapping
    public ResponseEntity<TaxLedgerMappingView> view(Authentication authentication) {
        UUID organizationId = organizationId(authentication);
        if (organizationId == null) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(mappingService.view(organizationId, mappingGate));
    }

    @PutMapping
    @PreAuthorize("hasRole('ACCOUNTANT')")
    public ResponseEntity<?> save(Authentication authentication,
            @RequestBody SaveTaxLedgerMappingRequest request) {
        UUID organizationId = organizationId(authentication);
        if (organizationId == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "User is not linked to an organization."));
        }
        try {
            mappingService.save(organizationId, authentication.getName(), request);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    private UUID organizationId(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof UserDetailsImpl principal) {
            return principal.getOrganizationId();
        }
        return null;
    }
}
