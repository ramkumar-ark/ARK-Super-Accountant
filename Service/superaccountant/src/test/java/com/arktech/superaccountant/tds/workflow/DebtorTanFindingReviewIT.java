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
import com.arktech.superaccountant.masters.models.UploadJob;
import com.arktech.superaccountant.masters.models.UploadJobStatus;
import com.arktech.superaccountant.masters.models.UploadLedgerSnapshot;
import com.arktech.superaccountant.masters.repository.OrganizationRepository;
import com.arktech.superaccountant.masters.repository.UploadJobRepository;
import com.arktech.superaccountant.masters.repository.UploadLedgerSnapshotRepository;
import com.arktech.superaccountant.tds.models.DebtorTan;
import com.arktech.superaccountant.tds.models.TdsFindingType;
import com.arktech.superaccountant.tds.repository.DebtorTanRepository;
import com.arktech.superaccountant.workflow.analysis.WorkflowAnalysisService;
import com.arktech.superaccountant.workflow.models.Workflow;
import com.arktech.superaccountant.workflow.models.WorkflowStage;
import com.arktech.superaccountant.workflow.models.WorkflowType;
import com.arktech.superaccountant.workflow.repository.WorkflowRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = "JWT_SECRET=test-jwt-secret-must-be-at-least-32-characters-long")
class DebtorTanFindingReviewIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @org.springframework.test.context.DynamicPropertySource
    static void configureProperties(org.springframework.test.context.DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired MockMvc mockMvc;
    @Autowired WorkflowRepository workflowRepository;
    @Autowired WorkflowAnalysisService analysisService;
    @Autowired OrganizationRepository organizationRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired UserRepository userRepository;
    @Autowired UploadJobRepository uploadJobRepository;
    @Autowired UploadLedgerSnapshotRepository snapshotRepository;
    @Autowired DayBookUploadJobRepository dayBookJobRepository;
    @Autowired ParsedVoucherRepository parsedVoucherRepository;
    @Autowired DebtorTanRepository debtorTanRepository;

    UUID orgId;
    UserDetailsImpl principal;
    UUID workflowId;

    @BeforeEach
    void seed() {
        Organization org = new Organization();
        org.setName("Debtor TAN Review IT " + UUID.randomUUID());
        org.setBooksStartDate(LocalDate.of(2025, 4, 1));
        orgId = organizationRepository.save(org).getId();

        Role role = roleRepository.findByName(ERole.ROLE_ACCOUNTANT).orElseThrow();
        User user = new User();
        user.setUsername("acct-" + UUID.randomUUID().toString().substring(0, 8));
        user.setPassword("x");
        user.setEmail(UUID.randomUUID() + "@example.com");
        user.setRole(role);
        principal = UserDetailsImpl.build(userRepository.save(user), orgId);

        UploadJob mastersJob = new UploadJob();
        mastersJob.setOrganizationId(orgId);
        mastersJob.setFileName("masters.xml");
        mastersJob.setStatus(UploadJobStatus.COMPLETED);
        mastersJob.setUploadedBy("it");
        UUID mastersJobId = uploadJobRepository.save(mastersJob).getId();
        snapshot(mastersJobId, "TDS Receivable 194J", LedgerCategory.OTHER, null);
        snapshot(mastersJobId, "Acme Corp", LedgerCategory.OTHER, "Sundry Debtors|Current Assets");

        DayBookUploadJob dayBookJob = new DayBookUploadJob();
        dayBookJob.setOrganizationId(orgId);
        dayBookJob.setFileName("daybook.xml");
        dayBookJob.setStatus(UploadJobStatus.COMPLETED);
        dayBookJob.setUploadedBy("it");
        UUID dayBookJobId = dayBookJobRepository.save(dayBookJob).getId();

        ParsedVoucher voucher = new ParsedVoucher();
        voucher.setUploadJobId(dayBookJobId);
        voucher.setOrganizationId(orgId);
        voucher.setVoucherGuid("dv-debtor-tan-review");
        voucher.setVoucherNumber("V1");
        voucher.setVoucherTypeName("Journal");
        voucher.setVoucherDate(LocalDate.of(2025, 4, 10));
        voucher.setPartyLedgerName("Acme Corp");
        voucher.setContentHash("c".repeat(64));
        voucher.setCancelled(false);
        List<ParsedVoucherEntry> entries = new ArrayList<>();
        entry(voucher, entries, "TDS Receivable 194J", new BigDecimal("1000.00"), true);
        entry(voucher, entries, "Acme Corp", new BigDecimal("-1000.00"), false);
        voucher.setEntries(entries);
        parsedVoucherRepository.save(voucher);

        Workflow workflow = new Workflow();
        workflow.setOrganizationId(orgId);
        workflow.setType(WorkflowType.TDS_PAYMENT);
        workflow.setPeriodStart(LocalDate.of(2025, 4, 1));
        workflow.setPeriodEnd(LocalDate.of(2025, 4, 30));
        workflow.setPeriodKey("2025-04");
        workflow.setFinancialYear(2025);
        workflow.setStage(WorkflowStage.DATA_UPLOADED);
        workflow.setUploadJobId(dayBookJobId);
        workflow.setCreatedBy("it");
        workflowId = workflowRepository.save(workflow).getId();
    }

    @Test
    void accountantTanProposalStoresTheDebtorTan() throws Exception {
        UUID findingId = runAnalysisAndFindDebtorTanFinding();

        mockMvc.perform(post("/api/v1/workflows/" + workflowId + "/findings/" + findingId + "/action")
                        .with(user(principal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"ACKNOWLEDGE\",\"value\":\" blra12345c \"}"))
                .andExpect(status().isOk());

        List<DebtorTan> records = debtorTanRepository.findByOrganizationIdOrderByLedgerNameAsc(orgId);
        assertThat(records).hasSize(1);
        assertThat(records.get(0).getLedgerName()).isEqualTo("Acme Corp");
        assertThat(records.get(0).getTan()).isEqualTo("BLRA12345C");
        assertThat(records.get(0).getSourceFindingId()).isEqualTo(findingId);
        assertThat(records.get(0).getUpdatedBy()).isEqualTo(principal.getUsername());
    }

    @Test
    void invalidTanProposalIsRejectedWithoutStoringAnything() throws Exception {
        UUID findingId = runAnalysisAndFindDebtorTanFinding();

        mockMvc.perform(post("/api/v1/workflows/" + workflowId + "/findings/" + findingId + "/action")
                        .with(user(principal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"ACKNOWLEDGE\",\"value\":\"not-a-tan\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(
                        "Enter a valid 10-character TAN (format AAAA99999A, e.g. BLRA12345C)."));

        assertThat(debtorTanRepository.findByOrganizationIdOrderByLedgerNameAsc(orgId)).isEmpty();
    }

    private UUID runAnalysisAndFindDebtorTanFinding() throws Exception {
        analysisService.run(orgId, workflowId, "it");
        String body = mockMvc.perform(get("/api/v1/workflows/" + workflowId + "/findings")
                        .with(user(principal)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<java.util.Map<String, Object>> matches = JsonPath.read(body,
                "$.findings[?(@.findingType == 'DEBTOR_TDS_RECEIVABLE_MISSING_TAN')]");
        assertThat(matches).hasSize(1);
        return UUID.fromString((String) matches.get(0).get("id"));
    }

    private void entry(ParsedVoucher v, List<ParsedVoucherEntry> entries, String ledger,
                       BigDecimal amount, boolean debit) {
        ParsedVoucherEntry e = new ParsedVoucherEntry();
        e.setParsedVoucher(v);
        e.setOrganizationId(orgId);
        e.setLedgerName(ledger);
        e.setAmount(amount);
        e.setDebit(debit);
        entries.add(e);
    }

    private void snapshot(UUID jobId, String ledger, LedgerCategory category, String groupChain) {
        UploadLedgerSnapshot snapshot = new UploadLedgerSnapshot();
        snapshot.setUploadJobId(jobId);
        snapshot.setOrganizationId(orgId);
        snapshot.setLedgerName(ledger);
        snapshot.setCategory(category);
        snapshot.setGroupChain(groupChain);
        snapshotRepository.save(snapshot);
    }
}
