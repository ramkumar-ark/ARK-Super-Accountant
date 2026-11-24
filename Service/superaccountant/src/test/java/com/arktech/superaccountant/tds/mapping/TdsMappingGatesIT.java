package com.arktech.superaccountant.tds.mapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.arktech.superaccountant.daybook.models.DayBookUploadJob;
import com.arktech.superaccountant.daybook.repository.DayBookUploadJobRepository;
import com.arktech.superaccountant.login.models.ERole;
import com.arktech.superaccountant.login.models.Role;
import com.arktech.superaccountant.login.models.User;
import com.arktech.superaccountant.login.repository.RoleRepository;
import com.arktech.superaccountant.login.repository.UserRepository;
import com.arktech.superaccountant.login.security.services.UserDetailsImpl;
import com.arktech.superaccountant.masters.models.Organization;
import com.arktech.superaccountant.masters.models.UploadJob;
import com.arktech.superaccountant.masters.models.UploadJobStatus;
import com.arktech.superaccountant.masters.models.UploadLedgerSnapshot;
import com.arktech.superaccountant.masters.repository.OrganizationRepository;
import com.arktech.superaccountant.masters.repository.UploadJobRepository;
import com.arktech.superaccountant.masters.repository.UploadLedgerSnapshotRepository;
import com.arktech.superaccountant.tds.models.TdsSectionConfig;
import com.arktech.superaccountant.tds.repository.TdsSectionConfigRepository;
import com.arktech.superaccountant.workflow.analysis.AnalysisRunStatus;
import com.arktech.superaccountant.workflow.analysis.WorkflowAnalysisRun;
import com.arktech.superaccountant.workflow.analysis.WorkflowAnalysisRunRepository;
import com.arktech.superaccountant.workflow.models.Workflow;
import com.arktech.superaccountant.workflow.models.WorkflowStage;
import com.arktech.superaccountant.workflow.models.WorkflowType;
import com.arktech.superaccountant.workflow.repository.WorkflowRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = "JWT_SECRET=test-jwt-secret-must-be-at-least-32-characters-long")
class TdsMappingGatesIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    MockMvc mockMvc;
    @Autowired
    OrganizationRepository organizationRepository;
    @Autowired
    UploadJobRepository uploadJobRepository;
    @Autowired
    UploadLedgerSnapshotRepository snapshotRepository;
    @Autowired
    TdsLedgerMappingRepository mappingRepository;
    @Autowired
    TdsSectionConfigRepository sectionConfigRepository;
    @Autowired
    DayBookUploadJobRepository dayBookUploadJobRepository;
    @Autowired
    WorkflowRepository workflowRepository;
    @Autowired
    WorkflowAnalysisRunRepository runRepository;
    @Autowired
    RoleRepository roleRepository;
    @Autowired
    UserRepository userRepository;

    UUID orgId;
    UserDetailsImpl accountant;

    @BeforeEach
    void seed() {
        mappingRepository.deleteAll();

        Organization org = new Organization("TDS Mapping Gates IT " + UUID.randomUUID());
        org.setPan("ABCCS1234F");
        org.setBooksStartDate(LocalDate.of(2025, 4, 1));
        orgId = organizationRepository.save(org).getId();

        accountant = principal(ERole.ROLE_ACCOUNTANT);
    }

    private UserDetailsImpl principal(ERole roleName) {
        Role role = roleRepository.findByName(roleName).orElseThrow();
        User u = new User();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        u.setUsername(roleName.name().replace("ROLE_", "").toLowerCase() + "-" + suffix);
        u.setPassword("x");
        u.setEmail(suffix + "@t.com");
        u.setRole(role);
        return UserDetailsImpl.build(userRepository.save(u), orgId);
    }

    private Set<String> sectionCodes() {
        return sectionConfigRepository.findAll().stream()
                .map(TdsSectionConfig::getSectionCode)
                .collect(Collectors.toSet());
    }

    private UploadJob mastersJob(String fileName, Instant createdAt) {
        UploadJob job = new UploadJob();
        job.setOrganizationId(orgId);
        job.setFileName(fileName);
        job.setStatus(UploadJobStatus.COMPLETED);
        job.setCreatedAt(createdAt);
        job.setUploadedBy("it");
        return uploadJobRepository.save(job);
    }

    private void snapshot(UploadJob job, String name, String parentGroup, String groupChain,
            Boolean tdsApplicable) {
        UploadLedgerSnapshot s = new UploadLedgerSnapshot();
        s.setUploadJobId(job.getId());
        s.setOrganizationId(orgId);
        s.setLedgerName(name);
        s.setParentGroup(parentGroup);
        s.setGroupChain(groupChain);
        s.setTdsApplicable(tdsApplicable);
        snapshotRepository.save(s);
    }

    private Workflow seedTdsPaymentWorkflow() {
        DayBookUploadJob dayBookJob = new DayBookUploadJob();
        dayBookJob.setOrganizationId(orgId);
        dayBookJob.setFileName("daybook.json");
        dayBookJob.setStatus(UploadJobStatus.COMPLETED);
        dayBookJob.setCoverageFrom(LocalDate.of(2025, 4, 1));
        dayBookJob.setCoverageTo(LocalDate.of(2025, 4, 30));
        dayBookJob.setTotalVouchersParsed(0);
        dayBookJob.setUploadedBy("it");
        dayBookJob = dayBookUploadJobRepository.save(dayBookJob);

        Workflow w = new Workflow();
        w.setOrganizationId(orgId);
        w.setType(WorkflowType.TDS_PAYMENT);
        w.setPeriodStart(LocalDate.of(2025, 4, 1));
        w.setPeriodEnd(LocalDate.of(2025, 4, 30));
        w.setPeriodKey("2025-04");
        w.setFinancialYear(2025);
        w.setStage(WorkflowStage.DATA_UPLOADED);
        w.setActive(true);
        w.setUploadJobId(dayBookJob.getId());
        w.setCreatedBy("it");
        return workflowRepository.saveAndFlush(w);
    }

    private void putMapping(String itemsJson) throws Exception {
        mockMvc.perform(put("/api/v1/tds/ledger-mapping").with(user(accountant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(itemsJson))
                .andExpect(status().isOk());
    }

    @Test
    void analysisBlockedWhileUnmappedThenMappingReRunsAndAdvanceSucceeds() throws Exception {
        UploadJob job1 = mastersJob("masters-1.json", Instant.now().minusSeconds(7200));
        TdsMappingTestData.seedNarayananSnapshots(orgId, job1.getId(), snapshotRepository);
        Workflow w = seedTdsPaymentWorkflow();

        mockMvc.perform(post("/api/v1/workflows/{id}/analyze", w.getId()).with(user(accountant)))
                .andExpect(status().isOk());

        Workflow reloaded = workflowRepository.findById(w.getId()).orElseThrow();
        assertThat(reloaded.getStage()).isEqualTo(WorkflowStage.DATA_UPLOADED);

        List<WorkflowAnalysisRun> runs = runRepository.findByWorkflowIdOrderByStartedAtAsc(w.getId());
        assertThat(runs).isNotEmpty();
        WorkflowAnalysisRun latest = runs.get(runs.size() - 1);
        assertThat(latest.getAnalyzerCode()).isEqualTo("TDS_PAYMENT");
        assertThat(latest.getStatus()).isEqualTo(AnalysisRunStatus.FAILED);
        assertThat(latest.getErrorMessage())
                .isEqualTo("2 TDS ledgers not mapped: Rent, TDS Payable 194C");

        mockMvc.perform(get("/api/v1/workflows/{id}/findings", w.getId()).with(user(accountant)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.findings[?(@.source == 'TDS_MAPPING')].blocking")
                        .value(Matchers.everyItem(Matchers.is(true))))
                .andExpect(jsonPath(
                                "$.findings[?(@.source == 'TDS_MAPPING' && @.ledgerName == 'Rent')].message")
                        .value(Matchers.hasItem(
                                "TDS ledger 'Rent' is not mapped. Map it under Settings → Compliance → TDS ledgers.")))
                .andExpect(jsonPath("$.blockingCount").value(2));

        mockMvc.perform(post("/api/v1/workflows/{id}/advance", w.getId()).with(user(accountant)))
                .andExpect(status().isConflict());

        putMapping(TdsMappingTestData.mappingItemsJson(sectionCodes()));

        reloaded = workflowRepository.findById(w.getId()).orElseThrow();
        assertThat(reloaded.getStage()).isEqualTo(WorkflowStage.FINDINGS_REVIEW);

        mockMvc.perform(post("/api/v1/workflows/{id}/advance", w.getId()).with(user(accountant)))
                .andExpect(status().isOk());
        reloaded = workflowRepository.findById(w.getId()).orElseThrow();
        assertThat(reloaded.getStage()).isEqualTo(WorkflowStage.PREPARE);
    }

    @Test
    void laterMastersUploadBlocksAdvanceUntilMapped() throws Exception {
        UploadJob job1 = mastersJob("masters-1.json", Instant.now().minusSeconds(7200));
        TdsMappingTestData.seedNarayananSnapshots(orgId, job1.getId(), snapshotRepository);
        Workflow w = seedTdsPaymentWorkflow();

        putMapping(TdsMappingTestData.mappingItemsJson(sectionCodes()));
        assertThat(workflowRepository.findById(w.getId()).orElseThrow().getStage())
                .isEqualTo(WorkflowStage.FINDINGS_REVIEW);

        UploadJob job2 = mastersJob("masters-2.json", Instant.now());
        snapshot(job2, "TDS Payable 194IA", "Direct Expenses",
                "Expenses|Direct Expenses|Duties & Taxes", false);

        mockMvc.perform(post("/api/v1/workflows/{id}/advance", w.getId()).with(user(accountant)))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/v1/workflows/{id}/findings", w.getId()).with(user(accountant)))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                                "$.findings[?(@.source == 'TDS_MAPPING' && @.ledgerName == 'TDS Payable 194IA')].blocking")
                        .value(Matchers.hasItem(true)))
                .andExpect(jsonPath("$.blockingCount").value(1));

        putMapping("{\"items\":[{\"ledgerName\":\"TDS Payable 194IA\",\"role\":\"TDS_PAYABLE\"}]}");

        Workflow awaiting = workflowRepository.findById(w.getId()).orElseThrow();
        awaiting.setStage(WorkflowStage.DATA_UPLOADED);
        workflowRepository.save(awaiting);
        mockMvc.perform(post("/api/v1/workflows/{id}/analyze", w.getId()).with(user(accountant)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/workflows/{id}/advance", w.getId()).with(user(accountant)))
                .andExpect(status().isOk());
        assertThat(workflowRepository.findById(w.getId()).orElseThrow().getStage())
                .isEqualTo(WorkflowStage.PREPARE);
    }

    @Test
    void savingAmbiguousCandidateAsNotTdsCompletesGate() throws Exception {
        UploadJob job1 = mastersJob("masters-1.json", Instant.now().minusSeconds(7200));
        snapshot(job1, "TDS Suspense Payable Receivable", "Suspense A/c",
                "Suspense A/c", null);

        mockMvc.perform(get("/api/v1/tds/ledger-mapping").with(user(accountant)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.complete").value(false))
                .andExpect(jsonPath("$.blockers[0]")
                        .value("1 TDS ledgers not mapped: TDS Suspense Payable Receivable"))
                .andExpect(jsonPath(
                                "$.ledgers[?(@.ledgerName == 'TDS Suspense Payable Receivable')].suggestedRole")
                        .value(Matchers.hasItem(Matchers.nullValue())));

        putMapping("{\"items\":[{\"ledgerName\":\"TDS Suspense Payable Receivable\",\"role\":\"NOT_TDS\"}]}");

        mockMvc.perform(get("/api/v1/tds/ledger-mapping").with(user(accountant)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.complete").value(true))
                .andExpect(jsonPath("$.blockers.length()").value(0));
    }
}
