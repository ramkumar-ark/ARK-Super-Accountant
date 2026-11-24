package com.arktech.superaccountant.tds.workflow;

import com.arktech.superaccountant.daybook.models.DayBookUploadJob;
import com.arktech.superaccountant.daybook.models.ParsedVoucher;
import com.arktech.superaccountant.daybook.models.ParsedVoucherEntry;
import com.arktech.superaccountant.daybook.repository.DayBookUploadJobRepository;
import com.arktech.superaccountant.daybook.repository.ParsedVoucherRepository;
import com.arktech.superaccountant.login.models.ERole;
import com.arktech.superaccountant.login.models.Role;
import com.arktech.superaccountant.login.models.User;
import com.arktech.superaccountant.login.repository.RoleRepository;
import com.arktech.superaccountant.login.repository.UserRepository;
import com.arktech.superaccountant.login.security.services.UserDetailsImpl;
import com.arktech.superaccountant.masters.models.LedgerCategory;
import com.arktech.superaccountant.masters.models.Organization;
import com.arktech.superaccountant.masters.models.ResolveStatus;
import com.arktech.superaccountant.masters.models.UploadJob;
import com.arktech.superaccountant.masters.models.UploadJobStatus;
import com.arktech.superaccountant.masters.models.UploadLedgerSnapshot;
import com.arktech.superaccountant.masters.repository.OrganizationRepository;
import com.arktech.superaccountant.masters.repository.UploadJobRepository;
import com.arktech.superaccountant.masters.repository.UploadLedgerSnapshotRepository;
import com.arktech.superaccountant.tds.mapping.TdsLedgerMappingRepository;
import com.arktech.superaccountant.tds.mapping.TdsMappingTestData;
import com.arktech.superaccountant.tds.models.TdsFinding;
import com.arktech.superaccountant.tds.models.TdsFinding;
import com.arktech.superaccountant.tds.models.TdsFindingType;
import com.arktech.superaccountant.tds.models.TdsPaymentDetail;
import com.arktech.superaccountant.tds.models.TdsPanOverride;
import com.arktech.superaccountant.tds.repository.TdsFindingRepository;
import com.arktech.superaccountant.tds.repository.TdsPanOverrideRepository;
import com.arktech.superaccountant.tds.repository.TdsPaymentDetailRepository;
import com.arktech.superaccountant.tds.repository.TdsSectionConfigRepository;
import com.arktech.superaccountant.workflow.analysis.WorkflowAnalysisService;
import com.arktech.superaccountant.workflow.models.Workflow;
import com.arktech.superaccountant.workflow.models.WorkflowStage;
import com.arktech.superaccountant.workflow.models.WorkflowType;
import com.arktech.superaccountant.workflow.repository.WorkflowRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end tests (real Postgres via Testcontainers) of TdsPaymentAnalyzer inside
 * WorkflowAnalysisService. NOT @Transactional: each test uses its own organization.
 * Needs Docker.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = "JWT_SECRET=test-jwt-secret-must-be-at-least-32-characters-long")
class TdsFindingsReviewIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @org.springframework.test.context.DynamicPropertySource
    static void configureProperties(org.springframework.test.context.DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired WorkflowRepository workflowRepository;
    @Autowired WorkflowAnalysisService analysisService;
    @Autowired OrganizationRepository organizationRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired UserRepository userRepository;
    @Autowired UploadJobRepository uploadJobRepository;
    @Autowired UploadLedgerSnapshotRepository snapshotRepository;
    @Autowired DayBookUploadJobRepository dayBookJobRepository;
    @Autowired ParsedVoucherRepository parsedVoucherRepository;
    @Autowired TdsFindingRepository findingRepository;
    @Autowired TdsPaymentDetailRepository paymentDetailRepository;
    @Autowired TdsPanOverrideRepository panOverrideRepository;
    @Autowired TdsLedgerMappingRepository mappingRepository;
    @Autowired TdsSectionConfigRepository sectionConfigRepository;
    @Autowired MockMvc mockMvc;

    UUID orgId;
    UserDetailsImpl principal;
    UUID mastersJobId;
    UUID dayBookJobId;

    @BeforeEach
    void seed() {
        Organization org = new Organization();
        org.setName("TDS Findings Review IT " + UUID.randomUUID());
        org.setBooksStartDate(LocalDate.of(2025, 4, 1));
        orgId = organizationRepository.save(org).getId();

        Role role = roleRepository.findByName(ERole.ROLE_ACCOUNTANT).orElseThrow();
        User user = new User();
        user.setUsername("acct-" + UUID.randomUUID());
        user.setPassword("x");
        user.setEmail("acct-" + UUID.randomUUID() + "@t.com");
        user.setRole(role);
        principal = UserDetailsImpl.build(userRepository.save(user), orgId);

        UploadJob mastersJob = new UploadJob();
        mastersJob.setOrganizationId(orgId);
        mastersJob.setFileName("masters.xml");
        mastersJob.setStatus(UploadJobStatus.COMPLETED);
        mastersJob.setUploadedBy("it");
        mastersJobId = uploadJobRepository.save(mastersJob).getId();

        snapshot(mastersJobId, "Subcontractor Charges", LedgerCategory.EXPENSE, true, "194C", null);
        snapshot(mastersJobId, "ABC Contractors", LedgerCategory.CREDITOR, null, null, null);
        snapshot(mastersJobId, "TDS Payable 194C", LedgerCategory.TDS, true, "194C", null);
        TdsMappingTestData.mapAll(orgId, mastersJobId, snapshotRepository, mappingRepository,
                sectionConfigRepository);

        DayBookUploadJob dayBookJob = new DayBookUploadJob();
        dayBookJob.setOrganizationId(orgId);
        dayBookJob.setFileName("daybook.xml");
        dayBookJob.setStatus(UploadJobStatus.COMPLETED);
        dayBookJob.setUploadedBy("it");
        dayBookJobId = dayBookJobRepository.save(dayBookJob).getId();

        ParsedVoucher v = new ParsedVoucher();
        v.setUploadJobId(dayBookJobId);
        v.setOrganizationId(orgId);
        v.setVoucherGuid("dv-1");
        v.setVoucherNumber("V1");
        v.setVoucherTypeName("Journal");
        v.setVoucherDate(LocalDate.of(2025, 4, 10));
        v.setPartyLedgerName("ABC Contractors");
        v.setContentHash("a".repeat(64));
        v.setCancelled(false);
        List<ParsedVoucherEntry> entries = new ArrayList<>();
        entry(v, entries, "Subcontractor Charges", new BigDecimal("100000"), true, false);
        entry(v, entries, "ABC Contractors", new BigDecimal("-99000"), false, true);
        entry(v, entries, "TDS Payable 194C", new BigDecimal("-1000"), false, false);
        v.setEntries(entries);
        parsedVoucherRepository.save(v);
    }

    private void entry(ParsedVoucher v, List<ParsedVoucherEntry> entries, String ledger,
                       BigDecimal amount, boolean debit, boolean party) {
        ParsedVoucherEntry e = new ParsedVoucherEntry();
        e.setParsedVoucher(v);
        e.setOrganizationId(orgId);
        e.setLedgerName(ledger);
        e.setAmount(amount);
        e.setDebit(debit);
        e.setPartyLedger(party);
        entries.add(e);
    }

    private void snapshot(UUID jobId, String ledger, LedgerCategory cat, Boolean tdsApplicable,
                          String tdsSection, String pan) {
        UploadLedgerSnapshot s = new UploadLedgerSnapshot();
        s.setUploadJobId(jobId);
        s.setOrganizationId(orgId);
        s.setLedgerName(ledger);
        s.setCategory(cat);
        s.setTdsApplicable(tdsApplicable);
        s.setTdsSection(tdsSection);
        s.setPan(pan);
        snapshotRepository.save(s);
    }

    private Workflow seedWorkflow() {
        Workflow w = new Workflow();
        w.setOrganizationId(orgId);
        w.setType(WorkflowType.TDS_PAYMENT);
        w.setPeriodStart(LocalDate.of(2025, 4, 1));
        w.setPeriodEnd(LocalDate.of(2025, 4, 30));
        w.setPeriodKey("2025-04");
        w.setFinancialYear(2025);
        w.setStage(WorkflowStage.DATA_UPLOADED);
        w.setUploadJobId(dayBookJobId);
        w.setCreatedBy("it");
        return workflowRepository.save(w);
    }

    @Test
    void findingsEndpoint_listsTdsSourceAndBlocks() throws Exception {
        Workflow w = seedWorkflow();
        analysisService.run(orgId, w.getId(), "it");

        org.springframework.test.web.servlet.MvcResult result = mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .get("/api/v1/workflows/" + w.getId() + "/findings")
                                .with(org.springframework.security.test.web.servlet.request
                                        .SecurityMockMvcRequestPostProcessors.user(principal)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertTrue(body.contains("\"source\":\"TDS\""));
        assertTrue(body.contains("\"findingType\":\"PAN_MISSING\""));
        assertTrue(body.contains("\"blocking\":true"));
    }

    @Test
    void accountantPanProposal_createsOverride_andReanalysisClearsFinding() throws Exception {
        Workflow w = seedWorkflow();
        analysisService.run(orgId, w.getId(), "it");

        String body = mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .get("/api/v1/workflows/" + w.getId() + "/findings")
                                .with(org.springframework.security.test.web.servlet.request
                                        .SecurityMockMvcRequestPostProcessors.user(principal)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .status().isOk())
                .andReturn().getResponse().getContentAsString();
        java.util.List<java.util.Map<String, Object>> panFindings = com.jayway.jsonpath.JsonPath.read(
                body, "$.findings[?(@.findingType == 'PAN_MISSING')]");
        UUID panFindingId = UUID.fromString((String) panFindings.get(0).get("id"));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/workflows/" + w.getId() + "/findings/" + panFindingId + "/action")
                        .with(org.springframework.security.test.web.servlet.request
                                .SecurityMockMvcRequestPostProcessors.user(principal))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"ACKNOWLEDGE\",\"value\":\"ABCPE1234F\","
                                + "\"note\":\"from invoice\"}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .status().isOk());

        List<TdsPanOverride> overrides = panOverrideRepository
                .findByOrganizationIdOrderByPartyLedgerNameAsc(orgId);
        assertEquals(1, overrides.size());
        assertEquals("ABCPE1234F", overrides.get(0).getPan());

        Workflow reset = workflowRepository.findById(w.getId()).orElseThrow();
        reset.setStage(WorkflowStage.DATA_UPLOADED);
        workflowRepository.save(reset);
        analysisService.run(orgId, w.getId(), "it");

        List<TdsFinding> current = findingRepository
                .findByWorkflowIdAndSupersededAtIsNullOrderByVoucherDateAsc(w.getId());
        assertTrue(current.stream()
                .noneMatch(f -> f.getFindingType() == TdsFindingType.PAN_MISSING));
    }

    @Test
    void panOverrides_flagMastersDifference() throws Exception {
        Workflow w = seedWorkflow();
        analysisService.run(orgId, w.getId(), "it");

        String body = mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .get("/api/v1/workflows/" + w.getId() + "/findings")
                                .with(org.springframework.security.test.web.servlet.request
                                        .SecurityMockMvcRequestPostProcessors.user(principal)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .status().isOk())
                .andReturn().getResponse().getContentAsString();
        java.util.List<java.util.Map<String, Object>> panFindings = com.jayway.jsonpath.JsonPath.read(
                body, "$.findings[?(@.findingType == 'PAN_MISSING')]");
        UUID panFindingId = UUID.fromString((String) panFindings.get(0).get("id"));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/workflows/" + w.getId() + "/findings/" + panFindingId + "/action")
                        .with(org.springframework.security.test.web.servlet.request
                                .SecurityMockMvcRequestPostProcessors.user(principal))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"ACKNOWLEDGE\",\"value\":\"ABCPE1234F\","
                                + "\"note\":\"from invoice\"}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .status().isOk());

        UploadJob laterJob = new UploadJob();
        laterJob.setOrganizationId(orgId);
        laterJob.setFileName("masters2.xml");
        laterJob.setStatus(UploadJobStatus.COMPLETED);
        laterJob.setUploadedBy("it");
        UUID laterJobId = uploadJobRepository.save(laterJob).getId();
        snapshot(laterJobId, "ABC Contractors", LedgerCategory.CREDITOR, null, null, "ZZZPE9999Z");

        String response = mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .get("/api/v1/tds/pan-overrides")
                                .with(org.springframework.security.test.web.servlet.request
                                        .SecurityMockMvcRequestPostProcessors.user(principal)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertTrue(response.contains("\"mastersDiffers\":true"));
        assertTrue(response.contains("ZZZPE9999Z"));
    }
}
