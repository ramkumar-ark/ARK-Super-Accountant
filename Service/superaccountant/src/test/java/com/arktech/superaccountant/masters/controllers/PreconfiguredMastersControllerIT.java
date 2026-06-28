package com.arktech.superaccountant.masters.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Transactional
@TestPropertySource(properties = "JWT_SECRET=test-jwt-secret-must-be-at-least-32-characters-long")
class PreconfiguredMastersControllerIT {

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
    private ObjectMapper objectMapper;

    @Test
    void onboard_standardTemplate_copies60PlusMasters() throws Exception {
        String orgToken = setupOrgScopedToken();

        MvcResult result = mockMvc.perform(post("/api/v1/preconfigured-masters/onboard")
                        .header("Authorization", "Bearer " + orgToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"templateSlug\":\"standard\"}"))
                .andExpect(status().isOk())
                .andReturn();

        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> body = objectMapper.readValue(
                result.getResponse().getContentAsString(), java.util.Map.class);
        int count = ((Number) body.get("count")).intValue();
        assertThat(count).isGreaterThanOrEqualTo(60);
    }

    @Test
    void onboard_simplifiedTemplate_copies30PlusMasters() throws Exception {
        String orgToken = setupOrgScopedToken();

        MvcResult result = mockMvc.perform(post("/api/v1/preconfigured-masters/onboard")
                        .header("Authorization", "Bearer " + orgToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"templateSlug\":\"simplified\"}"))
                .andExpect(status().isOk())
                .andReturn();

        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> body = objectMapper.readValue(
                result.getResponse().getContentAsString(), java.util.Map.class);
        int count = ((Number) body.get("count")).intValue();
        assertThat(count).isGreaterThanOrEqualTo(30);
    }

    @Test
    void onboard_unknownTemplateSlug_returns400() throws Exception {
        String orgToken = setupOrgScopedToken();

        mockMvc.perform(post("/api/v1/preconfigured-masters/onboard")
                        .header("Authorization", "Bearer " + orgToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"templateSlug\":\"nonexistent\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void onboard_calledTwice_returns400OnSecondCall() throws Exception {
        String orgToken = setupOrgScopedToken();

        // First call: success
        mockMvc.perform(post("/api/v1/preconfigured-masters/onboard")
                        .header("Authorization", "Bearer " + orgToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"templateSlug\":\"simplified\"}"))
                .andExpect(status().isOk());

        // Second call: 400 (existsByOrganizationId guard)
        mockMvc.perform(post("/api/v1/preconfigured-masters/onboard")
                        .header("Authorization", "Bearer " + orgToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"templateSlug\":\"simplified\"}"))
                .andExpect(status().isBadRequest());
    }

    /**
     * Creates a unique test user (accountant), creates an org, and returns
     * an org-scoped JWT so onboard endpoint can access getOrganizationId().
     */
    private String setupOrgScopedToken() throws Exception {
        String suffix = java.util.UUID.randomUUID().toString().substring(0, 8);
        String username = "it_" + suffix;

        // 1. Sign up
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"email\":\"" + username + "@test.com\",\"password\":\"TestPass1!\",\"role\":\"accountant\"}"))
                .andExpect(status().isOk());

        // 2. Sign in → get token (no org scope)
        MvcResult signinResult = mockMvc.perform(post("/api/auth/signin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"TestPass1!\"}"))
                .andExpect(status().isOk())
                .andReturn();
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> signinBody = objectMapper.readValue(
                signinResult.getResponse().getContentAsString(), java.util.Map.class);
        String token = (String) signinBody.get("token");

        // 3. Create org → get org ID
        MvcResult createOrgResult = mockMvc.perform(post("/api/organizations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Test Org " + suffix + "\",\"financialYearStart\":4}"))
                .andExpect(status().isCreated())
                .andReturn();
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> orgBody = objectMapper.readValue(
                createOrgResult.getResponse().getContentAsString(), java.util.Map.class);
        String orgId = orgBody.get("id").toString();

        // 4. Select org → get org-scoped JWT
        MvcResult selectResult = mockMvc.perform(post("/api/organizations/" + orgId + "/select")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> selectBody = objectMapper.readValue(
                selectResult.getResponse().getContentAsString(), java.util.Map.class);
        return (String) selectBody.get("token");
    }
}
