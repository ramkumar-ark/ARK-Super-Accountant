package com.arktech.superaccountant.login.controllers;

import com.arktech.superaccountant.login.models.ERole;
import com.arktech.superaccountant.login.models.Role;
import com.arktech.superaccountant.login.models.User;
import com.arktech.superaccountant.login.payload.request.LoginRequest;
import com.arktech.superaccountant.login.payload.request.SignupRequest;
import com.arktech.superaccountant.login.payload.response.JwtResponse;
import com.arktech.superaccountant.login.payload.response.MessageResponse;
import com.arktech.superaccountant.login.repository.RoleRepository;
import com.arktech.superaccountant.login.repository.UserRepository;
import com.arktech.superaccountant.login.security.jwt.JwtUtils;
import com.arktech.superaccountant.login.security.services.UserDetailsImpl;
import com.arktech.superaccountant.masters.models.OrganizationInvite;
import com.arktech.superaccountant.masters.models.UserOrganization;
import com.arktech.superaccountant.masters.repository.OrganizationInviteRepository;
import com.arktech.superaccountant.masters.repository.UserOrganizationRepository;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    @Autowired
    AuthenticationManager authenticationManager;

    @Autowired
    UserRepository userRepository;

    @Autowired
    RoleRepository roleRepository;

    @Autowired
    PasswordEncoder encoder;

    @Autowired
    JwtUtils jwtUtils;

    @Autowired
    OrganizationInviteRepository organizationInviteRepository;

    @Autowired
    UserOrganizationRepository userOrganizationRepository;

    @PostMapping("/signin")
    public ResponseEntity<?> authenticateUser(@Valid @RequestBody LoginRequest loginRequest) {

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.getUsername(), loginRequest.getPassword()));

        SecurityContextHolder.getContext().setAuthentication(authentication);
        String jwt = jwtUtils.generateJwtToken(authentication);

        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        String role = userDetails.getAuthorities().stream()
                .map(item -> item.getAuthority())
                .findFirst()
                .orElse("");

        return ResponseEntity.ok(new JwtResponse(jwt,
                userDetails.getId(),
                userDetails.getUsername(),
                userDetails.getEmail(),
                role));
    }

    @GetMapping("/invite/{token}")
    public ResponseEntity<?> validateInviteToken(@PathVariable String token) {
        Optional<OrganizationInvite> inviteOpt = organizationInviteRepository.findByToken(token);
        if (inviteOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(new MessageResponse("This invite link is invalid. Check the link and try again."));
        }
        OrganizationInvite invite = inviteOpt.get();
        if (!invite.isValid()) {
            return ResponseEntity.badRequest().body(new MessageResponse("This invite link has expired or has already been used. Ask your organization admin to send a new one."));
        }
        return ResponseEntity.ok(Map.of(
                "organizationName", invite.getOrganization().getName(),
                "role", invite.getRole().name().replace("ROLE_", "")
        ));
    }

    @PostMapping("/signup")
    public ResponseEntity<?> registerUser(
            @Valid @RequestBody SignupRequest signUpRequest,
            @RequestParam(required = false) String invite) {

        if (userRepository.existsByUsername(signUpRequest.getUsername())) {
            return ResponseEntity
                    .badRequest()
                    .body(new MessageResponse("Error: Username is already taken!"));
        }

        if (userRepository.existsByEmail(signUpRequest.getEmail())) {
            return ResponseEntity
                    .badRequest()
                    .body(new MessageResponse("Error: Email is already in use!"));
        }

        // Create new user's account
        User user = new User(signUpRequest.getUsername(),
                signUpRequest.getEmail(),
                encoder.encode(signUpRequest.getPassword()));

        String strRole = signUpRequest.getRole();
        Role userRole;

        if (strRole == null || strRole.isEmpty()) {
            userRole = roleRepository.findByName(ERole.ROLE_CASHIER)
                    .orElseThrow(() -> new RuntimeException("Error: Role is not found."));
        } else {
            switch (strRole) {
                case "owner":
                    userRole = roleRepository.findByName(ERole.ROLE_OWNER)
                            .orElseThrow(() -> new RuntimeException("Error: Role is not found."));
                    break;
                case "accountant":
                    userRole = roleRepository.findByName(ERole.ROLE_ACCOUNTANT)
                            .orElseThrow(() -> new RuntimeException("Error: Role is not found."));
                    break;
                case "data_entry":
                    userRole = roleRepository.findByName(ERole.ROLE_DATA_ENTRY_OPERATOR)
                            .orElseThrow(() -> new RuntimeException("Error: Role is not found."));
                    break;
                default:
                    userRole = roleRepository.findByName(ERole.ROLE_CASHIER)
                            .orElseThrow(() -> new RuntimeException("Error: Role is not found."));
            }
        }

        // Validate invite token and override role if present
        OrganizationInvite resolvedInvite = null;
        if (invite != null && !invite.isBlank()) {
            Optional<OrganizationInvite> inviteOpt = organizationInviteRepository.findByToken(invite);
            if (inviteOpt.isEmpty()) {
                return ResponseEntity.badRequest().body(new MessageResponse("This invite link is invalid. Check the link and try again."));
            }
            resolvedInvite = inviteOpt.get();
            if (!resolvedInvite.isValid()) {
                return ResponseEntity.badRequest().body(new MessageResponse("This invite link has expired or has already been used. Ask your organization admin to send a new one."));
            }
            // Override role from invite
            userRole = roleRepository.findByName(resolvedInvite.getRole())
                    .orElseThrow(() -> new RuntimeException("Error: Role is not found."));
        }

        user.setRole(userRole);
        userRepository.save(user);

        if (resolvedInvite != null) {
            UserOrganization membership = new UserOrganization(user, resolvedInvite.getOrganization(), resolvedInvite.getRole());
            userOrganizationRepository.save(membership);
            resolvedInvite.setUsedAt(Instant.now());
            organizationInviteRepository.save(resolvedInvite);
        }

        return ResponseEntity.ok(new MessageResponse("User registered successfully!"));
    }
}
