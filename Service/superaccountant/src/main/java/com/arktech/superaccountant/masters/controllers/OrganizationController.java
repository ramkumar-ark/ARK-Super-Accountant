package com.arktech.superaccountant.masters.controllers;

import com.arktech.superaccountant.login.repository.UserRepository;
import com.arktech.superaccountant.login.security.services.UserDetailsImpl;
import com.arktech.superaccountant.masters.models.Organization;
import com.arktech.superaccountant.masters.payload.request.CreateOrganizationRequest;
import com.arktech.superaccountant.masters.repository.OrganizationRepository;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/organizations")
public class OrganizationController {

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private UserRepository userRepository;

    @PostMapping
    public ResponseEntity<?> createOrganization(
            @Valid @RequestBody CreateOrganizationRequest request,
            @AuthenticationPrincipal UserDetailsImpl principal) {

        Organization org = new Organization(request.getName());
        org = organizationRepository.save(org);

        // Link the creating user to this organization
        final java.util.UUID orgId = org.getId();
        userRepository.findById(principal.getId()).ifPresent(user -> {
            user.setOrganizationId(orgId);
            userRepository.save(user);
        });

        return ResponseEntity.status(201).body(Map.of(
                "id", org.getId(),
                "name", org.getName(),
                "createdAt", org.getCreatedAt()
        ));
    }
}
