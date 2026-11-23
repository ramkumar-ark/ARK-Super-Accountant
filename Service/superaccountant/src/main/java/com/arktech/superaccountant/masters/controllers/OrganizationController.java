package com.arktech.superaccountant.masters.controllers;

import com.arktech.superaccountant.login.models.ERole;
import com.arktech.superaccountant.login.models.User;
import com.arktech.superaccountant.login.payload.response.MessageResponse;
import com.arktech.superaccountant.login.repository.UserRepository;
import com.arktech.superaccountant.login.security.jwt.JwtUtils;
import com.arktech.superaccountant.login.security.services.UserDetailsImpl;
import com.arktech.superaccountant.masters.models.InterestPayerType;
import com.arktech.superaccountant.masters.models.DeductorType;
import com.arktech.superaccountant.masters.models.Organization;
import com.arktech.superaccountant.masters.models.OrganizationInvite;
import com.arktech.superaccountant.masters.models.UserOrganization;
import com.arktech.superaccountant.masters.payload.request.CreateOrganizationRequest;
import com.arktech.superaccountant.masters.payload.request.UpdateDeductorDetailsRequest;
import com.arktech.superaccountant.masters.repository.OrganizationInviteRepository;
import com.arktech.superaccountant.masters.repository.OrganizationRepository;
import com.arktech.superaccountant.masters.repository.UserOrganizationRepository;
import com.arktech.superaccountant.tdsapplicability.EntityClass;
import com.arktech.superaccountant.tdsapplicability.TdsApplicabilityService;
import com.arktech.superaccountant.tdsapplicability.models.TurnoverNature;
import com.arktech.superaccountant.workflow.service.period.PeriodCalendar;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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

    @Autowired
    private TdsApplicabilityService tdsApplicabilityService;

    private static final String INDIVIDUAL_HUF_CODE = "Q";
    private static final BigDecimal BUSINESS_TURNOVER_LIMIT = new BigDecimal("10000000");
    private static final BigDecimal PROFESSION_TURNOVER_LIMIT = new BigDecimal("5000000");

    @PreAuthorize("hasRole('OWNER') or hasRole('ACCOUNTANT')")
    @PostMapping
    public ResponseEntity<?> createOrganization(
            @Valid @RequestBody CreateOrganizationRequest request,
            @AuthenticationPrincipal UserDetailsImpl principal) {

        String pan = request.getPan() != null ? request.getPan().trim().toUpperCase() : "";
        String effectiveType = EntityClass.deductorCodeFromPan(pan).orElse(null);
        if (effectiveType == null && request.getDeductorType() != null
                && !request.getDeductorType().isBlank()) {
            String requestedType = request.getDeductorType().trim().toUpperCase();
            if (!DeductorType.isValid(requestedType)) {
                return ResponseEntity.badRequest().body(Map.of("error", "Invalid deductor type."));
            }
            effectiveType = requestedType;
        }
        if (effectiveType == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Choose the entity type"));
        }

        String tan = null;
        if (request.getTan() != null && !request.getTan().isBlank()) {
            tan = request.getTan().trim().toUpperCase();
            if (!tan.matches(Organization.TAN_PATTERN)) {
                return ResponseEntity.badRequest().body(Map.of("error",
                        "TAN must be 10 characters in the format: ABCD12345E"));
            }
        }

        boolean individual = INDIVIDUAL_HUF_CODE.equals(effectiveType);
        Boolean turnoverExceeded = request.getTdsTurnoverExceeded();
        TurnoverNature turnoverNature = null;
        if (individual) {
            if (request.getTurnoverNature() != null && !request.getTurnoverNature().isBlank()) {
                try {
                    turnoverNature = TurnoverNature.valueOf(request.getTurnoverNature().trim().toUpperCase());
                } catch (IllegalArgumentException e) {
                    return ResponseEntity.badRequest().body(Map.of("error",
                            "Invalid turnover nature. Expected BUSINESS or PROFESSION."));
                }
            }
            if (turnoverExceeded == null && request.getPriorYearTurnover() != null
                    && turnoverNature != null) {
                BigDecimal limit = turnoverNature == TurnoverNature.PROFESSION
                        ? PROFESSION_TURNOVER_LIMIT
                        : BUSINESS_TURNOVER_LIMIT;
                turnoverExceeded = request.getPriorYearTurnover().compareTo(limit) > 0;
            }
            if (turnoverExceeded == null) {
                return ResponseEntity.badRequest().body(Map.of("error",
                        "Tell us whether last year's turnover exceeded the TDS limit"));
            }
        }

        Organization org = new Organization(request.getName());
        org.setGstin(request.getGstin());
        org.setPan(pan);
        org.setRegisteredAddress(request.getRegisteredAddress());
        org.setFinancialYearStart(request.getFinancialYearStart() > 0 ? request.getFinancialYearStart() : 4);
        org.setDeductorType(effectiveType);
        // TAN is accepted for every deductor type but stored only when the organization
        // will actually deduct: non-individual types, or an individual/HUF above the limit.
        if (tan != null && (!individual || turnoverExceeded)) {
            org.setTan(tan);
        }
        final Organization savedOrg = organizationRepository.save(org);

        if (individual) {
            int currentFy = PeriodCalendar.financialYearOf(LocalDate.now(), savedOrg.getFinancialYearStart());
            tdsApplicabilityService.recordDeclared(savedOrg.getId(), currentFy, turnoverExceeded,
                    request.getPriorYearTurnover(), turnoverNature, principal.getUsername());
        }

        userRepository.findById(principal.getId()).ifPresent(user -> {
            ERole membershipRole = user.getRole() != null ? user.getRole().getName() : ERole.ROLE_OWNER;
            UserOrganization membership = new UserOrganization(user, savedOrg, membershipRole);
            userOrganizationRepository.save(membership);
        });

        int currentFy = PeriodCalendar.financialYearOf(LocalDate.now(), savedOrg.getFinancialYearStart());
        boolean tdsApplicable = tdsApplicabilityService.resolve(savedOrg.getId(), currentFy).applicable();

        return ResponseEntity.status(201).body(Map.ofEntries(
                Map.entry("id", savedOrg.getId()),
                Map.entry("name", savedOrg.getName()),
                Map.entry("gstin", savedOrg.getGstin() != null ? savedOrg.getGstin() : ""),
                Map.entry("pan", savedOrg.getPan() != null ? savedOrg.getPan() : ""),
                Map.entry("registeredAddress", savedOrg.getRegisteredAddress() != null ? savedOrg.getRegisteredAddress() : ""),
                Map.entry("financialYearStart", savedOrg.getFinancialYearStart()),
                Map.entry("tan", savedOrg.getTan() != null ? savedOrg.getTan() : ""),
                Map.entry("deductorType", savedOrg.getDeductorType() != null ? savedOrg.getDeductorType() : ""),
                Map.entry("tdsApplicable", tdsApplicable),
                Map.entry("interestPayerType", InterestPayerType.from(savedOrg.getInterestPayerType()).name()),
                Map.entry("createdAt", savedOrg.getCreatedAt())
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
                .map(org -> ResponseEntity.ok(Map.ofEntries(
                        Map.entry("id", org.getId()),
                        Map.entry("name", org.getName()),
                        Map.entry("gstin", org.getGstin() != null ? org.getGstin() : ""),
                        Map.entry("pan", org.getPan() != null ? org.getPan() : ""),
                        Map.entry("registeredAddress", org.getRegisteredAddress() != null ? org.getRegisteredAddress() : ""),
                        Map.entry("financialYearStart", org.getFinancialYearStart()),
                        Map.entry("tan", org.getTan() != null ? org.getTan() : ""),
                        Map.entry("deductorType", org.getDeductorType() != null ? org.getDeductorType() : ""),
                        Map.entry("responsiblePersonName", org.getResponsiblePersonName() != null ? org.getResponsiblePersonName() : ""),
                        Map.entry("responsiblePersonDesignation", org.getResponsiblePersonDesignation() != null ? org.getResponsiblePersonDesignation() : ""),
                        Map.entry("interestPayerType", InterestPayerType.from(org.getInterestPayerType()).name()),
                        Map.entry("createdAt", org.getCreatedAt())
                )))
                .orElse(ResponseEntity.notFound().build());
    }

    @PreAuthorize("hasRole('OWNER') or hasRole('ACCOUNTANT')")
    @PatchMapping("/{id}/deductor")
    public ResponseEntity<?> updateDeductorDetails(
            @PathVariable UUID id,
            @RequestBody UpdateDeductorDetailsRequest request,
            @AuthenticationPrincipal UserDetailsImpl principal) {

        Optional<UserOrganization> membership = userOrganizationRepository
                .findByUserIdAndOrganizationId(principal.getId(), id);
        if (membership.isEmpty()) {
            return ResponseEntity.status(403).body(new MessageResponse("Access denied to this organization"));
        }

        Optional<Organization> orgOpt = organizationRepository.findById(id);
        if (orgOpt.isEmpty()) return ResponseEntity.notFound().build();
        Organization org = orgOpt.get();

        if (request.getTan() != null) {
            String tan = request.getTan().trim().toUpperCase();
            if (!tan.matches(Organization.TAN_PATTERN)) {
                return ResponseEntity.badRequest().body(Map.of("error", "Invalid TAN format. Expected AAAA99999A."));
            }
            org.setTan(tan);
        }
        if (request.getDeductorType() != null) {
            String deductorType = request.getDeductorType();
            if (!deductorType.isEmpty() && !DeductorType.isValid(deductorType)) {
                return ResponseEntity.badRequest().body(Map.of("error", "Invalid deductor type."));
            }
            org.setDeductorType(deductorType);
        }
        if (request.getResponsiblePersonName() != null) org.setResponsiblePersonName(request.getResponsiblePersonName());
        if (request.getResponsiblePersonDesignation() != null) org.setResponsiblePersonDesignation(request.getResponsiblePersonDesignation());
        if (request.getInterestPayerType() != null) {
            InterestPayerType payerType = InterestPayerType.parse(request.getInterestPayerType());
            if (payerType == null) {
                return ResponseEntity.badRequest().body(Map.of("error",
                        "Invalid interest payer type. Expected OTHER, BANK, CO_OPERATIVE_BANK or POST_OFFICE."));
            }
            org.setInterestPayerType(payerType.name());
        }

        organizationRepository.save(org);
        return ResponseEntity.ok(org);
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

    @PreAuthorize("hasRole('OWNER') or hasRole('ACCOUNTANT')")
    @GetMapping("/{id}/members")
    public ResponseEntity<?> getOrganizationMembers(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetailsImpl principal) {

        Optional<UserOrganization> membership = userOrganizationRepository
                .findByUserIdAndOrganizationId(principal.getId(), id);
        if (membership.isEmpty()) {
            return ResponseEntity.status(403).body(new MessageResponse("Access denied to this organization"));
        }

        if (organizationRepository.findById(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        List<UserOrganization> members = userOrganizationRepository.findByOrganizationId(id);
        List<Map<String, Object>> result = members.stream().map(m -> {
            Map<String, Object> entry = new java.util.HashMap<>();
            entry.put("username", m.getUser().getUsername());
            entry.put("email", m.getUser().getEmail());
            entry.put("role", m.getRole().name());
            return entry;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/invites/{token}/accept")
    public ResponseEntity<?> acceptInvite(
            @PathVariable String token,
            @AuthenticationPrincipal UserDetailsImpl principal) {

        Optional<OrganizationInvite> inviteOpt = organizationInviteRepository.findByToken(token);
        if (inviteOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(new MessageResponse("This invite link is invalid. Check the link and try again."));
        }

        OrganizationInvite invite = inviteOpt.get();
        if (!invite.isValid()) {
            return ResponseEntity.badRequest().body(new MessageResponse("This invite link has expired or has already been used. Ask your organization admin to send a new one."));
        }

        Optional<UserOrganization> existing = userOrganizationRepository
                .findByUserIdAndOrganizationId(principal.getId(), invite.getOrganization().getId());
        if (existing.isPresent()) {
            return ResponseEntity.badRequest().body(new MessageResponse("You are already a member of this organization."));
        }

        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        UserOrganization membership = new UserOrganization(user, invite.getOrganization(), invite.getRole());
        userOrganizationRepository.save(membership);

        invite.setUsedAt(Instant.now());
        organizationInviteRepository.save(invite);

        String newJwt = jwtUtils.generateJwtTokenForUser(principal.getUsername(), invite.getOrganization().getId());

        return ResponseEntity.ok(Map.of(
                "message", "Successfully joined the organization!",
                "token", newJwt,
                "organizationId", invite.getOrganization().getId(),
                "organizationName", invite.getOrganization().getName(),
                "role", invite.getRole().name()
        ));
    }
}
