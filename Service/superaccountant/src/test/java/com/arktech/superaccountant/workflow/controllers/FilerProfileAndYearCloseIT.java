package com.arktech.superaccountant.workflow.controllers;

import com.arktech.superaccountant.masters.models.Organization;
import com.arktech.superaccountant.masters.repository.OrganizationRepository;
import com.arktech.superaccountant.workflow.models.Workflow;
import com.arktech.superaccountant.workflow.models.WorkflowStage;
import com.arktech.superaccountant.workflow.models.WorkflowType;
import com.arktech.superaccountant.workflow.repository.WorkflowRepository;
import com.arktech.superaccountant.workflow.seam.CompanyBlockerSource;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = "JWT_SECRET=test-jwt-secret-must-be-at-least-32-characters-long")
class FilerProfileAndYearCloseIT {

    private static final List<String> fakeBlockers = new ArrayList<>();

    @TestConfiguration
    static class BlockerConfig {
        @Bean
        CompanyBlockerSource fakeCompanyBlockerSource() {
            return new CompanyBlockerSource() {
                @Override
                public List<String> financialYearCloseBlockers(UUID organizationId, int financialYear) {
                    return List.copyOf(fakeBlockers);
                }

                @Override
                public List<String> baselineConfirmBlockers(UUID organizationId) {
                    return List.copyOf(fakeBlockers);
                }
            };
        }
    }

    @AfterEach
    void clearBlockers() {
        fakeBlockers.clear();
    }

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
    private OrganizationRepository organizationRepository;

    @Autowired
    private WorkflowRepository workflowRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static int counter = 0;

    private static String unique() {
        return String.valueOf(++counter);
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
                    {"name":"FY Org","pan":"AAACA1234A","financialYearStart":4}
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

    private record Setup(String accountantJwt, String operatorJwt, UUID orgId) {
    }

    private Setup setup() throws Exception {
        fakeBlockers.clear();
        String ownerJwt = signupAndLogin("fyowner" + unique(), "fy" + unique() + "@test.com", "owner");
        UUID orgId = createOrganization(ownerJwt);
        Organization org = organizationRepository.findById(orgId).orElseThrow();
        org.setBooksStartDate(LocalDate.of(2024, 4, 1));
        organizationRepository.save(org);
        ownerJwt = selectOrg(ownerJwt, orgId);
        String accountantJwt = memberJwt(ownerJwt, orgId, "accountant",
                "fyacc" + unique(), "fyacc" + unique() + "@test.com");
        String operatorJwt = memberJwt(ownerJwt, orgId, "operator",
                "fyoper" + unique(), "fyoper" + unique() + "@test.com");
        return new Setup(accountantJwt, operatorJwt, orgId);
    }

    private void putProfile(String jwt, String body, int expectedStatus) throws Exception {
        mockMvc.perform(put("/api/v1/compliance/filer-profile")
                .header("Authorization", "Bearer " + jwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().is(expectedStatus));
    }

    private List<String> activeBaselineKeys(String jwt, String type) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/workflows")
                .param("type", type)
                .param("stage", "BASELINE")
                .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andReturn();
        List<String> keys = new ArrayList<>();
        for (var row : objectMapper.readTree(result.getResponse().getContentAsString())) {
            if (row.get("active").asBoolean()) {
                keys.add(row.get("periodKey").asText());
            }
        }
        return keys.stream().sorted().toList();
    }

    private void markFiled(UUID orgId, WorkflowType type, String periodKey,
                           LocalDate start, LocalDate end) {
        Workflow w = new Workflow();
        w.setOrganizationId(orgId);
        w.setType(type);
        w.setPeriodKey(periodKey);
        w.setPeriodStart(start);
        w.setPeriodEnd(end);
        w.setFinancialYear(2024);
        w.setStage(WorkflowStage.FILED);
        w.setActive(true);
        w.setCreatedBy("seed");
        workflowRepository.save(w);
    }

    @Test
    void putProfile_addsRow_andGetListsAscending() throws Exception {
        Setup s = setup();
        putProfile(s.accountantJwt(), """
            {"frequency":"QRMP","iffOptIn":false,"einvoiceEnabled":false,"effectiveFrom":"2025-04-01"}
            """, 200);
        putProfile(s.accountantJwt(), """
            {"frequency":"MONTHLY","iffOptIn":false,"einvoiceEnabled":false,"effectiveFrom":"2024-04-01"}
            """, 200);

        mockMvc.perform(get("/api/v1/compliance/filer-profile")
                .header("Authorization", "Bearer " + s.accountantJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].effectiveFrom").value("2024-04-01"))
                .andExpect(jsonPath("$[1].effectiveFrom").value("2025-04-01"));
    }

    @Test
    void putProfile_notQuarterStart_is400() throws Exception {
        Setup s = setup();
        putProfile(s.accountantJwt(), """
            {"frequency":"QRMP","iffOptIn":false,"einvoiceEnabled":false,"effectiveFrom":"2025-05-01"}
            """, 400);
    }

    @Test
    void putProfile_sameEffectiveFrom_replaces() throws Exception {
        Setup s = setup();
        putProfile(s.accountantJwt(), """
            {"frequency":"QRMP","iffOptIn":false,"einvoiceEnabled":false,"effectiveFrom":"2025-04-01"}
            """, 200);
        putProfile(s.accountantJwt(), """
            {"frequency":"MONTHLY","iffOptIn":true,"einvoiceEnabled":true,"effectiveFrom":"2025-04-01"}
            """, 200);

        mockMvc.perform(get("/api/v1/compliance/filer-profile")
                .header("Authorization", "Bearer " + s.accountantJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].frequency").value("MONTHLY"))
                .andExpect(jsonPath("$[0].iffOptIn").value(true));
    }

    @Test
    void putProfile_onOrBeforeStartedGstWorkflow_is409() throws Exception {
        Setup s = setup();
        mockMvc.perform(post("/api/v1/workflows")
                .header("Authorization", "Bearer " + s.operatorJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"type":"GSTR1","periodKey":"2024-04"}
                    """))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/compliance/filer-profile")
                .header("Authorization", "Bearer " + s.accountantJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"frequency":"QRMP","iffOptIn":false,"einvoiceEnabled":false,"effectiveFrom":"2024-04-01"}
                    """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value(
                        "GST workflows already started on or after 2024-04-01"));
    }

    @Test
    void putProfile_rebuildsGstBaselineRows() throws Exception {
        Setup s = setup();
        mockMvc.perform(put("/api/v1/compliance/responsibility-start")
                .header("Authorization", "Bearer " + s.accountantJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"type":"GSTR3B","firstPeriodStart":"2025-04-01"}
                    """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baselineRowCount").value(12));

        assertEquals(List.of("2024-04", "2024-05", "2024-06", "2024-07", "2024-08", "2024-09",
                "2024-10", "2024-11", "2024-12", "2025-01", "2025-02", "2025-03"),
                activeBaselineKeys(s.accountantJwt(), "GSTR3B"));

        putProfile(s.accountantJwt(), """
            {"frequency":"QRMP","iffOptIn":false,"einvoiceEnabled":false,"effectiveFrom":"2024-10-01"}
            """, 200);

        assertEquals(List.of("2024-04", "2024-05", "2024-06", "2024-07", "2024-08", "2024-09",
                "2024-Q3", "2024-Q4"),
                activeBaselineKeys(s.accountantJwt(), "GSTR3B"));

        mockMvc.perform(get("/api/v1/compliance/baseline")
                .header("Authorization", "Bearer " + s.accountantJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmed").value(false));
    }

    @Test
    void close_itrFiled_refusedWhilePeriodsOpen() throws Exception {
        Setup s = setup();
        mockMvc.perform(put("/api/v1/compliance/responsibility-start")
                .header("Authorization", "Bearer " + s.accountantJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"type":"TDS_PAYMENT","firstPeriodStart":"2024-07-01"}
                    """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/compliance/financial-years/2024/close")
                .header("Authorization", "Bearer " + s.accountantJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"closeType":"ITR_FILED","itrAckNo":"ACK123","itrAckDate":"2025-10-01"}
                    """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value(
                        org.hamcrest.Matchers.containsString("TDS_PAYMENT 2024-07: not started")));
    }

    @Test
    void close_itrFiled_requiresAck() throws Exception {
        Setup s = setup();
        mockMvc.perform(post("/api/v1/compliance/financial-years/2024/close")
                .header("Authorization", "Bearer " + s.accountantJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"closeType":"ITR_FILED"}
                    """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void close_itrFiled_refusedWithBlocker_thenCloses() throws Exception {
        Setup s = setup();
        mockMvc.perform(put("/api/v1/compliance/responsibility-start")
                .header("Authorization", "Bearer " + s.accountantJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"type":"TDS_PAYMENT","firstPeriodStart":"2024-07-01"}
                    """))
                .andExpect(status().isOk());
        // remaining TDS_PAYMENT periods of FY2024 as FILED
        markFiled(s.orgId(), WorkflowType.TDS_PAYMENT, "2024-07", LocalDate.of(2024, 7, 1), LocalDate.of(2024, 7, 31));
        markFiled(s.orgId(), WorkflowType.TDS_PAYMENT, "2024-08", LocalDate.of(2024, 8, 1), LocalDate.of(2024, 8, 31));
        markFiled(s.orgId(), WorkflowType.TDS_PAYMENT, "2024-09", LocalDate.of(2024, 9, 1), LocalDate.of(2024, 9, 30));
        markFiled(s.orgId(), WorkflowType.TDS_PAYMENT, "2024-10", LocalDate.of(2024, 10, 1), LocalDate.of(2024, 10, 31));
        markFiled(s.orgId(), WorkflowType.TDS_PAYMENT, "2024-11", LocalDate.of(2024, 11, 1), LocalDate.of(2024, 11, 30));
        markFiled(s.orgId(), WorkflowType.TDS_PAYMENT, "2024-12", LocalDate.of(2024, 12, 1), LocalDate.of(2024, 12, 31));
        markFiled(s.orgId(), WorkflowType.TDS_PAYMENT, "2025-01", LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31));
        markFiled(s.orgId(), WorkflowType.TDS_PAYMENT, "2025-02", LocalDate.of(2025, 2, 1), LocalDate.of(2025, 2, 28));
        markFiled(s.orgId(), WorkflowType.TDS_PAYMENT, "2025-03", LocalDate.of(2025, 3, 1), LocalDate.of(2025, 3, 31));

        fakeBlockers.add("Open amendment follow-up A-1");
        mockMvc.perform(post("/api/v1/compliance/financial-years/2024/close")
                .header("Authorization", "Bearer " + s.accountantJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"closeType":"ITR_FILED","itrAckNo":"ACK123","itrAckDate":"2025-10-01"}
                    """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value(
                        org.hamcrest.Matchers.containsString("Open amendment follow-up A-1")));

        fakeBlockers.clear();
        mockMvc.perform(post("/api/v1/compliance/financial-years/2024/close")
                .header("Authorization", "Bearer " + s.accountantJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"closeType":"ITR_FILED","itrAckNo":"ACK123","itrAckDate":"2025-10-01"}
                    """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.year.closed").value(true));

        // coverage moved past the closed FY
        mockMvc.perform(post("/api/v1/workflows")
                .header("Authorization", "Bearer " + s.operatorJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"type":"GSTR1","periodKey":"2024-05"}
                    """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(
                        "Period starts before coverage start 2025-04-01"));
    }

    @Test
    void close_handledElsewhere_warnsButCloses() throws Exception {
        Setup s = setup();
        mockMvc.perform(put("/api/v1/compliance/responsibility-start")
                .header("Authorization", "Bearer " + s.accountantJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"type":"TDS_PAYMENT","firstPeriodStart":"2024-07-01"}
                    """))
                .andExpect(status().isOk());
        fakeBlockers.add("Open amendment follow-up A-1");

        mockMvc.perform(post("/api/v1/compliance/financial-years/2024/close")
                .header("Authorization", "Bearer " + s.accountantJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"closeType":"HANDLED_ELSEWHERE"}
                    """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/compliance/financial-years/2024/close")
                .header("Authorization", "Bearer " + s.accountantJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"closeType":"HANDLED_ELSEWHERE","note":"Prior CA"}
                    """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.warnings.length()").value(
                        org.hamcrest.Matchers.greaterThan(0)));
    }

    @Test
    void close_outOfOrder_is409() throws Exception {
        Setup s = setup();
        mockMvc.perform(post("/api/v1/compliance/financial-years/2025/close")
                .header("Authorization", "Bearer " + s.accountantJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"closeType":"ITR_FILED","itrAckNo":"ACK123","itrAckDate":"2026-10-01"}
                    """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Close FY 2024 first"));
    }

    @Test
    void close_notEnded_is409() throws Exception {
        Setup s = setup();
        mockMvc.perform(post("/api/v1/compliance/financial-years/2026/close")
                .header("Authorization", "Bearer " + s.accountantJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"closeType":"ITR_FILED","itrAckNo":"ACK123","itrAckDate":"2026-10-01"}
                    """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("FY has not ended"));
    }

    @Test
    void close_alreadyClosed_is409() throws Exception {
        Setup s = setup();
        mockMvc.perform(post("/api/v1/compliance/financial-years/2024/close")
                .header("Authorization", "Bearer " + s.accountantJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"closeType":"HANDLED_ELSEWHERE","note":"Prior CA"}
                    """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/compliance/financial-years/2024/close")
                .header("Authorization", "Bearer " + s.accountantJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"closeType":"HANDLED_ELSEWHERE","note":"Again"}
                    """))
                .andExpect(status().isConflict());
    }

    @Test
    void close_byOperator_is403() throws Exception {
        Setup s = setup();
        mockMvc.perform(post("/api/v1/compliance/financial-years/2024/close")
                .header("Authorization", "Bearer " + s.operatorJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"closeType":"HANDLED_ELSEWHERE","note":"x"}
                    """))
                .andExpect(status().isForbidden());
    }

    @Test
    void listYears_showsOpenItemsAndClosable() throws Exception {
        Setup s = setup();
        mockMvc.perform(put("/api/v1/compliance/responsibility-start")
                .header("Authorization", "Bearer " + s.accountantJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"type":"TDS_PAYMENT","firstPeriodStart":"2024-07-01"}
                    """))
                .andExpect(status().isOk());

        MvcResult result = mockMvc.perform(get("/api/v1/compliance/financial-years")
                .header("Authorization", "Bearer " + s.accountantJwt()))
                .andExpect(status().isOk())
                .andReturn();
        var years = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(2024, years.get(0).get("financialYear").asInt());
        assertEquals(false, years.get(0).get("closable").asBoolean());
        assertTrue(years.get(0).get("openItems").size() > 0);
    }
}
