package com.arktech.superaccountant.gstr1.controllers;

import com.arktech.superaccountant.workflow.models.WorkflowStage;
import com.arktech.superaccountant.workflow.repository.WorkflowRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Transactional
@TestPropertySource(properties = "JWT_SECRET=test-jwt-secret-must-be-at-least-32-characters-long")
class Gstr1WorkflowControllerIT {

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
    private WorkflowRepository workflowRepository;

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
                    {"name":"Gstr1 Prepare Org","financialYearStart":4}
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

    private UUID startedGstr1Workflow(String operatorJwt) throws Exception {
        MvcResult startResult = mockMvc.perform(post("/api/v1/workflows")
                .header("Authorization", "Bearer " + operatorJwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"type":"GSTR1","periodKey":"2026-04"}
                    """))
                .andExpect(status().isOk())
                .andReturn();
        return UUID.fromString(
                objectMapper.readTree(startResult.getResponse().getContentAsString())
                        .get("id").asText());
    }

    @Test
    void getTables_operatorReads200_withTableRowsAndTotals() throws Exception {
        String ownerJwt = signupAndLogin("g1ownera", "g1ownera@test.com");
        UUID orgId = createOrganization(ownerJwt);
        ownerJwt = selectOrg(ownerJwt, orgId);
        String operatorJwt = memberJwt(ownerJwt, orgId, "operator",
                "g1operatora", "g1operatora@test.com");

        UUID workflowId = startedGstr1Workflow(operatorJwt);

        mockMvc.perform(get("/api/v1/workflows/{id}/gstr1/tables", workflowId)
                .header("Authorization", "Bearer " + operatorJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tables").isArray())
                .andExpect(jsonPath("$.fillIns").isArray());
    }

    @Test
    void putFillIn_asAccountant200_andListedAsFixedInPrepare() throws Exception {
        String ownerJwt = signupAndLogin("g1ownerb", "g1ownerb@test.com");
        UUID orgId = createOrganization(ownerJwt);
        ownerJwt = selectOrg(ownerJwt, orgId);
        String operatorJwt = memberJwt(ownerJwt, orgId, "operator",
                "g1operatorb", "g1operatorb@test.com");
        String accountantJwt = memberJwt(ownerJwt, orgId, "accountant",
                "g1accountantb", "g1accountantb@test.com");

        UUID workflowId = startedGstr1Workflow(operatorJwt);
        setStage(workflowId, WorkflowStage.PREPARE);

        mockMvc.perform(put("/api/v1/workflows/{id}/gstr1/fill-ins", workflowId)
                .header("Authorization", "Bearer " + accountantJwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"scope":"LEDGER","key":"Input GST - CGST","attribute":"HSN","value":"996334"}
                    """))
                .andExpect(status().isOk());

        MvcResult tablesResult = mockMvc.perform(
                get("/api/v1/workflows/{id}/gstr1/tables", workflowId)
                        .header("Authorization", "Bearer " + accountantJwt))
                .andExpect(status().isOk())
                .andReturn();
        var fillIns = objectMapper.readTree(tablesResult.getResponse().getContentAsString())
                .get("fillIns");
        assertThat(fillIns.isArray()).isTrue();
        assertThat(fillIns.size()).isEqualTo(1);
        assertThat(fillIns.get(0).get("attribute").asText()).isEqualTo("HSN");
        assertThat(fillIns.get(0).get("fixedInPrepare").asBoolean()).isTrue();
    }

    @Test
    void putFillIn_asOperator403() throws Exception {
        String ownerJwt = signupAndLogin("g1ownerc", "g1ownerc@test.com");
        UUID orgId = createOrganization(ownerJwt);
        ownerJwt = selectOrg(ownerJwt, orgId);
        String operatorJwt = memberJwt(ownerJwt, orgId, "operator",
                "g1operatorc", "g1operatorc@test.com");

        UUID workflowId = startedGstr1Workflow(operatorJwt);
        setStage(workflowId, WorkflowStage.PREPARE);

        mockMvc.perform(put("/api/v1/workflows/{id}/gstr1/fill-ins", workflowId)
                .header("Authorization", "Bearer " + operatorJwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"scope":"LEDGER","key":"Input GST - CGST","attribute":"HSN","value":"996334"}
                    """))
                .andExpect(status().isForbidden());
    }

    @Test
    void putFillIn_asOwner403() throws Exception {
        String ownerJwt = signupAndLogin("g1ownerd", "g1ownerd@test.com");
        UUID orgId = createOrganization(ownerJwt);
        ownerJwt = selectOrg(ownerJwt, orgId);
        String operatorJwt = memberJwt(ownerJwt, orgId, "operator",
                "g1operatord", "g1operatord@test.com");

        UUID workflowId = startedGstr1Workflow(operatorJwt);
        setStage(workflowId, WorkflowStage.PREPARE);

        mockMvc.perform(put("/api/v1/workflows/{id}/gstr1/fill-ins", workflowId)
                .header("Authorization", "Bearer " + ownerJwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"scope":"LEDGER","key":"Input GST - CGST","attribute":"HSN","value":"996334"}
                    """))
                .andExpect(status().isForbidden());
    }

    private void setStage(UUID workflowId, WorkflowStage stage) {
        var workflow = workflowRepository.findById(workflowId).orElseThrow();
        workflow.setStage(stage);
        workflowRepository.save(workflow);
    }
}
