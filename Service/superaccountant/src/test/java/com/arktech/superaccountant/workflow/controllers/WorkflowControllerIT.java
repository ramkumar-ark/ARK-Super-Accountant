package com.arktech.superaccountant.workflow.controllers;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Transactional
@TestPropertySource(properties = "JWT_SECRET=test-jwt-secret-must-be-at-least-32-characters-long")
class WorkflowControllerIT {

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

    private String signupAndLogin(String username, String email) throws Exception {
        return signupAndLogin(username, email, "owner");
    }

    private String signupAndLogin(String username, String email, String role) throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"username":"%s","email":"%s","password":"pass1234","role":"%s"}
                    """.formatted(username, email, role)))
                .andExpect(status().isOk());
        return login(username);
    }

    private String login(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/signin")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"username":"%s","password":"pass1234"}
                    """.formatted(username)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("token").asText();
    }

    private UUID createOrganization(String jwt) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/organizations")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + jwt)
                .content("""
                    {"name":"Workflow Org","financialYearStart":4}
                    """))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(
                objectMapper.readTree(result.getResponse().getContentAsString())
                        .get("id").asText());
    }

    private String selectOrg(String jwt, UUID orgId) throws Exception {
        MvcResult selected = mockMvc.perform(post("/api/organizations/{id}/select", orgId)
                .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(selected.getResponse().getContentAsString())
                .get("token").asText();
    }

    /** Invites a fresh signup into the org, selects it and re-logs in. */
    private String memberJwt(String ownerJwt, UUID orgId, String role,
                             String username, String email) throws Exception {
        MvcResult inviteResult = mockMvc.perform(post("/api/organizations/{id}/invites", orgId)
                .param("role", role)
                .header("Authorization", "Bearer " + ownerJwt))
                .andExpect(status().isOk())
                .andReturn();
        String token = objectMapper
                .readTree(inviteResult.getResponse().getContentAsString())
                .get("token").asText();

        signupAndLogin(username, email, role);
        mockMvc.perform(post("/api/organizations/invites/{token}/accept", token)
                .header("Authorization", "Bearer " + login(username)))
                .andExpect(status().isOk());
        return selectOrg(login(username), orgId);
    }

    @Test
    void start_asOperator_returns200AndListVisibleToOwner() throws Exception {
        String ownerJwt = signupAndLogin("wfowner", "wfowner@test.com");
        UUID orgId = createOrganization(ownerJwt);
        ownerJwt = selectOrg(ownerJwt, orgId);
        String operatorJwt = memberJwt(ownerJwt, orgId, "operator",
                "wfoperator", "wfoperator@test.com");

        mockMvc.perform(post("/api/v1/workflows")
                .header("Authorization", "Bearer " + operatorJwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"type":"GSTR1","periodKey":"2026-04"}
                    """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("STARTED"))
                .andExpect(jsonPath("$.periodStart").value("2026-04-01"));

        mockMvc.perform(get("/api/v1/workflows")
                .header("Authorization", "Bearer " + ownerJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("GSTR1"));
    }

    @Test
    void start_asOwner_returns403() throws Exception {
        String ownerJwt = signupAndLogin("wfowner2", "wfowner2@test.com");
        createOrganization(ownerJwt);

        mockMvc.perform(post("/api/v1/workflows")
                .header("Authorization", "Bearer " + ownerJwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"type":"GSTR1","periodKey":"2026-04"}
                    """))
                .andExpect(status().isForbidden());
    }

    @Test
    void start_duplicateActivePeriod_returns409() throws Exception {
        String ownerJwt = signupAndLogin("wfowner3", "wfowner3@test.com");
        UUID orgId = createOrganization(ownerJwt);
        ownerJwt = selectOrg(ownerJwt, orgId);
        String operatorJwt = memberJwt(ownerJwt, orgId, "operator",
                "wfoperator3", "wfoperator3@test.com");

        mockMvc.perform(post("/api/v1/workflows")
                .header("Authorization", "Bearer " + operatorJwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"type":"GSTR1","periodKey":"2026-04"}
                    """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/workflows")
                .header("Authorization", "Bearer " + operatorJwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"type":"GSTR1","periodKey":"2026-04"}
                    """))
                .andExpect(status().isConflict());
    }

    @Test
    void accountantOnlyTransitions_operatorGets403_accountantGets409ForBadStage() throws Exception {
        String ownerJwt = signupAndLogin("wfowner4", "wfowner4@test.com");
        UUID orgId = createOrganization(ownerJwt);
        ownerJwt = selectOrg(ownerJwt, orgId);
        String operatorJwt = memberJwt(ownerJwt, orgId, "operator",
                "wfoperator4", "wfoperator4@test.com");
        String accountantJwt = memberJwt(ownerJwt, orgId, "accountant",
                "wfaccountant4", "wfaccountant4@test.com");

        MvcResult startResult = mockMvc.perform(post("/api/v1/workflows")
                .header("Authorization", "Bearer " + operatorJwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"type":"GSTR1","periodKey":"2026-04"}
                    """))
                .andExpect(status().isOk())
                .andReturn();
        UUID workflowId = UUID.fromString(
                objectMapper.readTree(startResult.getResponse().getContentAsString())
                        .get("id").asText());

        mockMvc.perform(post("/api/v1/workflows/{id}/finalize", workflowId)
                .header("Authorization", "Bearer " + operatorJwt))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/workflows/{id}/finalize", workflowId)
                .header("Authorization", "Bearer " + accountantJwt))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/v1/workflows/{id}/unfinalize", workflowId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"reason":"fix"}
                    """)
                .header("Authorization", "Bearer " + accountantJwt))
                .andExpect(status().isConflict());
    }

    @Test
    void get_detail_showsStageChecklist() throws Exception {
        String ownerJwt = signupAndLogin("wfowner5", "wfowner5@test.com");
        UUID orgId = createOrganization(ownerJwt);
        ownerJwt = selectOrg(ownerJwt, orgId);
        String operatorJwt = memberJwt(ownerJwt, orgId, "operator",
                "wfoperator5", "wfoperator5@test.com");

        MvcResult startResult = mockMvc.perform(post("/api/v1/workflows")
                .header("Authorization", "Bearer " + operatorJwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"type":"GSTR1","periodKey":"2026-04"}
                    """))
                .andExpect(status().isOk())
                .andReturn();
        UUID workflowId = UUID.fromString(
                objectMapper.readTree(startResult.getResponse().getContentAsString())
                        .get("id").asText());

        mockMvc.perform(get("/api/v1/workflows/{id}", workflowId)
                .header("Authorization", "Bearer " + ownerJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baselineConfirmed").value(false))
                .andExpect(jsonPath("$.dependencies").isArray())
                .andExpect(jsonPath("$.analysisAvailable").value(false))
                .andExpect(jsonPath("$.requiredCoverageTo").value("2026-04-30"))
                .andExpect(jsonPath("$.requiredCoverageFrom").isNotEmpty());

        mockMvc.perform(get("/api/v1/workflows/{id}/events", workflowId)
                .header("Authorization", "Bearer " + ownerJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].kind").value("STARTED"));
    }
}
