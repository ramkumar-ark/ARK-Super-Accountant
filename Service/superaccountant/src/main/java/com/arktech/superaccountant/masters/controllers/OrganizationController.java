package com.arktech.superaccountant.masters.controllers;

import com.arktech.superaccountant.login.models.ERole;
import com.arktech.superaccountant.login.models.User;
import com.arktech.superaccountant.login.payload.response.MessageResponse;
import com.arktech.superaccountant.login.repository.UserRepository;
import com.arktech.superaccountant.login.security.jwt.JwtUtils;
import com.arktech.superaccountant.login.security.services.UserDetailsImpl;
import com.arktech.superaccountant.masters.models.Organization;
import com.arktech.superaccountant.masters.models.OrganizationInvite;
import com.arktech.superaccountant.masters.models.UserOrganization;
import com.arktech.superaccountant.masters.payload.request.CreateOrganizationRequest;
import com.arktech.superaccountant.masters.repository.OrganizationInviteRepository;
import com.arktech.superaccountant.masters.repository.OrganizationRepository;
import com.arktech.superaccountant.masters.repository.UserOrganizationRepository;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/organizations")
public class OrganizationController {

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserOrganizationRepository userOrganizationRepository;

    @Autowired
    private OrganizationInviteRepository organizationInviteRepository;

    @Autowired
    private JwtUtils jwtUtils;

    @PreAuthorize("hasRole('OWNER') or hasRole('ACCOUNTANT')")
    @PostMapping
    public ResponseEntity<?> createOrganization(
            @Valid @RequestBody CreateOrganizationRequest request,
            @AuthenticationPrincipal UserDetailsImpl principal) {

        Organization org = new Organization(request.getName());
        org.setGstin(request.getGstin());
        org.setPan(request.getPan());
        org.setRegisteredAddress(request.getRegisteredAddress());
        org.setFinancialYearStart(request.getFinancialYearStart() > 0 ? request.getFinancialYearStart() : 4);
        final Organization savedOrg = organizationRepository.save(org);

        // Link the creating user to this organization as OWNER
        userRepository.findById(principal.getId()).ifPresent(user -> {
            UserOrganization membership = new UserOrganization(user, savedOrg, ERole.ROLE_OWNER);
            userOrganizationRepository.save(membership);
        });

        return ResponseEntity.status(201).body(Map.of(
                "id", savedOrg.getId(),
                "name", savedOrg.getName(),
                "gstin", savedOrg.getGstin() != null ? savedOrg.getGstin() : "",
                "pan", savedOrg.getPan() != null ? savedOrg.getPan() : "",
                "registeredAddress", savedOrg.getRegisteredAddress() != null ? savedOrg.getRegisteredAddress() : "",
                "financialYearStart", savedOrg.getFinancialYearStart(),
                "createdAt", savedOrg.getCreatedAt()
        ));
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{id}")
    public ResponseEntity<?> getOrganization(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetailsImpl principal) {

        Optional<UserOrganization> membership = userOrganizationRepository
                .findByUserIdAndOrganizationId(principal.getId(), id);
        if (membership.isEmpty()) {
            return ResponseEntity.status(403).body(new MessageResponse("Access denied to this organization"));
        }

        return organizationRepository.findById(id)
                .map(org -> ResponseEntity.ok(Map.of(
                        "id", org.getId(),
                        "name", org.getName(),
                        "gstin", org.getGstin() != null ? org.getGstin() : "",
                        "pan", org.getPan() != null ? org.getPan() : "",
                        "registeredAddress", org.getRegisteredAddress() != null ? org.getRegisteredAddress() : "",
                        "financialYearStart", org.getFinancialYearStart(),
                        "createdAt", org.getCreatedAt()
                )))
                .orElse(ResponseEntity.notFound().build());
    }

    @PreAuthorize("hasRole('OWNER') or hasRole('ACCOUNTANT')")
    @PostMapping("/{id}/invites")
    public ResponseEntity<?> createInvite(
            @PathVariable UUID id,
            @RequestParam String role,
            @AuthenticationPrincipal UserDetailsImpl principal) {

        Optional<UserOrganization> membership = userOrganizationRepository
                .findByUserIdAndOrganizationId(principal.getId(), id);
        if (membership.isEmpty()) {
            return ResponseEntity.status(403).body(new MessageResponse("Access denied to this organization"));
        }

        Optional<Organization> orgOpt = organizationRepository.findById(id);
        if (orgOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        ERole inviteRole;
        try {
            inviteRole = ERole.valueOf("ROLE_" + role.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new MessageResponse("Invalid role: " + role));
        }

        User callerUser = userRepository.findById(principal.getId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        OrganizationInvite invite = new OrganizationInvite(orgOpt.get(), inviteRole, callerUser);
        organizationInviteRepository.save(invite);

        return ResponseEntity.ok(Map.of(
                "token", invite.getToken(),
                "expiresAt", invite.getExpiresAt(),
                "role", role,
                "organizationId", id
        ));
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/me/list")
    public ResponseEntity<?> getMyOrganizations(@AuthenticationPrincipal UserDetailsImpl principal) {
        List<UserOrganization> memberships = userOrganizationRepository.findByUserId(principal.getId());

        List<Map<String, Object>> result = memberships.stream().map(m -> {
            Map<String, Object> entry = new java.util.HashMap<>();
            entry.put("organizationId", m.getOrganization().getId());
            entry.put("organizationName", m.getOrganization().getName());
            entry.put("role", m.getRole().name());
            entry.put("isActive", m.getOrganization().getId().equals(principal.getOrganizationId()));
            return entry;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/{id}/select")
    public ResponseEntity<?> selectOrganization(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetailsImpl principal) {

        Optional<UserOrganization> membership = userOrganizationRepository
                .findByUserIdAndOrganizationId(principal.getId(), id);
        if (membership.isEmpty()) {
            return ResponseEntity.status(403)
                    .body(new MessageResponse("Could not switch to that organization. You may no longer be a member. Refresh and try again."));
        }

        String newJwt = jwtUtils.generateJwtTokenForUser(principal.getUsername(), id);

        return ResponseEntity.ok(Map.of(
                "token", newJwt,
                "organizationId", id,
                "organizationName", membership.get().getOrganization().getName(),
                "role", membership.get().getRole().name()
        ));
    }
}
