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

import java.util.UUID;

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

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private com.arktech.superaccountant.login.repository.RoleRepository roleRepository;
    @Autowired
    private com.arktech.superaccountant.login.repository.UserRepository userRepository;
    @Autowired
    private com.arktech.superaccountant.login.security.jwt.JwtUtils jwtUtils;
    @Autowired
    private com.arktech.superaccountant.masters.repository.PreconfiguredMasterRepository masterRepository;

    private UUID lastOrgId;

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
        assertThat(count).isGreaterThanOrEqualTo(50);
        assertThat(gstLedgerNames(lastOrgId)).isEmpty();
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
        assertThat(count).isGreaterThanOrEqualTo(25);
        assertThat(gstLedgerNames(lastOrgId)).isEmpty();
    }

    @Test
    void onboard_gstinCompanyGetsExactlyTheStandardGstLedgerSet_forEverySlug() throws Exception {
        for (String slug : java.util.List.of("standard", "simplified", "manufacturing")) {
            String orgToken = setupOrgScopedToken("accountant", "27AAPFU0939F1ZV");

            mockMvc.perform(post("/api/v1/preconfigured-masters/onboard")
                            .header("Authorization", "Bearer " + orgToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"templateSlug\":\"" + slug + "\"}"))
                    .andExpect(status().isOk());

            java.util.List<String> gstNames = gstLedgerNames(lastOrgId);
            assertThat(gstNames).containsExactlyInAnyOrderElementsOf(expectedStandardGstLedgerNames());
            for (String plain : java.util.List.of("Input IGST", "Input CGST", "Input SGST",
                    "Output IGST", "Output CGST", "Output SGST")) {
                assertThat(gstNames).doesNotContain(plain);
            }
            // no duplicates
            assertThat(java.util.Set.copyOf(gstNames)).hasSameSizeAs(gstNames);
        }
    }

    @Test
    void onboard_gstinCompanyViaLegacyUseTemplate_getsTheStandardGstLedgers() throws Exception {
        String orgToken = setupOrgScopedToken("accountant", "27AAPFU0939F1ZV");

        mockMvc.perform(post("/api/v1/preconfigured-masters/onboard")
                        .header("Authorization", "Bearer " + orgToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"useTemplate\":true}"))
                .andExpect(status().isOk());

        assertThat(gstLedgerNames(lastOrgId)).containsExactlyInAnyOrderElementsOf(expectedStandardGstLedgerNames());
    }

    @Test
    void onboard_nonGstinCompanyViaLegacyUseTemplate_getsNoGstLedgers() throws Exception {
        String orgToken = setupOrgScopedToken("accountant", null);

        mockMvc.perform(post("/api/v1/preconfigured-masters/onboard")
                        .header("Authorization", "Bearer " + orgToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"useTemplate\":true}"))
                .andExpect(status().isOk());

        assertThat(gstLedgerNames(lastOrgId)).isEmpty();
    }

    private java.util.List<String> gstLedgerNames(java.util.UUID orgId) {
        return masterRepository.findByOrganizationIdAndActiveTrue(orgId).stream()
                .filter(m -> m.getCategory() == com.arktech.superaccountant.masters.models.LedgerCategory.GST)
                .map(m -> m.getLedgerName())
                .toList();
    }

    private java.util.List<String> expectedStandardGstLedgerNames() {
        return com.arktech.superaccountant.masters.util.StandardGstLedgers.ledgers().stream()
                .map(l -> l.ledgerName())
                .toList();
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
        String orgToken = setupOrgScopedToken("accountant");

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

    @Test
    void owner_canApplyOnboardTemplate() throws Exception {
        String ownerToken = setupOrgScopedToken("owner");

        mockMvc.perform(post("/api/v1/preconfigured-masters/onboard")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"templateSlug\":\"standard\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void owner_manualWrites_403_operatorCreateStillAllowed() throws Exception {
        String ownerToken = setupOrgScopedToken("owner");
        String operatorToken = operatorJwt();
        java.util.UUID randomId = java.util.UUID.randomUUID();

        mockMvc.perform(post("/api/v1/preconfigured-masters")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ledgerName\":\"Owner Ledger\",\"category\":\"EXPENSE\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/v1/preconfigured-masters/" + randomId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/v1/preconfigured-masters/" + randomId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/preconfigured-masters/bulk")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"masters\":[]}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/preconfigured-masters")
                        .header("Authorization", "Bearer " + operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ledgerName\":\"Operator Ledger " + randomId + "\",\"category\":\"EXPENSE\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void owner_canStillList() throws Exception {
        String ownerToken = setupOrgScopedToken("owner");

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/preconfigured-masters")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk());
    }

    /**
     * Creates a unique test user (accountant), creates an org, and returns
     * an org-scoped JWT so onboard endpoint can access getOrganizationId().
     */
    private String setupOrgScopedToken() throws Exception {
        return setupOrgScopedToken("accountant");
    }

    private String setupOrgScopedToken(String role) throws Exception {
        return setupOrgScopedToken(role, null);
    }

    private String setupOrgScopedToken(String role, String gstin) throws Exception {
        String suffix = java.util.UUID.randomUUID().toString().substring(0, 8);
        String username = "it_" + suffix;

        // 1. Sign up
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"email\":\"" + username + "@test.com\",\"password\":\"TestPass1!\",\"role\":\"" + role + "\"}"))
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
        String orgPayload = "{\"name\":\"Test Org " + suffix + "\",\"pan\":\"AAACA1234A\",\"financialYearStart\":4"
                + (gstin == null ? "" : ",\"gstin\":\"" + gstin + "\"") + "}";
        MvcResult createOrgResult = mockMvc.perform(post("/api/organizations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orgPayload))
                .andExpect(status().isCreated())
                .andReturn();
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> orgBody = objectMapper.readValue(
                createOrgResult.getResponse().getContentAsString(), java.util.Map.class);
        String orgId = orgBody.get("id").toString();
        lastOrgId = UUID.fromString(orgId);

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

    /** Org-scoped operator JWT for the org created by the last setupOrgScopedToken call. */
    private String operatorJwt() {
        String suffix = java.util.UUID.randomUUID().toString().substring(0, 8);
        com.arktech.superaccountant.login.models.Role role = roleRepository
                .findByName(com.arktech.superaccountant.login.models.ERole.ROLE_OPERATOR).orElseThrow();
        com.arktech.superaccountant.login.models.User u = new com.arktech.superaccountant.login.models.User(
                "it_op_" + suffix, "it_op_" + suffix + "@test.com", "pass");
        u.setRole(role);
        userRepository.save(u);
        return jwtUtils.generateJwtTokenForUser(u.getUsername(), lastOrgId);
    }
}
