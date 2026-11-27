package com.arktech.superaccountant.login.security;

import org.springframework.security.core.Authentication;

/**
 * The app-level administrator capability. Separate from every company role ({@code ERole}):
 * statutory configuration is global, so no company owner, accountant, operator or auditor
 * may change it. Held by users whose {@code users.app_admin} flag is true; the flag is never
 * set by any API (no signup, invite or profile path), only by an operator in the database.
 */
public final class AppAdmin {

    private AppAdmin() {
    }

    /** Spring Security authority; matched by {@code hasRole('APP_ADMIN')}. */
    public static final String AUTHORITY = "ROLE_APP_ADMIN";

    public static boolean holds(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> AUTHORITY.equals(a.getAuthority()));
    }
}
