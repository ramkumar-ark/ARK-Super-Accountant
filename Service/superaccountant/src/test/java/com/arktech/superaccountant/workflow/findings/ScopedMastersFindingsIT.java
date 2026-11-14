package com.arktech.superaccountant.workflow.findings;

import com.arktech.superaccountant.login.models.ERole;
import com.arktech.superaccountant.login.models.Role;
import com.arktech.superaccountant.login.models.User;
import com.arktech.superaccountant.login.repository.RoleRepository;
import com.arktech.superaccountant.login.repository.UserRepository;
import com.arktech.superaccountant.login.security.services.UserDetailsImpl;
import com.arktech.superaccountant.masters.models.FindingSeverity;
import com.arktech.superaccountant.masters.models.LedgerCategory;
import com.arktech.superaccountant.masters.models.Organization;
import com.arktech.superaccountant.masters.models.PreconfiguredMaster;
import com.arktech.superaccountant.masters.models.ResolveStatus;
import com.arktech.superaccountant.masters.models.UploadJob;
import com.arktech.superaccountant.masters.models.UploadJobStatus;
import com.arktech.superaccountant.masters.models.UploadLedgerSnapshot;
import com.arktech.superaccountant.masters.models.ValidationFinding;
import com.arktech.superaccountant.masters.repository.OrganizationRepository;
import com.arktech.superaccountant.masters.repository.PreconfiguredMasterRepository;
import com.arktech.superaccountant.masters.repository.UploadJobRepository;
import com.arktech.superaccountant.masters.repository.UploadLedgerSnapshotRepository;
import com.arktech.superaccountant.masters.repository.ValidationFindingRepository;
import com.arktech.superaccountant.workflow.models.Workflow;
import com.arktech.superaccountant.workflow.models.WorkflowStage;
import com.arktech.superaccountant.workflow.models.WorkflowType;
import com.arktech.superaccountant.workflow.repository.WorkflowRepository;
import com.arktech.superaccountant.workflow.seam.WorkflowGate;
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

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The scoped masters finding source end to end (real Postgres via Testcontainers): GSTR1
 * workflows only see GST-scoped masters findings, TDS workflows only TDS-scoped ones, and
 * review actions run through the shared findings API. Needs Docker.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = "JWT_SECRET=test-jwt-secret-must-be-at-least-32-characters-long")
class ScopedMastersFindingsIT {

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
    RoleRepository roleRepository;
    @Autowired
    UserRepository userRepository;
    @Autowired
    WorkflowRepository workflowRepository;
    @Autowired
    UploadJobRepository uploadJobRepository;
    @Autowired
    UploadLedgerSnapshotRepository snapshotRepository;
    @Autowired
    ValidationFindingRepository findingRepository;
    @Autowired
    PreconfiguredMasterRepository masterRepository;
    @Autowired
    WorkflowGate workflowGate;

    UUID orgId;
    UploadJob job;
    ValidationFinding salesFinding;
    ValidationFinding contractorFinding;
    Workflow gstr1;
    Workflow tds;
    UserDetailsImpl operator;
    UserDetailsImpl accountant;
    UserDetailsImpl auditor;

    @BeforeEach
    void seed() {
        Organization org = new Organization("Scoped Masters IT " + UUID.randomUUID());
        org.setBooksStartDate(LocalDate.of(2025, 4, 1));
        orgId = organizationRepository.save(org).getId();

        job = new UploadJob();
        job.setOrganizationId(orgId);
        job.setFileName("masters.json");
        job.setStatus(UploadJobStatus.COMPLETED_WITH_MISMATCHES);
        job.setCreatedAt(Instant.now().minusSeconds(3600));
        job = uploadJobRepository.save(job);

        snapshot("Sales A/c", LedgerCategory.INCOME, null);
        snapshot("Contractor Charges", LedgerCategory.EXPENSE, "194C");

        PreconfiguredMaster master = new PreconfiguredMaster();
        master.setOrganizationId(orgId);
        master.setLedgerName("Contractor Charges");
        master.setCategory(LedgerCategory.EXPENSE);
        master.setExpectedParentGroup("Direct Expenses");
        masterRepository.save(master);

        salesFinding = finding("HSN_SAC_CODE", "Sales A/c", FindingSeverity.INFO);
        contractorFinding = finding("TDS_SECTION_MAPPING", "Contractor Charges", FindingSeverity.ERROR);

        gstr1 = workflow(WorkflowType.GSTR1);
        tds = workflow(WorkflowType.TDS_PAYMENT);

        operator = principal("op", ERole.ROLE_OPERATOR);
        accountant = principal("acct", ERole.ROLE_ACCOUNTANT);
        auditor = principal("aud", ERole.ROLE_AUDITOR_CA);
    }

    private void snapshot(String name, LedgerCategory category, String tdsSection) {
        UploadLedgerSnapshot s = new UploadLedgerSnapshot();
        s.setUploadJobId(job.getId());
        s.setOrganizationId(orgId);
        s.setLedgerName(name);
        s.setCategory(category);
        s.setTdsSection(tdsSection);
        snapshotRepository.save(s);
    }

    private ValidationFinding finding(String ruleCode, String ledgerName, FindingSeverity severity) {
        ValidationFinding f = new ValidationFinding();
        f.setUploadJobId(job.getId());
        f.setRuleCode(ruleCode);
        f.setCategory(LedgerCategory.EXPENSE);
        f.setLedgerName(ledgerName);
        f.setSeverity(severity);
        f.setResolveStatus(ResolveStatus.OPEN);
        f.setMessage("test");
        return findingRepository.saveAndFlush(f);
    }

    private Workflow workflow(WorkflowType type) {
        Workflow w = new Workflow();
        w.setOrganizationId(orgId);
        w.setType(type);
        w.setPeriodStart(LocalDate.of(2025, 4, 1));
        w.setPeriodEnd(LocalDate.of(2025, 4, 30));
        w.setPeriodKey("2025-04");
        w.setFinancialYear(2025);
        w.setStage(WorkflowStage.FINDINGS_REVIEW);
        w.setMastersJobId(job.getId());
        w.setCreatedBy("it");
        return workflowRepository.save(w);
    }

    private UserDetailsImpl principal(String name, ERole roleName) {
        Role role = roleRepository.findByName(roleName).orElseThrow();
        User u = new User();
        u.setUsername(name + "-" + UUID.randomUUID());
        u.setPassword("x");
        u.setEmail(name + "-" + UUID.randomUUID() + "@t.com");
        u.setRole(role);
        return UserDetailsImpl.build(userRepository.save(u), orgId);
    }

    @Test
    void list_gstr1VsTds_scopedAndBlockingCountMatchesGate() throws Exception {
        mockMvc.perform(get("/api/v1/workflows/" + gstr1.getId() + "/findings").with(user(accountant)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blockingCount").value(1))
                .andExpect(jsonPath("$.findings.length()").value(1))
                .andExpect(jsonPath("$.findings[0].id").value(salesFinding.getId().toString()))
                .andExpect(jsonPath("$.findings[0].source").value("MASTERS"))
                .andExpect(jsonPath("$.findings[0].findingType").value("HSN_SAC_CODE"));

        assertThat(workflowGate.blockingFindingCount(gstr1.getId())).isEqualTo(1);

        mockMvc.perform(get("/api/v1/workflows/" + tds.getId() + "/findings").with(user(operator)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blockingCount").value(1))
                .andExpect(jsonPath("$.findings.length()").value(1))
                .andExpect(jsonPath("$.findings[0].id").value(contractorFinding.getId().toString()))
                .andExpect(jsonPath("$.findings[0].findingType").value("TDS_SECTION_MAPPING"));

        assertThat(workflowGate.blockingFindingCount(tds.getId())).isEqualTo(1);
    }

    @Test
    void action_operatorAcknowledge_thenAccountantApprove_inPlace() throws Exception {
        mockMvc.perform(post("/api/v1/workflows/" + tds.getId() + "/findings/"
                        + contractorFinding.getId() + "/action")
                        .with(user(operator))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"ACKNOWLEDGE\",\"value\":\"194C\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resolveStatus").value("ACKNOWLEDGED"));

        mockMvc.perform(post("/api/v1/workflows/" + tds.getId() + "/findings/"
                        + contractorFinding.getId() + "/action")
                        .with(user(accountant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"APPROVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resolveStatus").value("APPROVED"));

        assertThat(findingRepository.findById(contractorFinding.getId()).orElseThrow()
                .getResolveStatus()).isEqualTo(ResolveStatus.APPROVED);
        assertThat(workflowGate.blockingFindingCount(tds.getId())).isZero();
    }

    @Test
    void action_outOfScopeOrOtherJobFinding_404() throws Exception {
        // F1 (HSN_SAC_CODE on an INCOME ledger) is out of scope for the TDS workflow.
        mockMvc.perform(post("/api/v1/workflows/" + tds.getId() + "/findings/"
                        + salesFinding.getId() + "/action")
                        .with(user(accountant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"ACKNOWLEDGE\",\"value\":\"1234\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Finding not found"));

        // A finding on an older masters job is not reachable from this workflow either.
        UploadJob olderJob = new UploadJob();
        olderJob.setOrganizationId(orgId);
        olderJob.setFileName("older.json");
        olderJob.setStatus(UploadJobStatus.COMPLETED);
        olderJob.setCreatedAt(Instant.now().minusSeconds(7200));
        olderJob = uploadJobRepository.save(olderJob);
        ValidationFinding oldFinding = new ValidationFinding();
        oldFinding.setUploadJobId(olderJob.getId());
        oldFinding.setRuleCode("HSN_SAC_CODE");
        oldFinding.setCategory(LedgerCategory.INCOME);
        oldFinding.setLedgerName("Sales A/c");
        oldFinding.setSeverity(FindingSeverity.INFO);
        oldFinding.setResolveStatus(ResolveStatus.OPEN);
        oldFinding.setMessage("test");
        oldFinding = findingRepository.saveAndFlush(oldFinding);

        mockMvc.perform(post("/api/v1/workflows/" + gstr1.getId() + "/findings/"
                        + oldFinding.getId() + "/action")
                        .with(user(accountant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"ACKNOWLEDGE\",\"value\":\"1234\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Finding not found"));
    }

    @Test
    void action_auditorCa_403() throws Exception {
        mockMvc.perform(post("/api/v1/workflows/" + tds.getId() + "/findings/"
                        + contractorFinding.getId() + "/action")
                        .with(user(auditor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"ACKNOWLEDGE\",\"value\":\"194C\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Only OPERATOR or ACCOUNTANT can resolve findings."));
    }

    @Test
    void advanceToPrepare_staleMastersJob_409() throws Exception {
        // Make the current findings non-blocking so only staleness can fail the advance.
        salesFinding.setResolveStatus(ResolveStatus.APPROVED);
        findingRepository.save(salesFinding);
        gstr1.setBaselineConfirmedAt(Instant.now());
        workflowRepository.save(gstr1);

        // A newer COMPLETED masters job makes the workflow's masters job stale.
        UploadJob newer = new UploadJob();
        newer.setOrganizationId(orgId);
        newer.setFileName("newer.json");
        newer.setStatus(UploadJobStatus.COMPLETED);
        newer.setCreatedAt(Instant.now());
        uploadJobRepository.save(newer);

        mockMvc.perform(post("/api/v1/workflows/" + gstr1.getId() + "/advance").with(user(accountant)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error")
                        .value("Masters job is not the latest completed masters job"));
    }
}
