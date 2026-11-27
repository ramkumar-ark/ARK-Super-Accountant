package com.arktech.superaccountant.statutory.controllers;

import com.arktech.superaccountant.login.models.ERole;
import com.arktech.superaccountant.login.models.Role;
import com.arktech.superaccountant.login.models.User;
import com.arktech.superaccountant.login.repository.RoleRepository;
import com.arktech.superaccountant.login.repository.UserRepository;
import com.arktech.superaccountant.login.security.services.UserDetailsImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /api/tds/sections serves the statutory tables backfilled at startup: active
 * Income-tax sections with the s.393 legal reference from the dated rule rows and the
 * RPU codes. Read-only and open to any authenticated user; unauthenticated calls are
 * rejected by the security chain.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = "JWT_SECRET=test-jwt-secret-must-be-at-least-32-characters-long")
class StatutorySectionsIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void sectionsListedForAnyAuthenticatedUser_withRefsAndRpuCodes() throws Exception {
        UserDetailsImpl principal = principal("reader", ERole.ROLE_OPERATOR);

        MvcResult result = mockMvc.perform(get("/api/tds/sections").with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(13))
                .andExpect(jsonPath("$[?(@.code == '194C')].description").value(
                        org.hamcrest.Matchers.hasItem("Payments to contractors and sub-contractors")))
                .andExpect(jsonPath("$[?(@.code == '194C')].legalReference").value(
                        org.hamcrest.Matchers.hasItem("393(1) [Table: Sl. No. 6(i)]")))
                .andExpect(jsonPath("$[?(@.code == '194C')].rpu26qCode").value(
                        org.hamcrest.Matchers.hasItem("94C")))
                .andExpect(jsonPath("$[?(@.code == '194C')].rpu140Code").value(
                        org.hamcrest.Matchers.hasItem("1024")))
                .andExpect(jsonPath("$[?(@.code == '194M')].legalReference").value(
                        org.hamcrest.Matchers.hasItem("393(1) [Table: Sl. No. 6(ii)]")))
                .andExpect(jsonPath("$[?(@.code == '194T')].rpu26qCode").value(
                        org.hamcrest.Matchers.hasItem("94T")))
                .andExpect(jsonPath("$[?(@.code == '194D')].rpu140Code").value(
                        org.hamcrest.Matchers.hasItem("1005")))
                .andExpect(jsonPath("$[?(@.code == '193')].legalReference").value(
                        org.hamcrest.Matchers.hasItem("393(1) [Table: Sl. No. 5(i)]")))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertFalse(body.contains("\"194B\""));
    }

    @Test
    void unauthenticatedRequestRejected() throws Exception {
        mockMvc.perform(get("/api/tds/sections"))
                .andExpect(status().isUnauthorized());
    }

    private UserDetailsImpl principal(String name, ERole roleName) {
        Role role = roleRepository.findByName(roleName).orElseThrow();
        User u = new User();
        u.setUsername(name + "-" + UUID.randomUUID());
        u.setPassword("x");
        u.setEmail(name + "-" + UUID.randomUUID() + "@t.com");
        u.setRole(role);
        return UserDetailsImpl.build(userRepository.save(u), null);
    }
}
