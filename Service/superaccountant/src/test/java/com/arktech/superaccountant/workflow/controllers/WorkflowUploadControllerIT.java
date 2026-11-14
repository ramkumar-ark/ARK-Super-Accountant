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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
    @Autowired private com.arktech.superaccountant.daybook.repository.ParsedVoucherRepository parsedVoucherRepository;
    @Autowired private com.arktech.superaccountant.masters.repository.ValidationFindingRepository validationFindingRepository;

    private static final List<String> FIXTURE_LEDGER_NAMES = List.of(
            "Bank Account", "Cement Purchases", "Cement Supplier", "Client ABC", "Depreciation",
            "Input CGST @9%", "Input IGST @18%", "Input SGST @9%", "Output CGST @9%",
            "Output SGST @9%", "Plant & Machinery", "Sales Account", "Steel Purchases",
            "Test Cement Purchases", "Test Purchases");

    private UUID orgId;
    private UUID validatedMastersJobId;

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
    void upload_malformedJson_returns400WithFailedJob() throws Exception {
        String token = setupAccountantOrg();
        UUID workflowId = startWorkflow(token);
        byte[] fixture = getClass().getResourceAsStream("/fixtures/daybook-malformed.json").readAllBytes();

        MvcResult result = mockMvc.perform(
                        multipart("/api/v1/workflows/" + workflowId + "/upload")
                                .file(new MockMultipartFile("file", "daybook-malformed.json",
                                        "application/json", fixture))
                                .param("coverageFrom", "2025-04-01")
                                .param("coverageTo", "2025-05-31")
                                .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andReturn();

        Map<?, ?> body = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        assertThat(body.get("error")).isNotNull();
        assertThat(body.get("error").toString()).isNotBlank();
        assertThat(body.get("uploadId")).isNotNull();
    }

    @Test
    void upload_nonJsonFile_returns400() throws Exception {
        String token = setupAccountantOrg();
        UUID workflowId = startWorkflow(token);

        mockMvc.perform(
                        multipart("/api/v1/workflows/" + workflowId + "/upload")
                                .file(new MockMultipartFile("file", "daybook.xml",
                                        "application/xml", "<xml/>".getBytes()))
                                .param("coverageFrom", "2025-04-01")
                                .param("coverageTo", "2025-05-31")
                                .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void upload_withoutAuth_returns401() throws Exception {
        byte[] fixture = getClass().getResourceAsStream("/fixtures/daybook-minimal.json").readAllBytes();

        mockMvc.perform(
                        multipart("/api/v1/workflows/" + UUID.randomUUID() + "/upload")
                                .file(new MockMultipartFile("file", "daybook-minimal.json",
                                        "application/json", fixture))
                                .param("coverageFrom", "2025-04-01")
                                .param("coverageTo", "2025-05-31"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void upload_utf16leFile_parsesCorrectly() throws Exception {
        String token = setupAccountantOrg();
        UUID workflowId = startWorkflow(token);
        byte[] fixture = getClass().getResourceAsStream("/fixtures/daybook-utf16le.json").readAllBytes();

        mockMvc.perform(
                        multipart("/api/v1/workflows/" + workflowId + "/upload")
                                .file(new MockMultipartFile("file", "daybook-utf16le.json",
                                        "application/json", fixture))
                                .param("coverageFrom", "2025-04-01")
                                .param("coverageTo", "2025-05-31")
                                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("DATA_UPLOADED"))
                .andExpect(jsonPath("$.uploadJobId").isNotEmpty());
    }

    @Test
    void upload_withoutCoverage_returns400() throws Exception {
        String token = setupAccountantOrg();
        UUID workflowId = startWorkflow(token);
        byte[] fixture = getClass().getResourceAsStream("/fixtures/daybook-minimal.json").readAllBytes();

        mockMvc.perform(
                        multipart("/api/v1/workflows/" + workflowId + "/upload")
                                .file(new MockMultipartFile("file", "daybook-minimal.json",
                                        "application/json", fixture))
                                .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void upload_voucherOutsideCoverage_returns422AndPersistsNoVouchers() throws Exception {
        String token = setupAccountantOrg();
        UUID workflowId = startWorkflow(token);
        byte[] fixture = getClass().getResourceAsStream("/fixtures/daybook-multi-type.json").readAllBytes();

        MvcResult result = mockMvc.perform(
                        multipart("/api/v1/workflows/" + workflowId + "/upload")
                                .file(new MockMultipartFile("file", "daybook-multi-type.json",
                                        "application/json", fixture))
                                .param("coverageFrom", "2025-04-01")
                                .param("coverageTo", "2025-04-30")
                                .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.outOfRangeCount").value(org.hamcrest.Matchers.greaterThan(0)))
                .andReturn();

        Map<?, ?> body = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        UUID jobId = UUID.fromString(body.get("uploadId").toString());

        assertThat(dayBookUploadJobRepository.findById(jobId))
                .hasValueSatisfying(job -> {
                    assertThat(job.getStatus()).isEqualTo(UploadJobStatus.FAILED);
                    assertThat(job.getErrorMessage()).isNotBlank();
                });
        assertThat(parsedVoucherRepository.findByUploadJobId(jobId)).isEmpty();
    }

    @Test
    void upload_persistsCoverageAndVoucherRange() throws Exception {
        String token = setupAccountantOrg();
        String jobId = uploadViaWorkflow(token, "daybook-multi-type.json");

        mockMvc.perform(get("/api/v1/day-book/uploads")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(jobId))
                .andExpect(jsonPath("$.content[0].coverageFrom").value("2025-04-01"))
                .andExpect(jsonPath("$.content[0].coverageTo").value("2025-05-31"))
                .andExpect(jsonPath("$.content[0].minVoucherDate").value("2025-04-01"))
                .andExpect(jsonPath("$.content[0].maxVoucherDate").value("2025-05-01"));
    }

    @Test
    void upload_withUnresolvedMastersFindings_returns200() throws Exception {
        String token = setupOrgWithUnresolvedMastersFinding();
        UUID workflowId = startWorkflow(token);
        byte[] fixture = getClass().getResourceAsStream("/fixtures/daybook-minimal.json").readAllBytes();

        mockMvc.perform(
                        multipart("/api/v1/workflows/" + workflowId + "/upload")
                                .file(new MockMultipartFile("file", "daybook-minimal.json",
                                        "application/json", fixture))
                                .param("coverageFrom", "2025-04-01")
                                .param("coverageTo", "2025-05-31")
                                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void upload_withoutMastersUpload_returns422() throws Exception {
        String token = setupOrgNoMasters();
        UUID workflowId = startWorkflow(token);
        byte[] fixture = getClass().getResourceAsStream("/fixtures/daybook-minimal.json").readAllBytes();

        mockMvc.perform(
                        multipart("/api/v1/workflows/" + workflowId + "/upload")
                                .file(new MockMultipartFile("file", "daybook-minimal.json",
                                        "application/json", fixture))
                                .param("coverageFrom", "2025-04-01")
                                .param("coverageTo", "2025-05-31")
                                .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value(
                        "Upload and validate masters before uploading the day book."));
    }

    @Test
    void legacyUploadRoute_isGone() throws Exception {
        String token = setupAccountantOrg();
        byte[] fixture = getClass().getResourceAsStream("/fixtures/daybook-minimal.json").readAllBytes();

        MvcResult result = mockMvc.perform(
                        multipart("/api/v1/day-book/upload")
                                .file(new MockMultipartFile("file", "daybook-minimal.json",
                                        "application/json", fixture))
                                .param("coverageFrom", "2025-04-01")
                                .param("coverageTo", "2025-05-31")
                                .header("Authorization", "Bearer " + token))
                .andReturn();

        int status = result.getResponse().getStatus();
        assertThat(status).isBetween(400, 499);
        assertThat(status).isNotIn(401, 403);
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

    private String uploadViaWorkflow(String token, String fixture) throws Exception {
        return uploadViaWorkflow(token, fixture, fixture);
    }

    private String uploadViaWorkflow(String token, String fixture, String fileName) throws Exception {
        return uploadViaWorkflow(token, fixture, fileName, "2025-04");
    }

    private String uploadViaWorkflow(String token, String fixture, String fileName, String periodKey)
            throws Exception {
        UUID workflowId = startWorkflow(token, periodKey);
        byte[] data = getClass().getResourceAsStream("/fixtures/" + fixture).readAllBytes();
        MvcResult result = mockMvc.perform(
                        multipart("/api/v1/workflows/" + workflowId + "/upload")
                                .file(new MockMultipartFile("file", fileName,
                                        "application/json", data))
                                .param("coverageFrom", "2025-04-01")
                                .param("coverageTo", "2025-05-31")
                                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        Map<?, ?> body = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        return body.get("uploadJobId").toString();
    }

    private void seedValidatedMasters(UUID org) {
        UploadJob masters = new UploadJob();
        masters.setOrganizationId(org);
        masters.setFileName("masters-fixture.json");
        masters.setStatus(UploadJobStatus.COMPLETED);
        masters.setTotalLedgersParsed(FIXTURE_LEDGER_NAMES.size());
        masters = uploadJobRepository.saveAndFlush(masters);
        validatedMastersJobId = masters.getId();
        for (String name : FIXTURE_LEDGER_NAMES) {
            UploadLedgerSnapshot snapshot = new UploadLedgerSnapshot();
            snapshot.setUploadJobId(masters.getId());
            snapshot.setOrganizationId(org);
            snapshot.setLedgerName(name);
            uploadLedgerSnapshotRepository.save(snapshot);
        }
        uploadLedgerSnapshotRepository.flush();
    }

    private void addUnresolvedMastersFinding() {
        com.arktech.superaccountant.masters.models.ValidationFinding finding =
                new com.arktech.superaccountant.masters.models.ValidationFinding();
        finding.setUploadJobId(validatedMastersJobId);
        finding.setRuleCode("MISMATCH_TEST");
        finding.setLedgerName("Test Purchases");
        finding.setSeverity(com.arktech.superaccountant.masters.models.FindingSeverity.ERROR);
        finding.setResolveStatus(com.arktech.superaccountant.masters.models.ResolveStatus.OPEN);
        finding.setMessage("unresolved test finding");
        validationFindingRepository.save(finding);
    }

    /**
     * Signup (accountant), create org, set booksStartDate so 2025-04 is coverable,
     * seed validated masters, select the org and return the scoped token.
     */
    private String setupAccountantOrg() throws Exception {
        return setupOrg("accountant", true);
    }

    private String setupOrgNoMasters() throws Exception {
        return setupOrg("accountant", false);
    }

    private String setupOrgWithUnresolvedMastersFinding() throws Exception {
        String token = setupOrg("accountant", true);
        addUnresolvedMastersFinding();
        return token;
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
        return startWorkflow(token, "2025-04");
    }

    private UUID startWorkflow(String token, String periodKey) throws Exception {
        MvcResult startResult = mockMvc.perform(post("/api/v1/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"GSTR1\",\"periodKey\":\"" + periodKey + "\"}")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        Map<?, ?> body = objectMapper.readValue(startResult.getResponse().getContentAsString(), Map.class);
        return UUID.fromString(body.get("id").toString());
    }
}
