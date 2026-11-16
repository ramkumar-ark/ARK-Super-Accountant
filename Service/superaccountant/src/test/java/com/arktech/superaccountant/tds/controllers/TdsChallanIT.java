package com.arktech.superaccountant.tds.controllers;

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
import com.arktech.superaccountant.tds.models.TdsFinding;
import com.arktech.superaccountant.tds.models.TdsFindingType;
import com.arktech.superaccountant.tds.models.TdsPaymentDetail;
import com.arktech.superaccountant.tds.repository.TdsFindingRepository;
import com.arktech.superaccountant.tds.repository.TdsPaymentDetailRepository;
import com.arktech.superaccountant.workflow.analysis.WorkflowAnalysisService;
import com.arktech.superaccountant.workflow.models.Workflow;
import com.arktech.superaccountant.workflow.models.WorkflowStage;
import com.arktech.superaccountant.workflow.models.WorkflowType;
import com.arktech.superaccountant.tds.models.TdsChallan;
import com.arktech.superaccountant.tds.models.TdsChallanStatus;
import com.arktech.superaccountant.tds.models.TdsPaymentDetail;
import com.arktech.superaccountant.tds.repository.TdsChallanRepository;
import com.arktech.superaccountant.tds.repository.TdsPaymentDetailRepository;
import com.arktech.superaccountant.workflow.repository.WorkflowRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import com.jayway.jsonpath.JsonPath;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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
class TdsChallanIT {

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
    @Autowired TdsChallanRepository challanRepository;
    @Autowired MockMvc mockMvc;

    UUID orgId;
    UserDetailsImpl principal;
    UserDetailsImpl operatorPrincipal;
    UUID mastersJobId;
    UUID dayBookJobId;

    @BeforeEach
    void seed() {
        Organization org = new Organization();
        org.setName("TDS Challan IT " + UUID.randomUUID());
        org.setBooksStartDate(LocalDate.of(2025, 4, 1));
        orgId = organizationRepository.save(org).getId();

        Role role = roleRepository.findByName(ERole.ROLE_ACCOUNTANT).orElseThrow();
        User user = new User();
        user.setUsername("acct-" + UUID.randomUUID());
        user.setPassword("x");
        user.setEmail("acct-" + UUID.randomUUID() + "@t.com");
        user.setRole(role);
        principal = UserDetailsImpl.build(userRepository.save(user), orgId);

        Role operatorRole = roleRepository.findByName(ERole.ROLE_OPERATOR).orElseThrow();
        User operator = new User();
        operator.setUsername("op-" + UUID.randomUUID());
        operator.setPassword("x");
        operator.setEmail("op-" + UUID.randomUUID() + "@t.com");
        operator.setRole(operatorRole);
        operatorPrincipal = UserDetailsImpl.build(userRepository.save(operator), orgId);

        UploadJob mastersJob = new UploadJob();
        mastersJob.setOrganizationId(orgId);
        mastersJob.setFileName("masters.xml");
        mastersJob.setStatus(UploadJobStatus.COMPLETED);
        mastersJob.setUploadedBy("it");
        mastersJobId = uploadJobRepository.save(mastersJob).getId();

        snapshot(mastersJobId, "Subcontractor Charges", LedgerCategory.EXPENSE, true, "194C", null);
        snapshot(mastersJobId, "ABC Contractors", LedgerCategory.CREDITOR, null, null, null);
        snapshot(mastersJobId, "TDS Payable 194C", LedgerCategory.TDS, true, "194C", null);

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
        w.setStage(WorkflowStage.PREPARE);
        w.setCreatedBy("it");
        return workflowRepository.save(w);
    }

    private TdsPaymentDetail seedDetail(UUID workflowId) {
        TdsPaymentDetail d = new TdsPaymentDetail();
        d.setWorkflowId(workflowId);
        d.setMonth(LocalDate.of(2025, 4, 1));
        d.setDueDate(LocalDate.of(2025, 5, 7));
        d.setTdsDeducted(new BigDecimal("5000.00"));
        return paymentDetailRepository.save(d);
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder postJson(
            String url, String json) {
        return org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post(url)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(json);
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor
            as(UserDetailsImpl p) {
        return org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                .user(p);
    }

    @Test
    void operatorProposes_accountantApproves_thenFinalizeSucceeds() throws Exception {
        Workflow w = seedWorkflow();
        seedDetail(w.getId());

        String challanJson = "{\"bsrCode\":\"1234567\",\"depositDate\":\"2025-05-07\","
                + "\"challanSerialNumber\":\"1234\",\"cin\":\"cin-1\","
                + "\"tdsAmount\":5000,\"surcharge\":0,\"educationCess\":0,"
                + "\"interest\":0,\"penalty\":0,\"nilChallan\":false}";

        String created = mockMvc.perform(postJson("/api/v1/workflows/" + w.getId()
                        + "/tds/challans", challanJson).with(as(operatorPrincipal)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .status().isCreated())
                .andReturn().getResponse().getContentAsString();
        assertTrue(created.contains("\"status\":\"PROPOSED\""));
        UUID challanId = UUID.fromString(JsonPath.read(created, "$.id"));

        mockMvc.perform(postJson("/api/v1/workflows/" + w.getId()
                        + "/tds/challans/" + challanId + "/approve", "{}")
                        .with(as(operatorPrincipal)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .status().isForbidden());

        String conflict = mockMvc.perform(postJson("/api/v1/workflows/" + w.getId()
                        + "/finalize", "{}").with(as(principal)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .status().isConflict())
                .andReturn().getResponse().getContentAsString();
        assertTrue(conflict.contains("awaiting accountant approval"));

        mockMvc.perform(postJson("/api/v1/workflows/" + w.getId()
                        + "/tds/challans/" + challanId + "/approve", "{}").with(as(principal)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .status().isOk());

        mockMvc.perform(postJson("/api/v1/workflows/" + w.getId()
                        + "/finalize", "{}").with(as(principal)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .status().isOk());

        assertEquals(WorkflowStage.FINALIZED,
                workflowRepository.findById(w.getId()).orElseThrow().getStage());
    }

    @Test
    void finalize_shortPayment_conflicts() throws Exception {
        Workflow w = seedWorkflow();
        seedDetail(w.getId());
        TdsChallan challan = new TdsChallan();
        challan.setPaymentWorkflowId(w.getId());
        challan.setBsrCode("1234567");
        challan.setDepositDate(LocalDate.of(2025, 5, 7));
        challan.setChallanSerialNumber("1234");
        challan.setTdsAmount(new BigDecimal("4000.00"));
        challan.setTotalAmount(new BigDecimal("4000.00"));
        challan.setStatus(TdsChallanStatus.APPROVED);
        challanRepository.save(challan);

        String body = mockMvc.perform(postJson("/api/v1/workflows/" + w.getId()
                        + "/finalize", "{}").with(as(principal)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .status().isConflict())
                .andReturn().getResponse().getContentAsString();

        assertTrue(body.contains("(short by 1000.00)"));
    }

    @Test
    void proposeChallan_invalidJson_returns400WithoutTrace() throws Exception {
        Workflow w = seedWorkflow();
        seedDetail(w.getId());

        String malformedJson = "{\"bsrCode\":\"1234567\",\"depositDate\":\"not-a-date\"}";

        mockMvc.perform(postJson("/api/v1/workflows/" + w.getId()
                        + "/tds/challans", malformedJson).with(as(principal)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .status().isBadRequest())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.message").exists())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.trace").doesNotExist());
    }
}
