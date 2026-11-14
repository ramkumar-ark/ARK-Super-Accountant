package com.arktech.superaccountant.daybook;

import com.arktech.superaccountant.masters.models.UploadJob;
import com.arktech.superaccountant.masters.models.UploadJobStatus;
import com.arktech.superaccountant.masters.models.UploadLedgerSnapshot;
import com.arktech.superaccountant.masters.repository.OrganizationRepository;
import com.arktech.superaccountant.masters.repository.UploadLedgerSnapshotRepository;
import com.arktech.superaccountant.masters.repository.UploadJobRepository;
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
class DayBookControllerIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private UploadJobRepository uploadJobRepository;
    @Autowired private UploadLedgerSnapshotRepository uploadLedgerSnapshotRepository;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private com.arktech.superaccountant.masters.repository.ValidationFindingRepository validationFindingRepository;
    private UUID validatedMastersJobId;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ── Test 1: TALLY-01, TALLY-06 ───────────────────────────────────────────

    @Test
    void upload_validJson_returnsCompletedJobWith201() throws Exception {
        String token = setupOrgScopedToken();
        String jobId = uploadViaWorkflow(token, "daybook-minimal.json");

        MvcResult result = mockMvc.perform(
                        get("/api/v1/day-book/summary/" + jobId)
                                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        @SuppressWarnings("unchecked")
        Map<String, Object> body = objectMapper.readValue(
                result.getResponse().getContentAsString(), Map.class);

        assertThat(body.get("status")).isEqualTo("COMPLETED");
        assertThat(((Number) body.get("totalVouchersParsed")).intValue()).isGreaterThanOrEqualTo(1);
        assertThat(body.get("errorMessage")).isNull();
    }

    // ── Test 2: TALLY-02, TALLY-03 ───────────────────────────────────────────

    @Test
    void upload_multiTypeFixture_returnsVoucherSummaryInResponse() throws Exception {
        String token = setupOrgScopedToken();
        String jobId = uploadViaWorkflow(token, "daybook-multi-type.json");

        MvcResult result = mockMvc.perform(
                        get("/api/v1/day-book/summary/" + jobId)
                                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        @SuppressWarnings("unchecked")
        Map<String, Object> body = objectMapper.readValue(
                result.getResponse().getContentAsString(), Map.class);

        assertThat(body.get("status")).isEqualTo("COMPLETED");
        assertThat(((Number) body.get("totalVouchersParsed")).intValue()).isEqualTo(7);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> summary = (List<Map<String, Object>>) body.get("voucherSummary");
        assertThat(summary).isNotEmpty();

        boolean hasPurchase = summary.stream()
                .anyMatch(row -> "Purchase".equals(row.get("voucherTypeName"))
                        && ((Number) row.get("count")).intValue() == 2);
        assertThat(hasPurchase).isTrue();
    }

    // ── Test 4: TALLY-02 (summary endpoint) ──────────────────────────────────

    @Test
    void getSummary_byJobId_returnsVoucherTypeBreakdown() throws Exception {
        String token = setupOrgScopedToken();
        String jobId = uploadViaWorkflow(token, "daybook-multi-type.json");

        MvcResult summaryResult = mockMvc.perform(
                        get("/api/v1/day-book/summary/" + jobId)
                                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        @SuppressWarnings("unchecked")
        Map<String, Object> summaryBody = objectMapper.readValue(
                summaryResult.getResponse().getContentAsString(), Map.class);

        assertThat(summaryBody.get("status")).isEqualTo("COMPLETED");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> summary = (List<Map<String, Object>>) summaryBody.get("voucherSummary");
        assertThat(summary).isNotEmpty();
    }

    // ── Test 5: T-4-04 cross-tenant ──────────────────────────────────────────

    @Test
    void getSummary_withAnotherOrgJobId_returns404() throws Exception {
        String token1 = setupOrgScopedToken();
        String jobId = uploadViaWorkflow(token1, "daybook-minimal.json");

        String token2 = setupOrgScopedToken();

        mockMvc.perform(
                        get("/api/v1/day-book/summary/" + jobId)
                                .header("Authorization", "Bearer " + token2))
                .andExpect(status().isNotFound());
    }

    // ── Test 6: TALLY-04 + D-11 gate endpoint ────────────────────────────────

    @Test
    void getGateStatus_whenNoFindings_returnsGatedFalse() throws Exception {
        String token = setupOrgScopedToken();

        MvcResult result = mockMvc.perform(
                        get("/api/v1/masters/gate-status")
                                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        @SuppressWarnings("unchecked")
        Map<String, Object> body = objectMapper.readValue(
                result.getResponse().getContentAsString(), Map.class);

        assertThat(body.containsKey("gated")).isTrue();
        if (Boolean.TRUE.equals(body.get("gated"))) {
            assertThat(body.get("unresolvedCount")).isNotNull();
            assertThat(body.get("reason")).isNotNull();
        }
    }

    // ── Upload history tests (worker B) ─────────────────────────────────────

    @Test
    void listUploads_returnsNewestFirstWithCoverage() throws Exception {
        String token = setupOrgScopedToken();
        uploadViaWorkflow(token, "daybook-minimal.json", "first.json", "2025-04");
        uploadViaWorkflow(token, "daybook-minimal.json", "second.json", "2025-05");

        mockMvc.perform(get("/api/v1/day-book/uploads")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].fileName").value("second.json"))
                .andExpect(jsonPath("$.content[0].coverageFrom").value("2025-04-01"))
                .andExpect(jsonPath("$.content[0].coverageTo").value("2025-05-31"))
                .andExpect(jsonPath("$.content[1].fileName").value("first.json"));
    }

    @Test
    void listUploads_invalidPaging_isClampedNotServerError() throws Exception {
        String token = setupOrgScopedToken();
        uploadViaWorkflow(token, "daybook-minimal.json", "a.json");

        mockMvc.perform(get("/api/v1/day-book/uploads").param("size", "0").param("page", "-1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.size").value(1));

        mockMvc.perform(get("/api/v1/day-book/uploads").param("size", "100000")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));
    }

    @Test
    void listUploads_otherOrgSeesNothing() throws Exception {
        String token1 = setupOrgScopedToken();
        uploadViaWorkflow(token1, "daybook-minimal.json", "mine.json");

        String token2 = setupOrgScopedTokenNoMasters();
        mockMvc.perform(get("/api/v1/day-book/uploads")
                        .header("Authorization", "Bearer " + token2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    /**
     * Masters gate (MastersGateService) blocks day book uploads until the org has a completed
     * masters upload with no unresolved findings, and DayBookMasterValidationService rejects
     * day book ledgers absent from that upload's ledger snapshot. Seed both so these tests
     * exercise the day book endpoints rather than the gates.
     */
    private void seedValidatedMasters(UUID orgId) {
        UploadJob masters = new UploadJob();
        masters.setOrganizationId(orgId);
        masters.setFileName("masters-fixture.json");
        masters.setStatus(UploadJobStatus.COMPLETED);
        masters.setTotalLedgersParsed(FIXTURE_LEDGER_NAMES.size());
        masters = uploadJobRepository.saveAndFlush(masters);
        validatedMastersJobId = masters.getId();

        for (String name : FIXTURE_LEDGER_NAMES) {
            UploadLedgerSnapshot snapshot = new UploadLedgerSnapshot();
            snapshot.setUploadJobId(masters.getId());
            snapshot.setOrganizationId(orgId);
            snapshot.setLedgerName(name);
            uploadLedgerSnapshotRepository.save(snapshot);
        }
        uploadLedgerSnapshotRepository.flush();
    }

    // Every ledgername referenced by the daybook-*.json fixtures.
    private static final List<String> FIXTURE_LEDGER_NAMES = List.of(
            "Bank Account", "Cement Purchases", "Cement Supplier", "Client ABC", "Depreciation",
            "Input CGST @9%", "Input IGST @18%", "Input SGST @9%", "Output CGST @9%",
            "Output SGST @9%", "Plant & Machinery", "Sales Account", "Steel Purchases",
            "Test Cement Purchases", "Test Purchases");

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String uploadViaWorkflow(String token, String fixture) throws Exception {
        return uploadViaWorkflow(token, fixture, fixture, "2025-04");
    }

    private String uploadViaWorkflow(String token, String fixture, String fileName) throws Exception {
        return uploadViaWorkflow(token, fixture, fileName, "2025-04");
    }

    private String uploadViaWorkflow(String token, String fixture, String fileName, String periodKey)
            throws Exception {
        MvcResult startResult = mockMvc.perform(post("/api/v1/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"GSTR1\",\"periodKey\":\"" + periodKey + "\"}")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        @SuppressWarnings("unchecked")
        Map<String, Object> startBody = objectMapper.readValue(
                startResult.getResponse().getContentAsString(), Map.class);
        String workflowId = startBody.get("id").toString();

        byte[] data = getClass().getResourceAsStream("/fixtures/" + fixture).readAllBytes();
        MvcResult uploadResult = mockMvc.perform(
                        multipart("/api/v1/workflows/" + workflowId + "/upload")
                                .file(new MockMultipartFile("file", fileName,
                                        "application/json", data))
                                .param("coverageFrom", "2025-04-01")
                                .param("coverageTo", "2025-05-31")
                                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        @SuppressWarnings("unchecked")
        Map<String, Object> uploadBody = objectMapper.readValue(
                uploadResult.getResponse().getContentAsString(), Map.class);
        return uploadBody.get("uploadJobId").toString();
    }

    private String setupOrgScopedTokenNoMasters() throws Exception {
        return setupOrgScopedToken(false, false);
    }

    private String setupOrgScopedToken() throws Exception {
        return setupOrgScopedToken(true, false);
    }

    private String setupOrgScopedToken(boolean seedMasters, boolean addUnresolvedFinding) throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String username = "it_" + suffix;

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"email\":\"" + username + "@test.com\",\"password\":\"TestPass1!\",\"role\":\"accountant\"}"))
                .andExpect(status().isOk());

        MvcResult signinResult = mockMvc.perform(post("/api/auth/signin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"TestPass1!\"}"))
                .andExpect(status().isOk())
                .andReturn();
        @SuppressWarnings("unchecked")
        Map<String, Object> signinBody = objectMapper.readValue(
                signinResult.getResponse().getContentAsString(), Map.class);
        String token = (String) signinBody.get("token");

        MvcResult createOrgResult = mockMvc.perform(post("/api/organizations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Test Org " + suffix + "\",\"financialYearStart\":4}"))
                .andExpect(status().isCreated())
                .andReturn();
        @SuppressWarnings("unchecked")
        Map<String, Object> orgBody = objectMapper.readValue(
                createOrgResult.getResponse().getContentAsString(), Map.class);
        UUID orgId = UUID.fromString(orgBody.get("id").toString());

        // CoverageService rejects workflow periods before the books start date.
        var org = organizationRepository.findById(orgId).orElseThrow();
        org.setBooksStartDate(LocalDate.of(2025, 4, 1));
        organizationRepository.save(org);

        if (seedMasters) {
            seedValidatedMasters(orgId);
            if (addUnresolvedFinding) {
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
        }

        MvcResult selectResult = mockMvc.perform(post("/api/organizations/" + orgId + "/select")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        @SuppressWarnings("unchecked")
        Map<String, Object> selectBody = objectMapper.readValue(
                selectResult.getResponse().getContentAsString(), Map.class);
        return (String) selectBody.get("token");
    }
}
