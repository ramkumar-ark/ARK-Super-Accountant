package com.arktech.superaccountant.tds.mapping.controllers;

import com.arktech.superaccountant.login.security.services.UserDetailsImpl;
import com.arktech.superaccountant.tds.mapping.TdsLedgerMappingGate;
import com.arktech.superaccountant.tds.mapping.TdsLedgerMappingService;
import com.arktech.superaccountant.tds.mapping.payload.SaveTdsLedgerMappingRequest;
import com.arktech.superaccountant.tds.mapping.payload.TdsLedgerMappingView;
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
@RequestMapping("/api/v1/tds/ledger-mapping")
public class TdsLedgerMappingController {

    private final TdsLedgerMappingService mappingService;

    private final TdsLedgerMappingGate mappingGate;

    public TdsLedgerMappingController(TdsLedgerMappingService mappingService, TdsLedgerMappingGate mappingGate) {
        this.mappingService = mappingService;
        this.mappingGate = mappingGate;
    }

    @GetMapping
    public ResponseEntity<TdsLedgerMappingView> view(Authentication authentication) {
        UUID organizationId = organizationId(authentication);
        if (organizationId == null) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(mappingService.view(organizationId, mappingGate));
    }

    @PutMapping
    @PreAuthorize("hasRole('ACCOUNTANT')")
    public ResponseEntity<?> save(Authentication authentication,
            @RequestBody SaveTdsLedgerMappingRequest request) {
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
