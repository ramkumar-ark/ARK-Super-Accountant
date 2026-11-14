package com.arktech.superaccountant.review;

import com.arktech.superaccountant.login.security.services.UserDetailsImpl;

/**
 * The actor driving a review action. {@code accountant} gates APPROVE and REJECT and the
 * one-step approve on ACKNOWLEDGE; {@code canSubmit} gates ACKNOWLEDGE.
 */
public record ReviewActor(String username, boolean accountant, boolean canSubmit) {

    public static ReviewActor of(UserDetailsImpl principal) {
        boolean accountant = principal.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ACCOUNTANT".equals(a.getAuthority()));
        boolean canSubmit = accountant || principal.getAuthorities().stream()
                .anyMatch(a -> "ROLE_OPERATOR".equals(a.getAuthority()));
        return new ReviewActor(principal.getUsername(), accountant, canSubmit);
    }
}
