package com.arktech.superaccountant.workflow.controllers;

import com.arktech.superaccountant.daybook.models.DayBookUploadJob;
import com.arktech.superaccountant.daybook.repository.DayBookUploadJobRepository;
import com.arktech.superaccountant.masters.models.UploadJobStatus;
import com.arktech.superaccountant.workflow.models.Workflow;
import com.arktech.superaccountant.workflow.repository.WorkflowEventRepository;
import com.arktech.superaccountant.workflow.repository.WorkflowRepository;
import com.arktech.superaccountant.workflow.seam.CompanyBlockerSource;
import com.arktech.superaccountant.workflow.seam.SnapshotService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = "JWT_SECRET=test-jwt-secret-must-be-at-least-32-characters-long")
class BaselineConfirmIT {

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
    private DayBookUploadJobRepository dayBookUploadJobRepository;

    @Autowired
    private WorkflowRepository workflowRepository;

    @Autowired
    private WorkflowEventRepository workflowEventRepository;

    @MockitoSpyBean
    private SnapshotService snapshotService;

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
                    {"name":"Baseline Org","pan":"AAACA1234A","financialYearStart":4}
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
        String ownerJwt = signupAndLogin("bcowner" + unique(), "bc" + unique() + "@test.com", "owner");
        UUID orgId = createOrganization(ownerJwt);
        var org = organizationRepository.findById(orgId).orElseThrow();
        org.setBooksStartDate(LocalDate.of(2025, 4, 1));
        organizationRepository.save(org);
        ownerJwt = selectOrg(ownerJwt, orgId);
        String accountantJwt = memberJwt(ownerJwt, orgId, "accountant",
                "bcacc" + unique(), "bcacc" + unique() + "@test.com");
        String operatorJwt = memberJwt(ownerJwt, orgId, "operator",
                "bcoper" + unique(), "bcoper" + unique() + "@test.com");
        return new Setup(accountantJwt, operatorJwt, orgId);
    }

    @Autowired
    private com.arktech.superaccountant.masters.repository.OrganizationRepository organizationRepository;

    private void putStart(String jwt, String type, String firstPeriodStart, int expectedStatus) throws Exception {
        mockMvc.perform(put("/api/v1/compliance/responsibility-start")
                .header("Authorization", "Bearer " + jwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"type":"%s","firstPeriodStart":"%s"}
                    """.formatted(type, firstPeriodStart)))
                .andExpect(status().is(expectedStatus));
    }

    private DayBookUploadJob completedUpload(UUID orgId, LocalDate from, LocalDate to) {
        DayBookUploadJob job = new DayBookUploadJob();
        job.setOrganizationId(orgId);
        job.setFileName("daybook.json");
        job.setStatus(UploadJobStatus.COMPLETED);
        job.setCoverageFrom(from);
        job.setCoverageTo(to);
        job.setTotalVouchersParsed(0);
        return dayBookUploadJobRepository.saveAndFlush(job);
    }

    @Test
    void confirm_stampsAllRowsAndOpensPrepareGate() throws Exception {
        Setup s = setup();
        putStart(s.accountantJwt(), "TDS_PAYMENT", "2025-07-01", 200);
        putStart(s.accountantJwt(), "GSTR1", "2025-06-01", 200);
        DayBookUploadJob upload = completedUpload(s.orgId(),
                LocalDate.of(2025, 4, 1), LocalDate.of(2025, 7, 31));

        mockMvc.perform(get("/api/v1/compliance/baseline")
                .header("Authorization", "Bearer " + s.accountantJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmed").value(false))
                .andExpect(jsonPath("$.rangeStart").value("2025-04-01"))
                .andExpect(jsonPath("$.rangeEnd").value("2025-06-30"))
                .andExpect(jsonPath("$.importedFiledData").value("not available"));

        mockMvc.perform(post("/api/v1/compliance/baseline/confirm")
                .header("Authorization", "Bearer " + s.accountantJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmed").value(true));

        List<Workflow> baseline = workflowRepository
                .findAllByOrganizationIdAndStageAndActiveTrue(s.orgId(), com.arktech.superaccountant.workflow.models.WorkflowStage.BASELINE);
        assertEquals(5, baseline.size());
        for (Workflow w : baseline) {
            assertNotNull(w.getBaselineConfirmedAt());
            assertNotNull(w.getBaselineConfirmedBy());
            assertEquals(upload.getId(), w.getUploadJobId());
            assertEquals(w.getPeriodEnd(), w.getFrozenThrough());
            assertEquals(1, workflowEventRepository
                    .findAllByWorkflowIdOrderByOccurredAtAsc(w.getId()).stream()
                    .filter(e -> e.getKind() == com.arktech.superaccountant.workflow.models.WorkflowEventKind.BASELINE_CONFIRMED)
                    .count());
        }
    }

    @Test
    void confirm_callsFreezeOncePerType() throws Exception {
        Setup s = setup();
        putStart(s.accountantJwt(), "TDS_PAYMENT", "2025-07-01", 200);
        putStart(s.accountantJwt(), "GSTR1", "2025-06-01", 200);
        completedUpload(s.orgId(), LocalDate.of(2025, 4, 1), LocalDate.of(2025, 7, 31));

        mockMvc.perform(post("/api/v1/compliance/baseline/confirm")
                .header("Authorization", "Bearer " + s.accountantJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isOk());

        List<Workflow> baseline = workflowRepository
                .findAllByOrganizationIdAndStageAndActiveTrue(s.orgId(), com.arktech.superaccountant.workflow.models.WorkflowStage.BASELINE);
        Workflow tds = baseline.stream().filter(w -> w.getType() == com.arktech.superaccountant.workflow.models.WorkflowType.TDS_PAYMENT)
                .max(java.util.Comparator.comparing(Workflow::getPeriodEnd)).orElseThrow();
        Workflow gstr1 = baseline.stream().filter(w -> w.getType() == com.arktech.superaccountant.workflow.models.WorkflowType.GSTR1)
                .max(java.util.Comparator.comparing(Workflow::getPeriodEnd)).orElseThrow();
        verify(snapshotService, times(2)).freeze(any(), eq(s.orgId()), any());
        verify(snapshotService).freeze(eq(tds.getId()), eq(s.orgId()), any());
        verify(snapshotService).freeze(eq(gstr1.getId()), eq(s.orgId()), any());
    }

    @Test
    void confirm_noUnconfirmedRows_is409() throws Exception {
        Setup s = setup();
        mockMvc.perform(post("/api/v1/compliance/baseline/confirm")
                .header("Authorization", "Bearer " + s.accountantJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("No unconfirmed baseline periods"));
    }

    @Test
    void confirm_noCompletedUpload_is409() throws Exception {
        Setup s = setup();
        putStart(s.accountantJwt(), "TDS_PAYMENT", "2025-07-01", 200);
        mockMvc.perform(post("/api/v1/compliance/baseline/confirm")
                .header("Authorization", "Bearer " + s.accountantJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value(
                        "Upload a day book covering the baseline range first"));
    }

    @Test
    void confirm_otherOrgsUpload_is400() throws Exception {
        Setup s = setup();
        Setup other = setup();
        putStart(s.accountantJwt(), "TDS_PAYMENT", "2025-07-01", 200);
        DayBookUploadJob upload = completedUpload(other.orgId(),
                LocalDate.of(2025, 4, 1), LocalDate.of(2025, 7, 31));

        mockMvc.perform(post("/api/v1/compliance/baseline/confirm")
                .header("Authorization", "Bearer " + s.accountantJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"uploadJobId\":\"" + upload.getId() + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Upload not found"));

        assertTrue(workflowRepository
                .findAllByOrganizationIdAndStageAndActiveTrue(s.orgId(), com.arktech.superaccountant.workflow.models.WorkflowStage.BASELINE)
                .stream().allMatch(w -> w.getBaselineConfirmedAt() == null));
    }

    @Test
    void confirm_uploadNotCoveringRange_is409() throws Exception {
        Setup s = setup();
        putStart(s.accountantJwt(), "TDS_PAYMENT", "2025-07-01", 200);
        completedUpload(s.orgId(), LocalDate.of(2025, 4, 1), LocalDate.of(2025, 5, 31));

        mockMvc.perform(post("/api/v1/compliance/baseline/confirm")
                .header("Authorization", "Bearer " + s.accountantJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value(
                        "Upload does not cover the baseline range 2025-04-01..2025-06-30"));

        // null coverage upload also refused
        DayBookUploadJob legacy = new DayBookUploadJob();
        legacy.setOrganizationId(s.orgId());
        legacy.setFileName("daybook.json");
        legacy.setStatus(UploadJobStatus.COMPLETED);
        legacy.setTotalVouchersParsed(0);
        dayBookUploadJobRepository.saveAndFlush(legacy);

        mockMvc.perform(post("/api/v1/compliance/baseline/confirm")
                .header("Authorization", "Bearer " + s.accountantJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isConflict());
    }

    @Test
    void confirm_withBlocker_is409() throws Exception {
        Setup s = setup();
        putStart(s.accountantJwt(), "TDS_PAYMENT", "2025-07-01", 200);
        completedUpload(s.orgId(), LocalDate.of(2025, 4, 1), LocalDate.of(2025, 7, 31));
        fakeBlockers.add("Open tamper finding T-1");

        mockMvc.perform(get("/api/v1/compliance/baseline")
                .header("Authorization", "Bearer " + s.accountantJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blockers[0]").value("Open tamper finding T-1"));

        mockMvc.perform(post("/api/v1/compliance/baseline/confirm")
                .header("Authorization", "Bearer " + s.accountantJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value(
                        org.hamcrest.Matchers.containsString("Open tamper finding T-1")));
    }

    @Test
    void put_afterConfirm_rebuildsUnconfirmed() throws Exception {
        Setup s = setup();
        putStart(s.accountantJwt(), "TDS_PAYMENT", "2025-07-01", 200);
        completedUpload(s.orgId(), LocalDate.of(2025, 4, 1), LocalDate.of(2025, 7, 31));
        mockMvc.perform(post("/api/v1/compliance/baseline/confirm")
                .header("Authorization", "Bearer " + s.accountantJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isOk());

        MvcResult started = mockMvc.perform(post("/api/v1/workflows")
                .header("Authorization", "Bearer " + s.operatorJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"type":"GSTR1","periodKey":"2025-06"}
                    """))
                .andExpect(status().isOk())
                .andReturn();
        String workflowId = objectMapper.readTree(started.getResponse().getContentAsString())
                .get("id").asText();

        putStart(s.accountantJwt(), "TDS_PAYMENT", "2025-08-01", 200);

        mockMvc.perform(get("/api/v1/compliance/baseline")
                .header("Authorization", "Bearer " + s.accountantJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmed").value(false));

        mockMvc.perform(get("/api/v1/workflows/{id}", workflowId)
                .header("Authorization", "Bearer " + s.accountantJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baselineConfirmed").value(false));
    }

    @Test
    void put_afterConfirm_moveEarlier_releasesSnapshot() throws Exception {
        Setup s = setup();
        putStart(s.accountantJwt(), "GSTR1", "2025-06-01", 200);
        completedUpload(s.orgId(), LocalDate.of(2025, 4, 1), LocalDate.of(2025, 7, 31));
        mockMvc.perform(post("/api/v1/compliance/baseline/confirm")
                .header("Authorization", "Bearer " + s.accountantJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isOk());

        org.mockito.Mockito.clearInvocations(snapshotService);
        putStart(s.accountantJwt(), "GSTR1", "2025-05-01", 200);
        verify(snapshotService, times(1)).release(any(), eq(s.orgId()), any());

        putStart(s.accountantJwt(), "TDS_PAYMENT", "2025-07-01", 200);
        verify(snapshotService, times(1)).release(any(), eq(s.orgId()), any());
    }

    @Test
    void advance_isRefusedWhileCompanyHasUnconfirmedRows() throws Exception {
        Setup s = setup();
        putStart(s.accountantJwt(), "TDS_PAYMENT", "2025-07-01", 200);

        // GSTR1 has no baseline rows of its own; the company-wide gate still refuses.
        MvcResult started = mockMvc.perform(post("/api/v1/workflows")
                .header("Authorization", "Bearer " + s.operatorJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"type":"GSTR1","periodKey":"2025-06"}
                    """))
                .andExpect(status().isOk())
                .andReturn();
        UUID workflowId = UUID.fromString(objectMapper
                .readTree(started.getResponse().getContentAsString()).get("id").asText());

        Workflow w = workflowRepository.findById(workflowId).orElseThrow();
        w.setStage(com.arktech.superaccountant.workflow.models.WorkflowStage.FINDINGS_REVIEW);
        workflowRepository.saveAndFlush(w);

        mockMvc.perform(post("/api/v1/workflows/{id}/advance", workflowId)
                .header("Authorization", "Bearer " + s.accountantJwt()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value(
                        "Baseline must be confirmed before Prepare"));

        completedUpload(s.orgId(), LocalDate.of(2025, 4, 1), LocalDate.of(2025, 7, 31));
        mockMvc.perform(post("/api/v1/compliance/baseline/confirm")
                .header("Authorization", "Bearer " + s.accountantJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/workflows/{id}/advance", workflowId)
                .header("Authorization", "Bearer " + s.accountantJwt()))
                .andExpect(status().isOk());
    }

    @Test
    void confirm_byOperator_is403() throws Exception {
        Setup s = setup();
        putStart(s.accountantJwt(), "TDS_PAYMENT", "2025-07-01", 200);
        mockMvc.perform(post("/api/v1/compliance/baseline/confirm")
                .header("Authorization", "Bearer " + s.operatorJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isForbidden());
    }
}
