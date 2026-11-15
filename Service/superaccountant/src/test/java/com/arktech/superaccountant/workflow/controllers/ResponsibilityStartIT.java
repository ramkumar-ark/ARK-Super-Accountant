package com.arktech.superaccountant.workflow.controllers;

import com.arktech.superaccountant.masters.models.Organization;
import com.arktech.superaccountant.masters.repository.OrganizationRepository;
import com.arktech.superaccountant.workflow.models.Workflow;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = "JWT_SECRET=test-jwt-secret-must-be-at-least-32-characters-long")
class ResponsibilityStartIT {

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

    @Autowired
    private OrganizationRepository organizationRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

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
                    {"name":"Compliance Org","financialYearStart":4,"booksStartDate":"2025-04-01"}
                    """))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(
                objectMapper.readTree(result.getResponse().getContentAsString())
                        .get("id").asText());
    }

    private void setBooksStartDate(UUID orgId) {
        Organization org = organizationRepository.findById(orgId).orElseThrow();
        org.setBooksStartDate(java.time.LocalDate.of(2025, 4, 1));
        organizationRepository.save(org);
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

    private void putStart(String jwt, String type, String firstPeriodStart, int expectedStatus) throws Exception {
        mockMvc.perform(put("/api/v1/compliance/responsibility-start")
                .header("Authorization", "Bearer " + jwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"type":"%s","firstPeriodStart":"%s"}
                    """.formatted(type, firstPeriodStart)))
                .andExpect(status().is(expectedStatus));
    }

    private record Setup(String accountantJwt, String operatorJwt, UUID orgId) {
    }

    private Setup setup() throws Exception {
        String ownerJwt = signupAndLogin("rsowner" + unique(), uniqueEmail(), "owner");
        UUID orgId = createOrganization(ownerJwt);
        setBooksStartDate(orgId);
        ownerJwt = selectOrg(ownerJwt, orgId);
        String accountantJwt = memberJwt(ownerJwt, orgId, "accountant",
                "rsacc" + unique(), uniqueEmail());
        String operatorJwt = memberJwt(ownerJwt, orgId, "operator",
                "rsoper" + unique(), uniqueEmail());
        return new Setup(accountantJwt, operatorJwt, orgId);
    }

    private static int counter = 0;

    private static String unique() {
        return String.valueOf(++counter);
    }

    private static String uniqueEmail() {
        return "rs" + unique() + "@test.com";
    }

    private List<String> activeBaselineKeys(String jwt, String type) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/workflows")
                .param("type", type)
                .param("stage", "BASELINE")
                .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andReturn();
        List<String> keys = new java.util.ArrayList<>();
        for (var row : objectMapper.readTree(result.getResponse().getContentAsString())) {
            if (row.get("active").asBoolean()) {
                keys.add(row.get("periodKey").asText());
            }
        }
        return keys.stream().sorted().toList();
    }

    @Test
    void put_createsBaselineRowsBeforeStart() throws Exception {
        Setup s = setup();
        MvcResult result = mockMvc.perform(put("/api/v1/compliance/responsibility-start")
                .header("Authorization", "Bearer " + s.accountantJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"type":"TDS_PAYMENT","firstPeriodStart":"2025-07-01"}
                    """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baselineRowCount").value(3))
                .andExpect(jsonPath("$.locked").value(false))
                .andReturn();

        mockMvc.perform(get("/api/v1/workflows")
                .param("type", "TDS_PAYMENT")
                .param("stage", "BASELINE")
                .header("Authorization", "Bearer " + s.accountantJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[*].periodKey").value(
                        org.hamcrest.Matchers.containsInAnyOrder("2025-04", "2025-05", "2025-06")))
                .andExpect(jsonPath("$[*].baselineConfirmedAt",
                        org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.nullValue())));
        assertEquals("TDS_PAYMENT", objectMapper.readTree(result.getResponse().getContentAsString())
                .get("type").asText());
    }

    @Test
    void put_atCoverageStart_createsNoRows() throws Exception {
        Setup s = setup();
        mockMvc.perform(put("/api/v1/compliance/responsibility-start")
                .header("Authorization", "Bearer " + s.accountantJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"type":"TDS_PAYMENT","firstPeriodStart":"2025-04-01"}
                    """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baselineRowCount").value(0));
    }

    @Test
    void put_notAPeriodStart_is400() throws Exception {
        Setup s = setup();
        mockMvc.perform(put("/api/v1/compliance/responsibility-start")
                .header("Authorization", "Bearer " + s.accountantJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"type":"TDS_PAYMENT","firstPeriodStart":"2025-07-15"}
                    """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(
                        "firstPeriodStart must be a period start for TDS_PAYMENT"));

        mockMvc.perform(put("/api/v1/compliance/responsibility-start")
                .header("Authorization", "Bearer " + s.accountantJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"type":"TDS_26Q","firstPeriodStart":"2025-08-01"}
                    """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(
                        "firstPeriodStart must be a period start for TDS_26Q"));
    }

    @Test
    void put_change_deactivatesOldRows() throws Exception {
        Setup s = setup();
        putStart(s.accountantJwt(), "TDS_PAYMENT", "2025-07-01", 200);
        putStart(s.accountantJwt(), "TDS_PAYMENT", "2025-06-01", 200);

        assertEquals(List.of("2025-04", "2025-05"), activeBaselineKeys(s.accountantJwt(), "TDS_PAYMENT"));

        UUID orgId = s.orgId();
        List<Workflow> all = workflowRepository.findAll().stream()
                .filter(w -> w.getOrganizationId().equals(orgId))
                .toList();
        Workflow june = all.stream()
                .filter(w -> w.getPeriodKey().equals("2025-06"))
                .findFirst().orElseThrow();
        assertFalse(june.isActive());
    }

    @Test
    void put_lockedOnceRealWorkflowExists() throws Exception {
        Setup s = setup();
        putStart(s.accountantJwt(), "TDS_PAYMENT", "2025-07-01", 200);
        mockMvc.perform(post("/api/v1/workflows")
                .header("Authorization", "Bearer " + s.operatorJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"type":"TDS_PAYMENT","periodKey":"2025-07"}
                    """))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/compliance/responsibility-start")
                .header("Authorization", "Bearer " + s.accountantJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"type":"TDS_PAYMENT","firstPeriodStart":"2025-06-01"}
                    """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value(
                        org.hamcrest.Matchers.containsString("locked")));

        mockMvc.perform(get("/api/v1/compliance/responsibility-start")
                .header("Authorization", "Bearer " + s.accountantJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.type=='TDS_PAYMENT')].locked").value(true));
    }

    @Test
    void start_beforeResponsibilityStart_is400() throws Exception {
        Setup s = setup();
        putStart(s.accountantJwt(), "TDS_PAYMENT", "2025-07-01", 200);

        mockMvc.perform(post("/api/v1/workflows")
                .header("Authorization", "Bearer " + s.operatorJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"type":"TDS_PAYMENT","periodKey":"2025-06"}
                    """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(
                        "Period starts before responsibility start 2025-07-01"));

        mockMvc.perform(post("/api/v1/workflows")
                .header("Authorization", "Bearer " + s.operatorJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"type":"GSTR1","periodKey":"2025-06"}
                    """))
                .andExpect(status().isOk());
    }

    @Test
    void put_byOperator_is403() throws Exception {
        Setup s = setup();
        mockMvc.perform(put("/api/v1/compliance/responsibility-start")
                .header("Authorization", "Bearer " + s.operatorJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"type":"TDS_PAYMENT","firstPeriodStart":"2025-07-01"}
                    """))
                .andExpect(status().isForbidden());
        assertTrue(true);
    }
}
