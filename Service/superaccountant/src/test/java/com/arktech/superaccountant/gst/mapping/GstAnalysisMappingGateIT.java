package com.arktech.superaccountant.gst.mapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.arktech.superaccountant.login.models.ERole;
import com.arktech.superaccountant.login.models.Role;
import com.arktech.superaccountant.login.models.User;
import com.arktech.superaccountant.login.repository.RoleRepository;
import com.arktech.superaccountant.login.repository.UserRepository;
import com.arktech.superaccountant.login.security.services.UserDetailsImpl;
import com.arktech.superaccountant.masters.models.LedgerCategory;
import com.arktech.superaccountant.masters.models.Organization;
import com.arktech.superaccountant.masters.models.UploadJob;
import com.arktech.superaccountant.masters.models.UploadJobStatus;
import com.arktech.superaccountant.masters.models.UploadLedgerSnapshot;
import com.arktech.superaccountant.masters.repository.OrganizationRepository;
import com.arktech.superaccountant.masters.repository.UploadJobRepository;
import com.arktech.superaccountant.masters.repository.UploadLedgerSnapshotRepository;
import com.arktech.superaccountant.workflow.analysis.AnalysisRunStatus;
import com.arktech.superaccountant.workflow.analysis.WorkflowAnalysisRun;
import com.arktech.superaccountant.workflow.analysis.WorkflowAnalysisRunRepository;
import com.arktech.superaccountant.workflow.models.Workflow;
import com.arktech.superaccountant.workflow.models.WorkflowStage;
import com.arktech.superaccountant.workflow.models.WorkflowType;
import com.arktech.superaccountant.workflow.repository.WorkflowRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
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
class GstAnalysisMappingGateIT {

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
    TaxLedgerMappingRepository mappingRepository;
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

        Organization org = new Organization("Analysis Mapping Gate IT " + UUID.randomUUID());
        org.setGstin("27AAPFU0939F1ZV");
        org.setFinancialYearStart(4);
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

    private Workflow seedGstr1Workflow() {
        UploadJob upload = new UploadJob();
        upload.setOrganizationId(orgId);
        upload.setFileName("daybook.json");
        upload.setStatus(UploadJobStatus.COMPLETED);
        upload.setCreatedAt(Instant.now().minusSeconds(3600));
        upload = uploadJobRepository.save(upload);

        Workflow w = new Workflow();
        w.setOrganizationId(orgId);
        w.setType(WorkflowType.GSTR1);
        w.setPeriodStart(java.time.LocalDate.of(2025, 6, 1));
        w.setPeriodEnd(java.time.LocalDate.of(2025, 6, 30));
        w.setPeriodKey("2025-06");
        w.setStage(WorkflowStage.DATA_UPLOADED);
        w.setActive(true);
        w.setCreatedBy("test");
        w.setUploadJobId(upload.getId());
        return workflowRepository.saveAndFlush(w);
    }

    private void seedMastersWithGstLedgers() {
        UploadJob job = new UploadJob();
        job.setOrganizationId(orgId);
        job.setFileName("masters.json");
        job.setStatus(UploadJobStatus.COMPLETED);
        job.setCreatedAt(Instant.now());
        job = uploadJobRepository.save(job);
        for (String name : List.of("Output CGST", "Output SGST")) {
            UploadLedgerSnapshot s = new UploadLedgerSnapshot();
            s.setUploadJobId(job.getId());
            s.setOrganizationId(orgId);
            s.setLedgerName(name);
            s.setCategory(LedgerCategory.GST);
            snapshotRepository.save(s);
        }
    }

    private void completeMapping() throws Exception {
        mockMvc.perform(put("/api/v1/gst/tax-ledger-mapping").with(user(accountant))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"items":[
                      {"ledgerName":"Output CGST","role":"OUTPUT","head":"CGST"},
                      {"ledgerName":"Output SGST","role":"OUTPUT","head":"SGST"}]}
                    """))
                .andExpect(status().isOk());
    }

    @Test
    void analysisStaysAtDataUploadedWhileUnmapped() throws Exception {
        seedMastersWithGstLedgers();
        Workflow w = seedGstr1Workflow();

        mockMvc.perform(post("/api/v1/workflows/{id}/analyze", w.getId()).with(user(accountant)))
                .andExpect(status().isOk());

        Workflow reloaded = workflowRepository.findById(w.getId()).orElseThrow();
        assertThat(reloaded.getStage()).isEqualTo(WorkflowStage.DATA_UPLOADED);

        List<WorkflowAnalysisRun> runs = runRepository.findByWorkflowIdOrderByStartedAtAsc(w.getId());
        assertThat(runs).isNotEmpty();
        WorkflowAnalysisRun latest = runs.get(runs.size() - 1);
        assertThat(latest.getAnalyzerCode()).isEqualTo("GST_OUTWARD");
        assertThat(latest.getStatus()).isEqualTo(AnalysisRunStatus.FAILED);
        assertThat(latest.getErrorMessage()).contains("tax ledgers not mapped");

        mockMvc.perform(get("/api/v1/workflows/{id}/findings", w.getId()).with(user(accountant)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.findings[?(@.source == 'TAX_MAPPING')].blocking")
                        .value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is(true))));
    }

    @Test
    void savingMappingReRunsAnalysis() throws Exception {
        seedMastersWithGstLedgers();
        Workflow w = seedGstr1Workflow();

        completeMapping();

        Workflow reloaded = workflowRepository.findById(w.getId()).orElseThrow();
        assertThat(reloaded.getStage()).isEqualTo(WorkflowStage.FINDINGS_REVIEW);
    }
}
