package com.arktech.superaccountant.gst.workflow;

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
import com.arktech.superaccountant.masters.models.FindingSeverity;
import com.arktech.superaccountant.masters.models.GstApplicabilityType;
import com.arktech.superaccountant.masters.models.LedgerCategory;
import com.arktech.superaccountant.masters.models.Organization;
import com.arktech.superaccountant.masters.models.ResolveStatus;
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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.math.BigDecimal;
import java.time.Instant;
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

/**
 * The GST day-book finding source end to end (real Postgres via Testcontainers): a GSTR3B
 * workflow lists the day book's GST_NOT_CHARGED as a HIGH blocking finding, GSTR1 does not see
 * purchase-side findings, an approved finding is carried across re-analysis and listed once,
 * and MEDIUM-only findings do not block the advance to PREPARE. Needs Docker.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = "JWT_SECRET=test-jwt-secret-must-be-at-least-32-characters-long")
class GstWorkflowFindingsIT {

    private static final String ORG_GSTIN = "27AAPFU0939F1ZV";
    private static final String VENDOR_GSTIN = "27AAAPL1234C1ZV";

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
    DayBookUploadJobRepository dayBookUploadJobRepository;
    @Autowired
    ParsedVoucherRepository parsedVoucherRepository;

    UUID orgId;
    UploadJob mastersJob;
    DayBookUploadJob daybookJob;
    Workflow gstr3b;
    Workflow gstr1;
    UserDetailsImpl operator;
    UserDetailsImpl accountant;

    @BeforeEach
    void seed() {
        Organization org = new Organization("Gst Workflow Findings IT " + UUID.randomUUID());
        org.setGstin(ORG_GSTIN);
        org.setBooksStartDate(LocalDate.of(2025, 4, 1));
        orgId = organizationRepository.save(org).getId();

        mastersJob = new UploadJob();
        mastersJob.setOrganizationId(orgId);
        mastersJob.setFileName("masters.json");
        mastersJob.setStatus(UploadJobStatus.COMPLETED);
        mastersJob.setCreatedAt(Instant.now().minusSeconds(7200));
        mastersJob = uploadJobRepository.save(mastersJob);

        snapshot("Rent", LedgerCategory.EXPENSE, true, GstApplicabilityType.TAXABLE, null);
        snapshot("Vendor X", LedgerCategory.PURCHASE, null, null, VENDOR_GSTIN);
        snapshot("Sales A/c", LedgerCategory.INCOME, false, GstApplicabilityType.NOT_APPLICABLE, null);

        daybookJob = daybookJob("daybook.json");

        // Purchase voucher for a registered party debiting taxable Rent with no GST entries.
        ParsedVoucher purchase = voucher("Purchase", LocalDate.of(2025, 4, 10), "Vendor X", VENDOR_GSTIN,
                "Regular", "P-001");
        entry(purchase, "Rent", new BigDecimal("10000"), true);
        entry(purchase, "Vendor X", new BigDecimal("-10000"), false);
        parsedVoucherRepository.save(purchase);

        // One Sales voucher so the outward side has something to look at.
        ParsedVoucher sales = voucher("Sales", LocalDate.of(2025, 4, 11), "Customer Y", VENDOR_GSTIN,
                "Regular", "S-001");
        entry(sales, "Customer Y", new BigDecimal("5000"), true);
        entry(sales, "Sales A/c", new BigDecimal("-5000"), false);
        parsedVoucherRepository.save(sales);

        gstr3b = workflow(WorkflowType.GSTR3B);
        gstr1 = workflow(WorkflowType.GSTR1);

        operator = principal("op", ERole.ROLE_OPERATOR);
        accountant = principal("acct", ERole.ROLE_ACCOUNTANT);
    }

    private void snapshot(String name, LedgerCategory category, Boolean gstApplicable,
                          GstApplicabilityType type, String gstin) {
        UploadLedgerSnapshot s = new UploadLedgerSnapshot();
        s.setUploadJobId(mastersJob.getId());
        s.setOrganizationId(orgId);
        s.setLedgerName(name);
        s.setCategory(category);
        s.setGstApplicable(gstApplicable);
        s.setGstApplicabilityType(type);
        s.setGstRegistrationType("Regular");
        s.setGstin(gstin);
        snapshotRepository.save(s);
    }

    private DayBookUploadJob daybookJob(String fileName) {
        DayBookUploadJob job = new DayBookUploadJob();
        job.setOrganizationId(orgId);
        job.setFileName(fileName);
        job.setStatus(UploadJobStatus.COMPLETED);
        job.setUploadedBy("it");
        job.setCoverageFrom(LocalDate.of(2025, 4, 1));
        job.setCoverageTo(LocalDate.of(2025, 4, 30));
        job.setCreatedAt(Instant.now());
        job.setCompletedAt(Instant.now());
        return dayBookUploadJobRepository.save(job);
    }

    private ParsedVoucher voucher(String typeName, LocalDate date, String party, String partyGstin,
                                  String registrationType, String number) {
        ParsedVoucher v = new ParsedVoucher();
        v.setUploadJobId(daybookJob.getId());
        v.setOrganizationId(orgId);
        v.setVoucherTypeName(typeName);
        v.setVoucherDate(date);
        v.setVoucherNumber(number);
        v.setPartyLedgerName(party);
        v.setPartyGstin(partyGstin);
        v.setPartyGstRegistrationType(registrationType);
        v.setTotalDebit(BigDecimal.ZERO);
        v.setTotalCredit(BigDecimal.ZERO);
        v.setEntries(new ArrayList<>());
        return v;
    }

    private void entry(ParsedVoucher v, String ledgerName, BigDecimal amount, boolean isParty) {
        ParsedVoucherEntry e = new ParsedVoucherEntry();
        e.setParsedVoucher(v);
        e.setOrganizationId(orgId);
        e.setLedgerName(ledgerName);
        e.setAmount(amount);
        e.setDebit(amount.compareTo(BigDecimal.ZERO) > 0);
        e.setPartyLedger(isParty);
        v.getEntries().add(e);
    }

    private Workflow workflow(WorkflowType type) {
        Workflow w = new Workflow();
        w.setOrganizationId(orgId);
        w.setType(type);
        w.setPeriodStart(LocalDate.of(2025, 4, 1));
        w.setPeriodEnd(LocalDate.of(2025, 4, 30));
        w.setPeriodKey("2025-04");
        w.setFinancialYear(2025);
        w.setStage(WorkflowStage.DATA_UPLOADED);
        w.setUploadJobId(daybookJob.getId());
        w.setMastersJobId(mastersJob.getId());
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

    private void analyze(Workflow w) throws Exception {
        mockMvc.perform(post("/api/v1/workflows/" + w.getId() + "/analyze").with(user(accountant)))
                .andExpect(status().isOk());
        assertThat(workflowRepository.findById(w.getId()).orElseThrow().getStage())
                .isEqualTo(WorkflowStage.FINDINGS_REVIEW);
    }

    private JsonNode findings(Workflow w) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/workflows/" + w.getId() + "/findings")
                        .with(user(accountant)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private List<JsonNode> gstFindings(JsonNode response) {
        List<JsonNode> out = new ArrayList<>();
        for (JsonNode f : response.get("findings")) {
            if ("GST".equals(f.get("source").asText())) {
                out.add(f);
            }
        }
        return out;
    }

    @Test
    void gstr3b_analysis_listsGstNotChargedAsBlocking() throws Exception {
        analyze(gstr3b);

        JsonNode response = findings(gstr3b);
        List<JsonNode> gst = gstFindings(response);
        assertThat(gst).hasSize(1);
        JsonNode f = gst.get(0);
        assertThat(f.get("findingType").asText()).isEqualTo("GST_NOT_CHARGED");
        assertThat(f.get("severity").asText()).isEqualTo("HIGH");
        assertThat(f.get("blocking").asBoolean()).isTrue();
        assertThat(f.get("voucherDate").asText()).isEqualTo("2025-04-10");
        assertThat(f.get("ledgerName").asText()).isEqualTo("Rent");
        assertThat(response.get("blockingCount").asInt()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void gstr1_doesNotListPurchaseFindings() throws Exception {
        analyze(gstr1);

        JsonNode response = findings(gstr1);
        assertThat(gstFindings(response)).isEmpty();
    }

    @Test
    void approvedFinding_carriedAcrossReanalysis_listedOnce() throws Exception {
        analyze(gstr3b);

        String oldId = gstFindings(findings(gstr3b)).get(0).get("id").asText();

        mockMvc.perform(post("/api/v1/workflows/" + gstr3b.getId() + "/findings/" + oldId + "/action")
                        .with(user(operator))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"ACKNOWLEDGE\",\"note\":\"Will fix in Tally\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/workflows/" + gstr3b.getId() + "/findings/" + oldId + "/action")
                        .with(user(accountant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"APPROVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resolveStatus").value("APPROVED"));

        // Re-bind the same upload and re-run the analysis. The binding endpoint itself clears
        // mastersJobId and its after-commit listener would run the analysis without the masters
        // snapshots, so the IT re-binds directly at the repository level.
        Workflow w = workflowRepository.findById(gstr3b.getId()).orElseThrow();
        w.setUploadJobId(daybookJob.getId());
        w.setMastersJobId(mastersJob.getId());
        w.setStage(WorkflowStage.DATA_UPLOADED);
        workflowRepository.save(w);

        analyze(gstr3b);

        JsonNode response = findings(gstr3b);
        List<JsonNode> gst = gstFindings(response);
        assertThat(gst).hasSize(1);
        JsonNode carried = gst.get(0);
        assertThat(carried.get("findingType").asText()).isEqualTo("GST_NOT_CHARGED");
        assertThat(carried.get("resolveStatus").asText()).isEqualTo(ResolveStatus.APPROVED.name());
        assertThat(carried.get("carriedFromFindingId").asText()).isEqualTo(oldId);

        // The old (superseded) finding is no longer reachable from this workflow.
        mockMvc.perform(post("/api/v1/workflows/" + gstr3b.getId() + "/findings/" + oldId + "/action")
                        .with(user(operator))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"ACKNOWLEDGE\",\"note\":\"again\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Finding not found"));
    }

    @Test
    void mediumOnlyFindings_doNotBlockAdvance() throws Exception {
        snapshot("Office Expenses", LedgerCategory.EXPENSE, false, GstApplicabilityType.NOT_APPLICABLE, null);

        // A second upload whose only purchase voucher has a registered-by-type party with no
        // GSTIN anywhere: PARTY_GSTIN_ISSUE (MEDIUM) fires, rule 1 stays silent.
        DayBookUploadJob job2 = daybookJob("daybook-medium.json");
        job2.setCreatedAt(Instant.now().plusSeconds(60)); // strictly newer than the seeded upload
        dayBookUploadJobRepository.save(job2);
        ParsedVoucher purchase = new ParsedVoucher();
        purchase.setUploadJobId(job2.getId());
        purchase.setOrganizationId(orgId);
        purchase.setVoucherTypeName("Purchase");
        purchase.setVoucherDate(LocalDate.of(2025, 4, 12));
        purchase.setVoucherNumber("P-002");
        purchase.setPartyLedgerName("Vendor Y");
        purchase.setPartyGstRegistrationType("Regular");
        purchase.setTotalDebit(BigDecimal.ZERO);
        purchase.setTotalCredit(BigDecimal.ZERO);
        purchase.setEntries(new ArrayList<>());
        ParsedVoucherEntry expense = new ParsedVoucherEntry();
        expense.setParsedVoucher(purchase);
        expense.setOrganizationId(orgId);
        expense.setLedgerName("Office Expenses");
        expense.setAmount(new BigDecimal("2000"));
        expense.setDebit(true);
        expense.setPartyLedger(false);
        purchase.getEntries().add(expense);
        ParsedVoucherEntry party = new ParsedVoucherEntry();
        party.setParsedVoucher(purchase);
        party.setOrganizationId(orgId);
        party.setLedgerName("Vendor Y");
        party.setAmount(new BigDecimal("-2000"));
        party.setDebit(false);
        party.setPartyLedger(true);
        purchase.getEntries().add(party);
        parsedVoucherRepository.save(purchase);

        // Re-bind the period's single GSTR3B workflow to the second upload; the binding
        // triggers the after-commit analysis itself (no masters needed for this scenario).
        mockMvc.perform(post("/api/v1/workflows/" + gstr3b.getId() + "/use-upload")
                        .with(user(accountant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"uploadJobId\":\"" + job2.getId() + "\"}"))
                .andExpect(status().isOk());

        JsonNode response = findings(gstr3b);
        List<JsonNode> gst = gstFindings(response);
        assertThat(gst).hasSize(1);
        assertThat(gst.get(0).get("findingType").asText()).isEqualTo("PARTY_GSTIN_ISSUE");
        assertThat(gst.get(0).get("severity").asText()).isEqualTo("MEDIUM");
        assertThat(gst.get(0).get("blocking").asBoolean()).isFalse();
        assertThat(response.get("blockingCount").asInt()).isZero();

        Workflow fresh = workflowRepository.findById(gstr3b.getId()).orElseThrow();
        fresh.setBaselineConfirmedAt(Instant.now());
        workflowRepository.save(fresh);

        mockMvc.perform(post("/api/v1/workflows/" + gstr3b.getId() + "/advance").with(user(accountant)))
                .andExpect(status().isOk());
        assertThat(workflowRepository.findById(gstr3b.getId()).orElseThrow().getStage())
                .isEqualTo(WorkflowStage.PREPARE);
    }
}
