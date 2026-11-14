package com.arktech.superaccountant.masters.controllers;

import com.arktech.superaccountant.login.models.ERole;
import com.arktech.superaccountant.login.models.Role;
import com.arktech.superaccountant.login.models.User;
import com.arktech.superaccountant.login.repository.RoleRepository;
import com.arktech.superaccountant.login.repository.UserRepository;
import com.arktech.superaccountant.login.security.jwt.JwtUtils;
import com.arktech.superaccountant.masters.models.Organization;
import com.arktech.superaccountant.masters.repository.OrganizationRepository;
import com.arktech.superaccountant.masters.services.OrganizationSettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Per-org validation settings: default, role checks, audit of who changed it, org isolation. Needs Docker. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Transactional
@TestPropertySource(properties = "JWT_SECRET=test-jwt-secret-must-be-at-least-32-characters-long")
class ValidationSettingsIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    private static final String URL = "/api/v1/validation-settings";

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired JwtUtils jwtUtils;
    @Autowired OrganizationRepository organizationRepository;
    @Autowired OrganizationSettingsService settingsService;

    private UUID orgA, orgB;
    private String operatorJwt, accountantJwt, ownerJwt, auditorJwt, otherOrgAccountantJwt;

    @BeforeEach
    void setUp() {
        orgA = organizationRepository.save(new Organization("Settings Org A " + UUID.randomUUID())).getId();
        orgB = organizationRepository.save(new Organization("Settings Org B " + UUID.randomUUID())).getId();
        operatorJwt = user("vs_operator", ERole.ROLE_OPERATOR, orgA);
        accountantJwt = user("vs_accountant", ERole.ROLE_ACCOUNTANT, orgA);
        ownerJwt = user("vs_owner", ERole.ROLE_OWNER, orgA);
        auditorJwt = user("vs_auditor", ERole.ROLE_AUDITOR_CA, orgA);
        otherOrgAccountantJwt = user("vs_accountant_b", ERole.ROLE_ACCOUNTANT, orgB);
    }

    private String user(String username, ERole eRole, UUID orgId) {
        Role role = roleRepository.findByName(eRole).orElseGet(() -> roleRepository.save(new Role(eRole)));
        if (userRepository.findByUsername(username).isEmpty()) {
            User u = new User(username, username + "@test.com", "pass");
            u.setRole(role);
            userRepository.save(u);
        }
        return jwtUtils.generateJwtTokenForUser(username, orgId);
    }

    private ResultActions getAs(String jwt) throws Exception {
        return mockMvc.perform(get(URL).header("Authorization", "Bearer " + jwt));
    }

    private ResultActions putAs(String jwt, boolean value) throws Exception {
        return mockMvc.perform(put(URL).contentType("application/json")
                .content("{\"requireHsnSacOnExpense\":" + value + "}")
                .header("Authorization", "Bearer " + jwt));
    }

    @Test
    void default_isOn_withNoAuditInfo() throws Exception {
        getAs(operatorJwt).andExpect(status().isOk())
                .andExpect(jsonPath("$.requireHsnSacOnExpense").value(true))
                .andExpect(jsonPath("$.updatedBy").doesNotExist());
        assertThat(settingsService.validationSettings(orgA))
                .containsEntry(OrganizationSettingsService.REQUIRE_HSN_SAC_ON_EXPENSE, true);
    }

    @Test
    void accountant_canTurnOff_andChangeIsRecorded_andReachesValidationSettings() throws Exception {
        putAs(accountantJwt, false).andExpect(status().isOk())
                .andExpect(jsonPath("$.requireHsnSacOnExpense").value(false))
                .andExpect(jsonPath("$.updatedBy").value("vs_accountant"))
                .andExpect(jsonPath("$.updatedAt").exists());

        getAs(auditorJwt).andExpect(status().isOk())
                .andExpect(jsonPath("$.requireHsnSacOnExpense").value(false))
                .andExpect(jsonPath("$.updatedBy").value("vs_accountant"));
        assertThat(settingsService.validationSettings(orgA))
                .containsEntry(OrganizationSettingsService.REQUIRE_HSN_SAC_ON_EXPENSE, false);
    }

    @Test
    void owner_change_403_andValueUnchanged() throws Exception {
        putAs(accountantJwt, false).andExpect(status().isOk());
        putAs(ownerJwt, true).andExpect(status().isForbidden());

        getAs(accountantJwt).andExpect(status().isOk())
                .andExpect(jsonPath("$.requireHsnSacOnExpense").value(false))
                .andExpect(jsonPath("$.updatedBy").value("vs_accountant"));
    }

    @Test
    void operator_cannotChange() throws Exception {
        putAs(operatorJwt, false).andExpect(status().isForbidden());
    }

    @Test
    void auditor_cannotChange() throws Exception {
        putAs(auditorJwt, false).andExpect(status().isForbidden());
    }

    @Test
    void unauthenticated_isRejected() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
    }

    @Test
    void missingValue_isBadRequest() throws Exception {
        mockMvc.perform(put(URL).contentType("application/json").content("{}")
                        .header("Authorization", "Bearer " + accountantJwt))
                .andExpect(status().isBadRequest());
    }

    @Test
    void setting_isIsolatedPerOrganization() throws Exception {
        putAs(accountantJwt, false).andExpect(status().isOk());

        getAs(otherOrgAccountantJwt).andExpect(status().isOk())
                .andExpect(jsonPath("$.requireHsnSacOnExpense").value(true))
                .andExpect(jsonPath("$.updatedBy").doesNotExist());
        assertThat(settingsService.requireHsnSacOnExpense(orgB)).isTrue();
        assertThat(settingsService.requireHsnSacOnExpense(orgA)).isFalse();
    }
}
