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
import com.arktech.superaccountant.tds.models.Tds26qDetail;
import com.arktech.superaccountant.tds.models.TdsChallan;
import com.arktech.superaccountant.tds.models.TdsChallanStatus;
import com.arktech.superaccountant.tds.models.TdsDeducteeEntry;
import com.arktech.superaccountant.tds.repository.Tds26qDetailRepository;
import com.arktech.superaccountant.tds.repository.TdsChallanRepository;
import com.arktech.superaccountant.tds.repository.TdsDeducteeEntryRepository;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
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
class Tds26qPrepareIT {

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
    @Autowired TdsDeducteeEntryRepository entryRepository;
    @Autowired TdsChallanRepository challanRepository;
    @Autowired Tds26qDetailRepository detail26qRepository;
    @Autowired MockMvc mockMvc;

    UUID orgId;
    UserDetailsImpl principal;
    UserDetailsImpl operatorPrincipal;
    UUID mastersJobId;
    UUID dayBookJobId;

    @BeforeEach
    void seed() {
        Organization org = new Organization();
        org.setName("TDS 26Q Prepare IT " + UUID.randomUUID());
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

        org.setTan("CHEA12345B");
        org.setPan("CHEAB1234F");
        org.setDeductorType("1");
        org.setResponsiblePersonName("Responsible Person");
        org.setRegisteredAddress("12 Ledger Lane");
        organizationRepository.save(org);

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

    private final java.util.Map<String, java.util.UUID> challanIdsByPeriod =
            new java.util.HashMap<>();

    private static org.springframework.test.web.servlet.request.RequestPostProcessor
            as(UserDetailsImpl p) {
        return org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                .user(p);
    }

    private Workflow seedPaymentWorkflow(String month, String periodKey, boolean approved,
                                         int serial) {
        return seedPaymentWorkflow(2025, month, periodKey, approved, serial);
    }

    private Workflow seedPaymentWorkflow(int fy, String month, String periodKey, boolean approved,
                                         int serial) {
        int monthNumber = Integer.parseInt(month.substring(month.length() - 2));
        Workflow w = new Workflow();
        w.setOrganizationId(orgId);
        w.setType(WorkflowType.TDS_PAYMENT);
        w.setPeriodStart(LocalDate.of(fy, monthNumber, 1));
        w.setPeriodEnd(w.getPeriodStart().plusMonths(1).minusDays(1));
        w.setPeriodKey(periodKey);
        w.setFinancialYear(fy);
        w.setStage(WorkflowStage.FINALIZED);
        w.setFinalizedAt(java.time.Instant.now());
        w.setFinalizedBy("it");
        w.setFrozenThrough(w.getPeriodEnd());
        w.setCreatedBy("it");
        Workflow saved = workflowRepository.save(w);
        TdsChallan challan = new TdsChallan();
        challan.setPaymentWorkflowId(saved.getId());
        challan.setBsrCode("1234567");
        challan.setDepositDate(w.getPeriodEnd().plusDays(7));
        challan.setChallanSerialNumber(String.valueOf(serial));
        challan.setTdsAmount(new BigDecimal("5000.00"));
        challan.setTotalAmount(new BigDecimal("5000.00"));
        challan.setStatus(approved ? TdsChallanStatus.APPROVED : TdsChallanStatus.PROPOSED);
        challanIdsByPeriod.put(periodKey, challanRepository.save(challan).getId());
        return saved;
    }

    private Workflow seedQuarter() {
        return seedQuarter(2025, "2025-Q1", LocalDate.of(2025, 4, 1));
    }

    private Workflow seedQuarter(int fy, String periodKey, LocalDate periodStart) {
        Workflow w = new Workflow();
        w.setOrganizationId(orgId);
        w.setType(WorkflowType.TDS_26Q);
        w.setPeriodStart(periodStart);
        w.setPeriodEnd(periodStart.plusMonths(3).minusDays(1));
        w.setPeriodKey(periodKey);
        w.setFinancialYear(fy);
        w.setStage(WorkflowStage.PREPARE);
        w.setCreatedBy("it");
        Workflow saved = workflowRepository.save(w);
        Tds26qDetail detail = new Tds26qDetail();
        detail.setWorkflowId(saved.getId());
        detail.setTan("CHEA12345B");
        detail.setDeductorPan("CHEAB1234F");
        detail.setDeductorName("Deductor");
        detail.setDeductorAddress("12 Ledger Lane");
        detail.setDeductorType("1");
        detail.setResponsiblePersonName("Responsible Person");
        detail26qRepository.save(detail);
        return saved;
    }

    private TdsDeducteeEntry seedEntry(Workflow quarter) {
        return seedEntry(quarter, "194C", LocalDate.of(2025, 5, 10));
    }

    private TdsDeducteeEntry seedEntry(Workflow quarter, String section, LocalDate paymentDate) {
        TdsDeducteeEntry e = new TdsDeducteeEntry();
        e.setWorkflowId(quarter.getId());
        e.setDeducteeName("ABC Contractors");
        e.setDeducteePan("ABCDE1234F");
        e.setPanValid(true);
        e.setSection(section);
        e.setAmountPaid(new BigDecimal("500000.00"));
        e.setTdsDeducted(new BigDecimal("5000.00"));
        e.setTdsDeposited(new BigDecimal("5000.00"));
        e.setDateOfPayment(paymentDate);
        e.setDateOfDeduction(paymentDate);
        e.setRateApplied(new BigDecimal("1.00"));
        e.setSourceVoucherIds("[\"v\"]");
        return entryRepository.save(e);
    }

    private String post(String url, String json) throws Exception {
        return mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post(url)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(json)
                        .with(as(principal)))
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void quarterChallans_listsApprovedChallansPerPaymentMonth() throws Exception {
        Workflow quarter = seedQuarter();
        seedPaymentWorkflow("2025-04", "2025-04", true, 111);
        seedPaymentWorkflow("2025-05", "2025-05", false, 222);
        seedPaymentWorkflow("2025-06", "2025-06", true, 333);

        String body = mockMvc.perform(org.springframework.test.web.servlet.request
                        .MockMvcRequestBuilders.get("/api/v1/workflows/" + quarter.getId()
                                + "/tds/quarter-challans").with(as(principal)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .status().isOk())
                .andReturn().getResponse().getContentAsString();

        List<java.util.Map<String, Object>> months = JsonPath.read(body, "$");
        assertEquals(3, months.size(), body);
        assertEquals("2025-04", months.get(0).get("periodKey"), body);
        assertEquals("2025-05", months.get(1).get("periodKey"), body);
        assertEquals("2025-06", months.get(2).get("periodKey"), body);
        assertEquals(1, ((List<?>) months.get(0).get("challans")).size(), body);
        assertEquals(0, ((List<?>) months.get(1).get("challans")).size(), body);
        assertEquals(1, ((List<?>) months.get(2).get("challans")).size(), body);
        assertEquals(challanIdsByPeriod.get("2025-04").toString(),
                ((java.util.Map<?, ?>) ((List<?>) months.get(0).get("challans")).get(0))
                        .get("id").toString(), body);
    }

    @Test
    void quarterChallans_rejectsNonQuarterWorkflow() throws Exception {
        Workflow payment = seedPaymentWorkflow("2025-04", "2025-04", true, 111);

        String body = mockMvc.perform(org.springframework.test.web.servlet.request
                        .MockMvcRequestBuilders.get("/api/v1/workflows/" + payment.getId()
                                + "/tds/quarter-challans").with(as(principal)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .status().isBadRequest())
                .andReturn().getResponse().getContentAsString();

        assertTrue(body.contains("Not a TDS_26Q workflow"), body);
    }

    @Test
    void map_rejectsChallanOutsideQuarterOrUnapproved() throws Exception {
        Workflow quarter = seedQuarter();
        seedPaymentWorkflow("2025-04", "2025-04", true, 111);
        seedPaymentWorkflow("2025-05", "2025-05", true, 222);
        seedPaymentWorkflow("2025-06", "2025-06", true, 333);
        seedPaymentWorkflow("2025-07", "2025-07", true, 444);
        seedPaymentWorkflow("2025-06", "2025-06b", false, 555);
        TdsDeducteeEntry entry = seedEntry(quarter);

        String outside = post("/api/v1/workflows/" + quarter.getId()
                + "/tds/entries/map", "{\"challanId\":\"" + challanIdsByPeriod.get("2025-07")
                + "\",\"entryIds\":[\"" + entry.getId() + "\"]}");
        assertTrue(outside.contains("Challan is not an approved challan of this quarter")
                || outside.contains("error"), outside);

        String proposed = post("/api/v1/workflows/" + quarter.getId()
                + "/tds/entries/map", "{\"challanId\":\"" + challanIdsByPeriod.get("2025-06b")
                + "\",\"entryIds\":[\"" + entry.getId() + "\"]}");
        assertTrue(proposed.contains("Challan is not an approved challan of this quarter")
                || proposed.contains("error"), proposed);

        assertTrue(entryRepository.findById(entry.getId()).orElseThrow()
                .getTdsChallanId() == null);
    }

    @Test
    void export_inPrepare_blockedUntilGatePasses_thenAllowed() throws Exception {
        Workflow quarter = seedQuarter();
        seedPaymentWorkflow("2025-04", "2025-04", true, 111);
        seedPaymentWorkflow("2025-05", "2025-05", true, 222);
        seedPaymentWorkflow("2025-06", "2025-06", true, 333);
        TdsDeducteeEntry e1 = seedEntry(quarter);
        TdsDeducteeEntry e2 = seedEntry(quarter);
        TdsDeducteeEntry e3 = seedEntry(quarter);

        String blocked = mockMvc.perform(org.springframework.test.web.servlet.request
                        .MockMvcRequestBuilders.get("/api/v1/workflows/" + quarter.getId()
                                + "/tds/export").with(as(principal)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .status().isConflict())
                .andReturn().getResponse().getContentAsString();
        assertTrue(blocked.contains("Export is available once the gate conditions pass"), blocked);

        post("/api/v1/workflows/" + quarter.getId() + "/tds/entries/map",
                "{\"challanId\":\"" + challanIdsByPeriod.get("2025-04")
                        + "\",\"entryIds\":[\"" + e1.getId() + "\"]}");
        post("/api/v1/workflows/" + quarter.getId() + "/tds/entries/map",
                "{\"challanId\":\"" + challanIdsByPeriod.get("2025-05")
                        + "\",\"entryIds\":[\"" + e2.getId() + "\"]}");
        post("/api/v1/workflows/" + quarter.getId() + "/tds/entries/map",
                "{\"challanId\":\"" + challanIdsByPeriod.get("2025-06")
                        + "\",\"entryIds\":[\"" + e3.getId() + "\"]}");

        var result = mockMvc.perform(org.springframework.test.web.servlet.request
                        .MockMvcRequestBuilders.get("/api/v1/workflows/" + quarter.getId()
                                + "/tds/export").with(as(principal)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .status().isOk())
                .andReturn();
        assertTrue(result.getResponse().getContentType().startsWith("text/plain"),
                String.valueOf(result.getResponse().getContentType()));
        assertTrue(result.getResponse().getContentAsString().startsWith("1^FH^"));
        assertTrue(result.getResponse().getHeader("Content-Disposition")
                .contains("26Q_CHEA12345B_FY2025_Q1.txt"));
    }

    @Test
    void export_beforePrepare_conflicts() throws Exception {
        Workflow quarter = seedQuarter();
        quarter.setStage(WorkflowStage.FINDINGS_REVIEW);
        workflowRepository.save(quarter);

        String body = mockMvc.perform(org.springframework.test.web.servlet.request
                        .MockMvcRequestBuilders.get("/api/v1/workflows/" + quarter.getId()
                                + "/tds/export").with(as(principal)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .status().isConflict())
                .andReturn().getResponse().getContentAsString();
        assertTrue(body.contains("Export is available from Prepare onward"), body);
    }

    @Test
    void mapAll_gatePasses_finalize_thenExport() throws Exception {
        Workflow quarter = seedQuarter();
        seedPaymentWorkflow("2025-04", "2025-04", true, 111);
        seedPaymentWorkflow("2025-05", "2025-05", true, 222);
        seedPaymentWorkflow("2025-06", "2025-06", true, 333);
        UUID aprilChallanId = challanIdsByPeriod.get("2025-04");
        UUID mayChallanId = challanIdsByPeriod.get("2025-05");
        UUID juneChallanId = challanIdsByPeriod.get("2025-06");
        TdsDeducteeEntry e1 = seedEntry(quarter);
        TdsDeducteeEntry e2 = seedEntry(quarter);
        TdsDeducteeEntry e3 = seedEntry(quarter);

        post("/api/v1/workflows/" + quarter.getId() + "/tds/entries/map",
                "{\"challanId\":\"" + aprilChallanId + "\",\"entryIds\":[\"" + e1.getId() + "\"]}");
        post("/api/v1/workflows/" + quarter.getId() + "/tds/entries/map",
                "{\"challanId\":\"" + mayChallanId + "\",\"entryIds\":[\"" + e2.getId() + "\"]}");
        post("/api/v1/workflows/" + quarter.getId() + "/tds/entries/map",
                "{\"challanId\":\"" + juneChallanId + "\",\"entryIds\":[\"" + e3.getId() + "\"]}");

        String gate = mockMvc.perform(org.springframework.test.web.servlet.request
                        .MockMvcRequestBuilders.get("/api/v1/workflows/" + quarter.getId()
                                + "/tds/gate").with(as(principal)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertFalse(gate.contains("\"passed\":false"), gate);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/workflows/" + quarter.getId() + "/finalize")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{}").with(as(principal)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .status().isOk());

        var result = mockMvc.perform(org.springframework.test.web.servlet.request
                        .MockMvcRequestBuilders.get("/api/v1/workflows/" + quarter.getId()
                                + "/tds/export").with(as(principal)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .status().isOk())
                .andReturn();
        assertTrue(result.getResponse().getContentType().startsWith("text/plain"),
                String.valueOf(result.getResponse().getContentType()));
        String body = result.getResponse().getContentAsString();
        assertTrue(body.startsWith("1^FH^"), body);
        assertTrue(result.getResponse().getHeader("Content-Disposition")
                .contains("26Q_CHEA12345B_FY2025_Q1.txt"));
    }

    @Test
    void export_newActQuarter_generatesForm140() throws Exception {
        Workflow quarter = seedQuarter(2026, "2026-Q1", LocalDate.of(2026, 4, 1));
        seedPaymentWorkflow(2026, "2026-04", "2026-04", true, 111);
        seedPaymentWorkflow(2026, "2026-05", "2026-05", true, 222);
        seedPaymentWorkflow(2026, "2026-06", "2026-06", true, 333);
        TdsDeducteeEntry e1 = seedEntry(quarter, "194J_B", LocalDate.of(2026, 5, 10));
        TdsDeducteeEntry e2 = seedEntry(quarter, "194C", LocalDate.of(2026, 6, 15));
        TdsDeducteeEntry e3 = seedEntry(quarter, "194J_B", LocalDate.of(2026, 6, 20));

        post("/api/v1/workflows/" + quarter.getId() + "/tds/entries/map",
                "{\"challanId\":\"" + challanIdsByPeriod.get("2026-04")
                        + "\",\"entryIds\":[\"" + e1.getId() + "\"]}");
        post("/api/v1/workflows/" + quarter.getId() + "/tds/entries/map",
                "{\"challanId\":\"" + challanIdsByPeriod.get("2026-05")
                        + "\",\"entryIds\":[\"" + e2.getId() + "\"]}");
        post("/api/v1/workflows/" + quarter.getId() + "/tds/entries/map",
                "{\"challanId\":\"" + challanIdsByPeriod.get("2026-06")
                        + "\",\"entryIds\":[\"" + e3.getId() + "\"]}");

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/workflows/" + quarter.getId() + "/finalize")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{}").with(as(principal)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .status().isOk());

        var result = mockMvc.perform(org.springframework.test.web.servlet.request
                        .MockMvcRequestBuilders.get("/api/v1/workflows/" + quarter.getId()
                                + "/tds/export").with(as(principal)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .status().isOk())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        assertTrue(body.contains("^BH^1^1^140^"), body);
        assertTrue(body.contains("^202627^") || body.contains("202627"), body);
        assertTrue(body.contains("1027"), body);
        assertTrue(result.getResponse().getHeader("Content-Disposition")
                .contains("140_CHEA12345B_FY2026_Q1.txt"));
    }

    @Test
    void finalize_unmapped_conflictsWithGateDetail() throws Exception {
        Workflow quarter = seedQuarter();
        seedPaymentWorkflow("2025-04", "2025-04", true, 111);
        seedPaymentWorkflow("2025-05", "2025-05", true, 222);
        seedPaymentWorkflow("2025-06", "2025-06", true, 333);
        seedEntry(quarter);

        String body = mockMvc.perform(org.springframework.test.web.servlet.request
                        .MockMvcRequestBuilders.post("/api/v1/workflows/" + quarter.getId()
                                + "/finalize")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{}").with(as(principal)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .status().isConflict())
                .andReturn().getResponse().getContentAsString();

        assertTrue(body.contains("entries unmapped"), body);
    }

    @Test
    void gate_followsPaymentMonthFindings_forExportAndFinalize() throws Exception {
        Workflow quarter = seedQuarter();
        Workflow april = seedPaymentWorkflow("2025-04", "2025-04", true, 111);
        seedPaymentWorkflow("2025-05", "2025-05", true, 222);
        seedPaymentWorkflow("2025-06", "2025-06", true, 333);
        TdsDeducteeEntry e1 = seedEntry(quarter);
        TdsDeducteeEntry e2 = seedEntry(quarter);
        TdsDeducteeEntry e3 = seedEntry(quarter);
        post("/api/v1/workflows/" + quarter.getId() + "/tds/entries/map",
                "{\"challanId\":\"" + challanIdsByPeriod.get("2025-04")
                        + "\",\"entryIds\":[\"" + e1.getId() + "\"]}");
        post("/api/v1/workflows/" + quarter.getId() + "/tds/entries/map",
                "{\"challanId\":\"" + challanIdsByPeriod.get("2025-05")
                        + "\",\"entryIds\":[\"" + e2.getId() + "\"]}");
        post("/api/v1/workflows/" + quarter.getId() + "/tds/entries/map",
                "{\"challanId\":\"" + challanIdsByPeriod.get("2025-06")
                        + "\",\"entryIds\":[\"" + e3.getId() + "\"]}");

        TdsFinding finding = new TdsFinding();
        finding.setOrganizationId(orgId);
        finding.setWorkflowId(april.getId());
        finding.setUploadJobId(UUID.randomUUID());
        finding.setFindingType(TdsFindingType.PAN_MISSING);
        finding.setSeverity(com.arktech.superaccountant.masters.models.FindingSeverity.ERROR);
        finding.setPartyLedgerName("ABC Contractors");
        finding.setMessage("PAN is missing");
        finding.setResolveStatus(ResolveStatus.ACKNOWLEDGED);
        finding = findingRepository.save(finding);

        String blockedExport = mockMvc.perform(org.springframework.test.web.servlet.request
                        .MockMvcRequestBuilders.get("/api/v1/workflows/" + quarter.getId()
                                + "/tds/export").with(as(principal)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .status().isConflict())
                .andReturn().getResponse().getContentAsString();
        assertTrue(blockedExport.contains("Payment months finalized"), blockedExport);
        assertTrue(blockedExport.contains("2025-04"), blockedExport);

        String blockedFinalize = mockMvc.perform(org.springframework.test.web.servlet.request
                        .MockMvcRequestBuilders.post("/api/v1/workflows/" + quarter.getId()
                                + "/finalize")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{}").with(as(principal)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .status().isConflict())
                .andReturn().getResponse().getContentAsString();
        assertTrue(blockedFinalize.contains("Payment months finalized"), blockedFinalize);
        assertTrue(blockedFinalize.contains("2025-04"), blockedFinalize);

        finding.setResolveStatus(ResolveStatus.APPROVED);
        findingRepository.save(finding);

        mockMvc.perform(org.springframework.test.web.servlet.request
                        .MockMvcRequestBuilders.get("/api/v1/workflows/" + quarter.getId()
                                + "/tds/export").with(as(principal)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .status().isOk());

        mockMvc.perform(org.springframework.test.web.servlet.request
                        .MockMvcRequestBuilders.post("/api/v1/workflows/" + quarter.getId()
                                + "/finalize")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{}").with(as(principal)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .status().isOk());
    }

    @Test
    void paymentFinalize_openHigherRateNotAppliedFinding_conflicts() throws Exception {
        Workflow payment = new Workflow();
        payment.setOrganizationId(orgId);
        payment.setType(WorkflowType.TDS_PAYMENT);
        payment.setPeriodStart(LocalDate.of(2025, 4, 1));
        payment.setPeriodEnd(LocalDate.of(2025, 4, 30));
        payment.setPeriodKey("2025-04");
        payment.setFinancialYear(2025);
        payment.setStage(WorkflowStage.PREPARE);
        payment.setCreatedBy("it");
        payment = workflowRepository.save(payment);

        TdsFinding finding = new TdsFinding();
        finding.setOrganizationId(orgId);
        finding.setWorkflowId(payment.getId());
        finding.setUploadJobId(UUID.randomUUID());
        finding.setFindingType(TdsFindingType.HIGHER_RATE_NOT_APPLIED);
        finding.setSeverity(com.arktech.superaccountant.masters.models.FindingSeverity.ERROR);
        finding.setPartyLedgerName("No PAN Party");
        finding.setMessage("Higher rate not applied");
        findingRepository.save(finding);

        String body = mockMvc.perform(org.springframework.test.web.servlet.request
                        .MockMvcRequestBuilders.post("/api/v1/workflows/" + payment.getId()
                                + "/finalize")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{}").with(as(principal)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .status().isConflict())
                .andReturn().getResponse().getContentAsString();

        assertTrue(body.contains("blocking findings must be resolved first"), body);
    }
}
