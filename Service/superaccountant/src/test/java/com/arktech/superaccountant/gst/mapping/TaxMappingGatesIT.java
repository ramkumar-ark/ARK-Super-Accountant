package com.arktech.superaccountant.gst.mapping;

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
import com.arktech.superaccountant.masters.models.LedgerCategory;
import com.arktech.superaccountant.masters.models.Organization;
import com.arktech.superaccountant.masters.models.UploadJob;
import com.arktech.superaccountant.masters.models.UploadJobStatus;
import com.arktech.superaccountant.masters.models.UploadLedgerSnapshot;
import com.arktech.superaccountant.masters.repository.OrganizationRepository;
import com.arktech.superaccountant.masters.repository.UploadJobRepository;
import com.arktech.superaccountant.masters.repository.UploadLedgerSnapshotRepository;
import com.arktech.superaccountant.workflow.models.Workflow;
import com.arktech.superaccountant.workflow.models.WorkflowStage;
import com.arktech.superaccountant.workflow.models.WorkflowType;
import com.arktech.superaccountant.workflow.repository.WorkflowRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
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
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = "JWT_SECRET=test-jwt-secret-must-be-at-least-32-characters-long")
class TaxMappingGatesIT {

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
    ObjectMapper objectMapper = new ObjectMapper();
    @Autowired
    OrganizationRepository organizationRepository;
    @Autowired
    UploadJobRepository uploadJobRepository;
    @Autowired
    UploadLedgerSnapshotRepository snapshotRepository;
    @Autowired
    TaxLedgerMappingRepository mappingRepository;
    @Autowired
    DayBookUploadJobRepository dayBookUploadJobRepository;
    @Autowired
    WorkflowRepository workflowRepository;
    @Autowired
    RoleRepository roleRepository;
    @Autowired
    UserRepository userRepository;

    UUID orgId;
    UserDetailsImpl accountant;

    @BeforeEach
    void seed() {
        mappingRepository.deleteAll();

        Organization org = new Organization("Tax Mapping Gates IT " + UUID.randomUUID());
        org.setGstin("27AAPFU0939F1ZV");
        org.setFinancialYearStart(4);
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

    private UploadJob mastersJob(String fileName) {
        UploadJob job = new UploadJob();
        job.setOrganizationId(orgId);
        job.setFileName(fileName);
        job.setStatus(UploadJobStatus.COMPLETED);
        job.setCreatedAt(Instant.now());
        return uploadJobRepository.save(job);
    }

    private void gstSnapshot(UploadJob job, String name) {
        UploadLedgerSnapshot s = new UploadLedgerSnapshot();
        s.setUploadJobId(job.getId());
        s.setOrganizationId(orgId);
        s.setLedgerName(name);
        s.setCategory(LedgerCategory.GST);
        snapshotRepository.save(s);
    }

    private void completeMapping() throws Exception {
        mockMvc.perform(put("/api/v1/gst/tax-ledger-mapping").with(user(accountant))
                .contentType(MediaType.APPLICATION_JSON)
                .content(TaxMappingTestData.standardMappingItemsJson()))
                .andExpect(status().isOk());
    }

    private void putStartGstr1() throws Exception {
        mockMvc.perform(put("/api/v1/compliance/responsibility-start").with(user(accountant))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"type":"GSTR1","firstPeriodStart":"2025-06-01"}
                    """))
                .andExpect(status().isOk());
    }

    private void completedDayBookUpload() {
        DayBookUploadJob job = new DayBookUploadJob();
        job.setOrganizationId(orgId);
        job.setFileName("daybook.json");
        job.setStatus(UploadJobStatus.COMPLETED);
        job.setCoverageFrom(LocalDate.of(2025, 4, 1));
        job.setCoverageTo(LocalDate.of(2025, 7, 31));
        job.setTotalVouchersParsed(0);
        dayBookUploadJobRepository.saveAndFlush(job);
    }

    @Test
    void baselineConfirmRefusedWhileUnmapped() throws Exception {
        UploadJob job1 = mastersJob("masters-1.json");
        TaxMappingTestData.standardLedgerNames().forEach(name -> gstSnapshot(job1, name));
        putStartGstr1();
        completedDayBookUpload();

        mockMvc.perform(post("/api/v1/compliance/baseline/confirm").with(user(accountant))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error")
                        .value(org.hamcrest.Matchers.containsString("tax ledgers not mapped")));

        completeMapping();

        mockMvc.perform(post("/api/v1/compliance/baseline/confirm").with(user(accountant))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isOk());
    }

    @Test
    void newGstLedgerInLaterMastersBlocksPrepare() throws Exception {
        UploadJob job1 = mastersJob("masters-1.json");
        TaxMappingTestData.standardLedgerNames().forEach(name -> gstSnapshot(job1, name));
        completeMapping();

        putStartGstr1();
        completedDayBookUpload();
        MvcResult debug2 = mockMvc.perform(post("/api/v1/compliance/baseline/confirm").with(user(accountant))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andReturn();
        System.out.println("CONFIRM2=" + debug2.getResponse().getContentAsString());

        Workflow w = new Workflow();
        w.setOrganizationId(orgId);
        w.setType(WorkflowType.GSTR1);
        w.setPeriodStart(LocalDate.of(2025, 6, 1));
        w.setPeriodEnd(LocalDate.of(2025, 6, 30));
        w.setPeriodKey("2025-06");
        w.setFinancialYear(2025);
        w.setStage(WorkflowStage.FINDINGS_REVIEW);
        w.setActive(true);
        w.setCreatedBy("system");
        w.setMastersJobId(job1.getId());
        w = workflowRepository.saveAndFlush(w);

        mockMvc.perform(post("/api/v1/workflows/{id}/advance", w.getId()).with(user(accountant)))
                .andExpect(status().isOk());

        // A later masters upload adds a new GST ledger; mapping is now stale.
        UploadJob job2 = mastersJob("masters-2.json");
        gstSnapshot(job2, "Output Cess");
        w.setStage(WorkflowStage.FINDINGS_REVIEW);
        workflowRepository.saveAndFlush(w);

        mockMvc.perform(post("/api/v1/workflows/{id}/advance", w.getId()).with(user(accountant)))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/v1/workflows/{id}/findings", w.getId()).with(user(accountant)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.findings[?(@.source == 'TAX_MAPPING' && @.ledgerName == 'Output Cess')].blocking")
                        .value(true))
                .andExpect(jsonPath("$.blockingCount").value(1));
    }
}
