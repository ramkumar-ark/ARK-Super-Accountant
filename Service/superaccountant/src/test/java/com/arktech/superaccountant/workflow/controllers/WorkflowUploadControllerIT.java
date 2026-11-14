package com.arktech.superaccountant.workflow.controllers;

import com.arktech.superaccountant.daybook.models.DayBookUploadJob;
import com.arktech.superaccountant.daybook.repository.DayBookUploadJobRepository;
import com.arktech.superaccountant.masters.models.UploadJob;
import com.arktech.superaccountant.masters.models.UploadJobStatus;
import com.arktech.superaccountant.masters.models.UploadLedgerSnapshot;
import com.arktech.superaccountant.masters.repository.OrganizationRepository;
import com.arktech.superaccountant.masters.repository.UploadJobRepository;
import com.arktech.superaccountant.masters.repository.UploadLedgerSnapshotRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Transactional
@TestPropertySource(properties = "JWT_SECRET=test-jwt-secret-must-be-at-least-32-characters-long")
class WorkflowUploadControllerIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private UploadJobRepository uploadJobRepository;
    @Autowired private UploadLedgerSnapshotRepository uploadLedgerSnapshotRepository;
    @Autowired private DayBookUploadJobRepository dayBookUploadJobRepository;

    private static final List<String> FIXTURE_LEDGER_NAMES = List.of(
            "Bank Account", "Cement Purchases", "Cement Supplier", "Client ABC", "Depreciation",
            "Input CGST @9%", "Input IGST @18%", "Input SGST @9%", "Output CGST @9%",
            "Output SGST @9%", "Plant & Machinery", "Sales Account", "Steel Purchases",
            "Test Cement Purchases", "Test Purchases");

    @Test
    void upload_bindsAndMovesToDataUploaded() throws Exception {
        String token = setupAccountantOrg();
        UUID workflowId = startWorkflow(token);

        byte[] fixture = getClass().getResourceAsStream("/fixtures/daybook-multi-type.json").readAllBytes();
        MvcResult result = mockMvc.perform(
                        multipart("/api/v1/workflows/" + workflowId + "/upload")
                                .file(new MockMultipartFile("file", "daybook-multi-type.json",
                                        "application/json", fixture))
                                .param("coverageFrom", "2025-04-01")
                                .param("coverageTo", "2025-05-31")
                                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("DATA_UPLOADED"))
                .andExpect(jsonPath("$.warnings").isArray())
                .andReturn();

        Map<?, ?> body = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        UUID uploadJobId = UUID.fromString(body.get("uploadJobId").toString());
        assertThat(dayBookUploadJobRepository.findById(uploadJobId))
                .hasValueSatisfying(job ->
                        assertThat(job.getCreatedForWorkflowId()).isEqualTo(workflowId));
    }

    @Test
    void upload_lateFirstVoucher_returnsWarning() throws Exception {
        String token = setupAccountantOrg();
        UUID workflowId = startWorkflow(token);
        byte[] fixture = getClass().getResourceAsStream("/fixtures/daybook-minimal.json").readAllBytes();

        mockMvc.perform(
                        multipart("/api/v1/workflows/" + workflowId + "/upload")
                                .file(new MockMultipartFile("file", "daybook-minimal.json",
                                        "application/json", fixture))
                                .param("coverageFrom", "2025-03-01")
                                .param("coverageTo", "2025-04-30")
                                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.warnings[0]").value(
                        org.hamcrest.Matchers.startsWith("First voucher is dated")));
    }

    @Test
    void useUpload_latestCompleted_binds() throws Exception {
        String token = setupAccountantOrg();
        UUID workflowId = startWorkflow(token);

        // Legacy upload with coverage, marked COMPLETED
        DayBookUploadJob job = new DayBookUploadJob();
        job.setOrganizationId(orgId);
        job.setFileName("daybook.json");
        job.setStatus(UploadJobStatus.COMPLETED);
        job.setCoverageFrom(LocalDate.of(2025, 4, 1));
        job.setCoverageTo(LocalDate.of(2025, 5, 31));
        job.setTotalVouchersParsed(1);
        job = dayBookUploadJobRepository.saveAndFlush(job);

        mockMvc.perform(post("/api/v1/workflows/" + workflowId + "/use-upload")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"uploadJobId\":\"" + job.getId() + "\"}")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("DATA_UPLOADED"));
    }

    @Test
    void useUpload_otherOrgJob_returns404() throws Exception {
        String token1 = setupAccountantOrg();
        // Second org job
        DayBookUploadJob foreign = new DayBookUploadJob();
        foreign.setOrganizationId(UUID.randomUUID());
        foreign.setFileName("other.json");
        foreign.setStatus(UploadJobStatus.COMPLETED);
        foreign.setCoverageFrom(LocalDate.of(2025, 4, 1));
        foreign.setCoverageTo(LocalDate.of(2025, 5, 31));
        foreign = dayBookUploadJobRepository.saveAndFlush(foreign);

        UUID workflowId = startWorkflow(token1);
        mockMvc.perform(post("/api/v1/workflows/" + workflowId + "/use-upload")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"uploadJobId\":\"" + foreign.getId() + "\"}")
                        .header("Authorization", "Bearer " + token1))
                .andExpect(status().isNotFound());
    }

    @Test
    void upload_byOwner_returns403() throws Exception {
        // WorkflowController.start is ACCOUNTANT/OPERATOR-only, so an owner cannot
        // even create a workflow; the upload endpoint must refuse owners too.
        String ownerToken = setupOwnerOrg();
        byte[] fixture = getClass().getResourceAsStream("/fixtures/daybook-minimal.json").readAllBytes();

        mockMvc.perform(
                        multipart("/api/v1/workflows/" + UUID.randomUUID() + "/upload")
                                .file(new MockMultipartFile("file", "daybook-minimal.json",
                                        "application/json", fixture))
                                .param("coverageFrom", "2025-04-01")
                                .param("coverageTo", "2025-05-31")
                                .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isForbidden());
    }

    private UUID orgId;

    private void seedValidatedMasters(UUID org) {
        UploadJob masters = new UploadJob();
        masters.setOrganizationId(org);
        masters.setFileName("masters-fixture.json");
        masters.setStatus(UploadJobStatus.COMPLETED);
        masters.setTotalLedgersParsed(FIXTURE_LEDGER_NAMES.size());
        masters = uploadJobRepository.saveAndFlush(masters);
        for (String name : FIXTURE_LEDGER_NAMES) {
            UploadLedgerSnapshot snapshot = new UploadLedgerSnapshot();
            snapshot.setUploadJobId(masters.getId());
            snapshot.setOrganizationId(org);
            snapshot.setLedgerName(name);
            uploadLedgerSnapshotRepository.save(snapshot);
        }
        uploadLedgerSnapshotRepository.flush();
    }

    /**
     * Signup (accountant), create org, set booksStartDate so 2025-04 is coverable,
     * seed validated masters, select the org and return the scoped token.
     */
    private String setupAccountantOrg() throws Exception {
        return setupOrg("accountant", true);
    }

    private String setupOwnerOrg() throws Exception {
        return setupOrg("owner", false);
    }

    private String setupOrg(String role, boolean seedMasters) throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String username = "it_" + suffix;

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"email\":\"" + username
                                + "@test.com\",\"password\":\"TestPass1!\",\"role\":\"" + role + "\"}"))
                .andExpect(status().isOk());

        MvcResult signinResult = mockMvc.perform(post("/api/auth/signin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"TestPass1!\"}"))
                .andExpect(status().isOk())
                .andReturn();
        Map<?, ?> signinBody = objectMapper.readValue(
                signinResult.getResponse().getContentAsString(), Map.class);
        String token = (String) signinBody.get("token");

        MvcResult createOrgResult = mockMvc.perform(post("/api/organizations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Test Org " + suffix + "\",\"financialYearStart\":4}"))
                .andExpect(status().isCreated())
                .andReturn();
        Map<?, ?> orgBody = objectMapper.readValue(
                createOrgResult.getResponse().getContentAsString(), Map.class);
        orgId = UUID.fromString(orgBody.get("id").toString());

        // A's CoverageService rejects periods before the books start date
        var org = organizationRepository.findById(orgId).orElseThrow();
        org.setBooksStartDate(LocalDate.of(2025, 4, 1));
        organizationRepository.save(org);

        if (seedMasters) {
            seedValidatedMasters(orgId);
        }

        MvcResult selectResult = mockMvc.perform(post("/api/organizations/" + orgId + "/select")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        Map<?, ?> selectBody = objectMapper.readValue(
                selectResult.getResponse().getContentAsString(), Map.class);
        return (String) selectBody.get("token");
    }

    private UUID startWorkflow(String token) throws Exception {
        MvcResult startResult = mockMvc.perform(post("/api/v1/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"GSTR1\",\"periodKey\":\"2025-04\"}")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        Map<?, ?> body = objectMapper.readValue(startResult.getResponse().getContentAsString(), Map.class);
        return UUID.fromString(body.get("id").toString());
    }
}
