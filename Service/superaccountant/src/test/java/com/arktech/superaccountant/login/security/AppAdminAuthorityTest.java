package com.arktech.superaccountant.login.security;

import com.arktech.superaccountant.login.models.ERole;
import com.arktech.superaccountant.login.models.Role;
import com.arktech.superaccountant.login.models.User;
import com.arktech.superaccountant.login.security.services.UserDetailsImpl;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The app-administrator authority is granted only by the users.app_admin flag, never by a
 * company role, and it is appended after the company role so callers that read the first
 * authority as "the role" keep working.
 */
class AppAdminAuthorityTest {

    private User user(Boolean appAdmin) {
        Role role = new Role();
        role.setName(ERole.ROLE_OWNER);
        User u = new User();
        u.setId(1L);
        u.setUsername("owner");
        u.setPassword("x");
        u.setEmail("o@t.com");
        u.setRole(role);
        u.setAppAdmin(appAdmin);
        return u;
    }

    @Test
    void flagNullOrFalseGivesOnlyCompanyRole() {
        for (Boolean flag : new Boolean[] {null, false}) {
            UserDetailsImpl details = UserDetailsImpl.build(user(flag), null);
            List<String> authorities = details.getAuthorities().stream()
                    .map(a -> a.getAuthority()).toList();
            assertEquals(List.of("ROLE_OWNER"), authorities);
        }
        assertTrue(!AppAdmin.holds(null));
    }

    @Test
    void flagTrueAppendsAppAdminAfterCompanyRole() {
        UserDetailsImpl details = UserDetailsImpl.build(user(true), null);
        List<String> authorities = details.getAuthorities().stream()
                .map(a -> a.getAuthority()).toList();
        assertEquals(List.of("ROLE_OWNER", "ROLE_APP_ADMIN"), authorities);
        assertTrue(authorities.indexOf("ROLE_APP_ADMIN") > authorities.indexOf("ROLE_OWNER"));

        org.springframework.security.authentication.TestingAuthenticationToken auth =
                new org.springframework.security.authentication.TestingAuthenticationToken(
                        details, null, details.getAuthorities());
        assertTrue(AppAdmin.holds(auth));
    }
}
