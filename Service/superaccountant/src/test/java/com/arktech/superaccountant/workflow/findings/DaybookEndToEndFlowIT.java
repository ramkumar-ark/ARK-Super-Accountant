package com.arktech.superaccountant.workflow.findings;

import com.arktech.superaccountant.daybook.models.DayBookUploadJob;
import com.arktech.superaccountant.daybook.models.ParsedVoucher;
import com.arktech.superaccountant.daybook.models.ParsedVoucherEntry;
import com.arktech.superaccountant.daybook.repository.DayBookUploadJobRepository;
import com.arktech.superaccountant.daybook.repository.ParsedVoucherRepository;
import com.arktech.superaccountant.gst.mapping.TaxMappingTestData;
import com.arktech.superaccountant.login.models.ERole;
import com.arktech.superaccountant.login.models.Role;
import com.arktech.superaccountant.login.models.User;
import com.arktech.superaccountant.login.repository.RoleRepository;
import com.arktech.superaccountant.login.repository.UserRepository;
import com.arktech.superaccountant.login.security.services.UserDetailsImpl;
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
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Day-book end to end (real Postgres via Testcontainers): one upload analysed into GST and TDS
 * findings, the submit/approve review flow driving both workflow gates to zero, and the role
 * matrix on the findings action API. Needs Docker.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = "JWT_SECRET=test-jwt-secret-must-be-at-least-32-characters-long")
class DaybookEndToEndFlowIT {

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
    com.arktech.superaccountant.gst.mapping.TaxLedgerMappingRepository taxLedgerMappingRepository;
    @Autowired
    com.arktech.superaccountant.masters.services.FindingOverrideMasterService mastersService;
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
    Workflow tdsPayment;
    UserDetailsImpl operator;
    UserDetailsImpl accountant;
    UserDetailsImpl auditor;
    UserDetailsImpl owner;

    @BeforeEach
    void seed() {
        Organization org = new Organization("Daybook End To End IT " + UUID.randomUUID());
        org.setGstin(ORG_GSTIN);
        org.setBooksStartDate(LocalDate.of(2025, 4, 1));
        orgId = organizationRepository.save(org).getId();

        mastersJob = new UploadJob();
        mastersJob.setOrganizationId(orgId);
        mastersJob.setFileName("masters.json");
        mastersJob.setStatus(UploadJobStatus.COMPLETED);
        mastersJob.setCreatedAt(Instant.now().minusSeconds(7200));
        mastersJob = uploadJobRepository.save(mastersJob);

        for (String name : TaxMappingTestData.standardLedgerNames()) {
            snapshot(name, LedgerCategory.GST, null, null, null, null, null);
        }
        TaxMappingTestData.mapAll(orgId, mastersJob.getId(), taxLedgerMappingRepository, mastersService);
        snapshot("Rent", LedgerCategory.EXPENSE, true, GstApplicabilityType.TAXABLE, null, null, null);
        snapshot("Vendor X", LedgerCategory.PURCHASE, null, null, VENDOR_GSTIN, null, null);
        snapshot("Sales A/c", LedgerCategory.INCOME, false,
                GstApplicabilityType.NOT_APPLICABLE, null, null, null);
        snapshot("Subcontractor Charges", LedgerCategory.EXPENSE, false,
                GstApplicabilityType.NOT_APPLICABLE, null, true, "194C");
        snapshot("ABC Contractors", LedgerCategory.CREDITOR, null, null, null, null, null);
        snapshot("TDS Payable 194C", LedgerCategory.TDS, null, null, null, null, "194C");

        daybookJob = daybookJob("daybook.json");

        ParsedVoucher purchase = voucher("Purchase", LocalDate.of(2025, 4, 10), "Vendor X",
                VENDOR_GSTIN, "Regular", "P-001");
        entry(purchase, "Rent", new BigDecimal("10000"), true);
        entry(purchase, "Vendor X", new BigDecimal("-10000"), false);
        parsedVoucherRepository.save(purchase);

        ParsedVoucher sales = voucher("Sales", LocalDate.of(2025, 4, 11), "Customer Y",
                VENDOR_GSTIN, "Regular", "S-001");
        entry(sales, "Customer Y", new BigDecimal("5000"), true);
        entry(sales, "Sales A/c", new BigDecimal("-5000"), false);
        parsedVoucherRepository.save(sales);

        ParsedVoucher tdsVoucher = voucher("Journal", LocalDate.of(2025, 4, 12),
                "ABC Contractors", null, null, "J-001");
        entry(tdsVoucher, "Subcontractor Charges", new BigDecimal("100000"), true);
        entry(tdsVoucher, "ABC Contractors", new BigDecimal("-99000"), true);
        entry(tdsVoucher, "TDS Payable 194C", new BigDecimal("-1000"), false);
        parsedVoucherRepository.save(tdsVoucher);

        gstr3b = workflow(WorkflowType.GSTR3B);
        tdsPayment = workflow(WorkflowType.TDS_PAYMENT);

        operator = principal("op", ERole.ROLE_OPERATOR);
        accountant = principal("acct", ERole.ROLE_ACCOUNTANT);
        auditor = principal("aud", ERole.ROLE_AUDITOR_CA);
        owner = principal("own", ERole.ROLE_OWNER);
    }

    private void snapshot(String name, LedgerCategory category, Boolean gstApplicable,
                          GstApplicabilityType type, String gstin, Boolean tdsApplicable,
                          String tdsSection) {
        UploadLedgerSnapshot s = new UploadLedgerSnapshot();
        s.setUploadJobId(mastersJob.getId());
        s.setOrganizationId(orgId);
        s.setLedgerName(name);
        s.setCategory(category);
        s.setGstApplicable(gstApplicable);
        s.setGstApplicabilityType(type);
        s.setGstRegistrationType("Regular");
        s.setGstin(gstin);
        s.setTdsApplicable(tdsApplicable);
        s.setTdsSection(tdsSection);
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

    private JsonNode finding(JsonNode response, String source, String type) {
        for (JsonNode f : response.get("findings")) {
            if (source.equals(f.get("source").asText()) && type.equals(f.get("findingType").asText())) {
                return f;
            }
        }
        throw new AssertionError("No " + source + " " + type + " finding in " + response);
    }

    private void action(Workflow w, String findingId, UserDetailsImpl who, String body,
                        int expectedStatus) throws Exception {
        mockMvc.perform(post("/api/v1/workflows/" + w.getId() + "/findings/" + findingId + "/action")
                        .with(user(who))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().is(expectedStatus));
    }

    @Test
    void reviewFlowClearsBothGatesAndRoleMatrixIsEnforced() throws Exception {
        analyze(gstr3b);
        analyze(tdsPayment);

        JsonNode gstFindings = findings(gstr3b);
        JsonNode gstBlocking = finding(gstFindings, "GST", "GST_NOT_CHARGED");
        assertThat(gstFindings.get("blockingCount").asInt()).isPositive();

        JsonNode tdsFindings = findings(tdsPayment);
        JsonNode panMissing = finding(tdsFindings, "TDS", "PAN_MISSING");
        JsonNode higherRate = finding(tdsFindings, "TDS", "HIGHER_RATE_NOT_APPLIED");
        assertThat(tdsFindings.get("blockingCount").asInt()).isEqualTo(2);

        action(gstr3b, gstBlocking.get("id").asText(), operator,
                "{\"action\":\"ACKNOWLEDGE\",\"note\":\"Will fix in Tally\"}", 200);
        assertThat(findings(gstr3b).get("blockingCount").asInt()).isPositive();
        mockMvc.perform(post("/api/v1/workflows/" + gstr3b.getId() + "/advance")
                        .with(user(accountant)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value(containsString("blocking findings must be resolved first")));

        action(tdsPayment, panMissing.get("id").asText(), operator,
                "{\"action\":\"ACKNOWLEDGE\",\"value\":\"AAAPL1234C\"}", 200);
        action(tdsPayment, panMissing.get("id").asText(), operator,
                "{\"action\":\"APPROVE\"}", 403);
        action(tdsPayment, panMissing.get("id").asText(), auditor,
                "{\"action\":\"ACKNOWLEDGE\",\"value\":\"AAAPL1234C\"}", 403);
        action(tdsPayment, panMissing.get("id").asText(), owner,
                "{\"action\":\"ACKNOWLEDGE\",\"value\":\"AAAPL1234C\"}", 403);
        action(tdsPayment, panMissing.get("id").asText(), accountant,
                "{\"action\":\"APPROVE\"}", 200);

        action(tdsPayment, higherRate.get("id").asText(), accountant,
                "{\"action\":\"ACKNOWLEDGE\",\"note\":\"Reviewed the higher rate\"}", 200);

        action(gstr3b, gstBlocking.get("id").asText(), accountant, "{\"action\":\"APPROVE\"}", 200);
        assertThat(findings(gstr3b).get("blockingCount").asInt()).isZero();
        assertThat(findings(tdsPayment).get("blockingCount").asInt()).isZero();

        Workflow freshGst = workflowRepository.findById(gstr3b.getId()).orElseThrow();
        freshGst.setBaselineConfirmedAt(Instant.now());
        workflowRepository.save(freshGst);
        mockMvc.perform(post("/api/v1/workflows/" + gstr3b.getId() + "/advance")
                        .with(user(accountant)))
                .andExpect(status().isOk());
        assertThat(workflowRepository.findById(gstr3b.getId()).orElseThrow().getStage())
                .isEqualTo(WorkflowStage.PREPARE);

        Workflow freshTds = workflowRepository.findById(tdsPayment.getId()).orElseThrow();
        freshTds.setBaselineConfirmedAt(Instant.now());
        workflowRepository.save(freshTds);
        mockMvc.perform(post("/api/v1/workflows/" + tdsPayment.getId() + "/advance")
                        .with(user(accountant)))
                .andExpect(status().isOk());
        assertThat(workflowRepository.findById(tdsPayment.getId()).orElseThrow().getStage())
                .isEqualTo(WorkflowStage.PREPARE);
    }
}
