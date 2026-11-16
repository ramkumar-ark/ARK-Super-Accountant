package com.arktech.superaccountant.filedreturns.recon;

import com.arktech.superaccountant.daybook.models.DayBookUploadJob;
import com.arktech.superaccountant.daybook.models.ParsedVoucher;
import com.arktech.superaccountant.daybook.models.ParsedVoucherEntry;
import com.arktech.superaccountant.daybook.repository.DayBookUploadJobRepository;
import com.arktech.superaccountant.daybook.repository.ParsedVoucherRepository;
import com.arktech.superaccountant.filedreturns.FiledReturnSnapshot;
import com.arktech.superaccountant.filedreturns.FiledReturnSnapshotRepository;
import com.arktech.superaccountant.gst.mapping.TaxLedgerMapping;
import com.arktech.superaccountant.gst.mapping.TaxLedgerMappingRepository;
import com.arktech.superaccountant.masters.models.LedgerCategory;
import com.arktech.superaccountant.masters.models.UploadJob;
import com.arktech.superaccountant.masters.models.UploadJobStatus;
import com.arktech.superaccountant.masters.models.UploadLedgerSnapshot;
import com.arktech.superaccountant.masters.repository.OrganizationRepository;
import com.arktech.superaccountant.masters.repository.UploadJobRepository;
import com.arktech.superaccountant.masters.repository.UploadLedgerSnapshotRepository;
import com.arktech.superaccountant.masters.services.FindingOverrideMasterService;
import com.arktech.superaccountant.gst.mapping.TaxMappingTestData;
import com.arktech.superaccountant.masters.models.ResolveStatus;
import com.arktech.superaccountant.workflow.models.Workflow;
import com.arktech.superaccountant.workflow.models.WorkflowStage;
import com.arktech.superaccountant.workflow.models.WorkflowType;
import com.arktech.superaccountant.workflow.repository.BaselineImportRepository;
import com.arktech.superaccountant.workflow.repository.WorkflowRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Baseline reconciliation end to end (real Postgres via Testcontainers): importing a filed
 * GSTR-1 triggers recon against the books, an incomplete tax-ledger mapping refuses to recon,
 * approved differences survive an unchanged re-run and reopen when the filed data changes,
 * baseline confirm re-runs recon on the bound upload, and a row with no filed data reports
 * NOT_IMPORTED. Needs Docker.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = "JWT_SECRET=test-jwt-secret-must-be-at-least-32-characters-long")
class FiledReturnReconIT {

    private static final String ORG_GSTIN = "29ABCDE1234F1Z5";
    private static final String PARTY_A = "27AAAAA0000A1Z5";
    private static final String PARTY_B = "27BBBBB1111B1Z2";
    private static final String PARTY_C = "27CCCCC2222C1Z3";

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private WorkflowRepository workflowRepository;

    @Autowired
    private UploadJobRepository uploadJobRepository;

    @Autowired
    private UploadLedgerSnapshotRepository uploadLedgerSnapshotRepository;

    @Autowired
    private TaxLedgerMappingRepository taxLedgerMappingRepository;

    @Autowired
    private FindingOverrideMasterService mastersService;

    @Autowired
    private DayBookUploadJobRepository dayBookUploadJobRepository;

    @Autowired
    private ParsedVoucherRepository parsedVoucherRepository;

    @Autowired
    private FiledReturnSnapshotRepository snapshotRepository;

    @Autowired
    private BaselineImportRepository baselineImportRepository;

    @Autowired
    private FiledReturnDiscrepancyRepository discrepancyRepository;

    private static int counter = 0;

    private static String unique() {
        return String.valueOf(++counter);
    }

    private record Setup(UUID orgId, String accountantJwt, String operatorJwt,
                         UUID gstr1Row, UUID gstr3bRow, UUID daybookJobId) {
    }

    private String signupAndLogin(String username, String email, String role) throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"username":"%s","email":"%s","password":"pass1234","role":"%s"}
                    """.formatted(username, email, role)))
                .andExpect(status().isOk());
        return login(username);
    }

    private String login(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/signin")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"username":"%s","password":"pass1234"}
                    """.formatted(username)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("token").asText();
    }

    private UUID createOrganization(String jwt) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/organizations")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + jwt)
                .content("""
                    {"name":"Recon Org","financialYearStart":4}
                    """))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(
                objectMapper.readTree(result.getResponse().getContentAsString())
                        .get("id").asText());
    }

    private String selectOrg(String jwt, UUID orgId) throws Exception {
        MvcResult selected = mockMvc.perform(post("/api/organizations/{id}/select", orgId)
                .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(selected.getResponse().getContentAsString())
                .get("token").asText();
    }

    private String memberJwt(String ownerJwt, UUID orgId, String role,
                             String username, String email) throws Exception {
        MvcResult inviteResult = mockMvc.perform(post("/api/organizations/{id}/invites", orgId)
                .param("role", role)
                .header("Authorization", "Bearer " + ownerJwt))
                .andExpect(status().isOk())
                .andReturn();
        String token = objectMapper
                .readTree(inviteResult.getResponse().getContentAsString())
                .get("token").asText();
        signupAndLogin(username, email, role);
        mockMvc.perform(post("/api/organizations/invites/{token}/accept", token)
                .header("Authorization", "Bearer " + login(username)))
                .andExpect(status().isOk());
        return selectOrg(login(username), orgId);
    }

    private Setup setup() throws Exception {
        String ownerJwt = signupAndLogin("rconner" + unique(), "rc" + unique() + "@test.com", "owner");
        UUID orgId = createOrganization(ownerJwt);
        var org = organizationRepository.findById(orgId).orElseThrow();
        org.setGstin(ORG_GSTIN);
        org.setBooksStartDate(LocalDate.of(2026, 4, 1));
        organizationRepository.save(org);
        ownerJwt = selectOrg(ownerJwt, orgId);
        String accountantJwt = memberJwt(ownerJwt, orgId, "accountant",
                "rcacc" + unique(), "rcacc" + unique() + "@test.com");
        String operatorJwt = memberJwt(ownerJwt, orgId, "operator",
                "rcoper" + unique(), "rcoper" + unique() + "@test.com");

        UploadJob mastersJob = new UploadJob();
        mastersJob.setOrganizationId(orgId);
        mastersJob.setFileName("masters.json");
        mastersJob.setStatus(UploadJobStatus.COMPLETED);
        mastersJob.setCreatedAt(Instant.now().minusSeconds(7200));
        mastersJob = uploadJobRepository.save(mastersJob);
        gstLedger(mastersJob, orgId, "Output IGST");
        gstLedger(mastersJob, orgId, "Output CGST");
        gstLedger(mastersJob, orgId, "Output SGST");
        TaxMappingTestData.mapAll(orgId, mastersJob.getId(), taxLedgerMappingRepository, mastersService);

        DayBookUploadJob daybookJob = completedUpload(orgId,
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30));
        booksSale(daybookJob, orgId, "INV-7", LocalDate.of(2026, 4, 7), "Customer A", PARTY_A, "10000", "1800");
        booksSale(daybookJob, orgId, "INV-8", LocalDate.of(2026, 4, 9), "Customer B", PARTY_B, "5000", "900");
        booksSale(daybookJob, orgId, "INV-9", LocalDate.of(2026, 4, 12), "Customer C", PARTY_C, "3000", "540");

        Workflow gstr1Row = baselineRow(orgId, WorkflowType.GSTR1, "2026-04");
        Workflow gstr3bRow = baselineRow(orgId, WorkflowType.GSTR3B, "2026-04");
        return new Setup(orgId, accountantJwt, operatorJwt, gstr1Row.getId(), gstr3bRow.getId(),
                daybookJob.getId());
    }

    private void gstLedger(UploadJob mastersJob, UUID orgId, String name) {
        UploadLedgerSnapshot s = new UploadLedgerSnapshot();
        s.setUploadJobId(mastersJob.getId());
        s.setOrganizationId(orgId);
        s.setLedgerName(name);
        s.setCategory(LedgerCategory.GST);
        uploadLedgerSnapshotRepository.save(s);
    }

    private DayBookUploadJob completedUpload(UUID orgId, LocalDate from, LocalDate to) {
        DayBookUploadJob job = new DayBookUploadJob();
        job.setOrganizationId(orgId);
        job.setFileName("daybook.json");
        job.setStatus(UploadJobStatus.COMPLETED);
        job.setUploadedBy("it");
        job.setCoverageFrom(from);
        job.setCoverageTo(to);
        job.setTotalVouchersParsed(0);
        job.setCreatedAt(Instant.now());
        job.setCompletedAt(Instant.now());
        return dayBookUploadJobRepository.saveAndFlush(job);
    }

    private void booksSale(DayBookUploadJob job, UUID orgId, String number, LocalDate date,
                           String party, String partyGstin, String taxable, String igst) {
        ParsedVoucher v = new ParsedVoucher();
        v.setUploadJobId(job.getId());
        v.setOrganizationId(orgId);
        v.setVoucherTypeName("Sales");
        v.setVoucherDate(date);
        v.setVoucherNumber(number);
        v.setPartyLedgerName(party);
        v.setPartyGstin(partyGstin);
        v.setPartyGstRegistrationType("Regular");
        v.setTotalDebit(BigDecimal.ZERO);
        v.setTotalCredit(BigDecimal.ZERO);
        v.setEntries(new ArrayList<>());
        partyEntry(v, party, new BigDecimal(taxable).add(new BigDecimal(igst)));
        rateEntry(v, "Sales A/c", new BigDecimal("-" + taxable), "18", "Taxable");
        rateEntry(v, "Output IGST", new BigDecimal("-" + igst), null, null);
        parsedVoucherRepository.saveAndFlush(v);
    }

    private void partyEntry(ParsedVoucher v, String ledger, BigDecimal amount) {
        ParsedVoucherEntry e = new ParsedVoucherEntry();
        e.setParsedVoucher(v);
        e.setOrganizationId(v.getOrganizationId());
        e.setLedgerName(ledger);
        e.setAmount(amount);
        e.setDebit(amount.compareTo(BigDecimal.ZERO) > 0);
        e.setPartyLedger(true);
        v.getEntries().add(e);
    }

    private void rateEntry(ParsedVoucher v, String ledger, BigDecimal amount,
                           String rate, String taxability) {
        ParsedVoucherEntry e = new ParsedVoucherEntry();
        e.setParsedVoucher(v);
        e.setOrganizationId(v.getOrganizationId());
        e.setLedgerName(ledger);
        e.setAmount(amount);
        e.setDebit(amount.compareTo(BigDecimal.ZERO) > 0);
        e.setPartyLedger(false);
        if (rate != null) {
            e.setGstRate(new BigDecimal(rate));
            e.setGstTaxability(taxability);
        }
        v.getEntries().add(e);
    }

    private Workflow baselineRow(UUID orgId, WorkflowType type, String periodKey) {
        Workflow w = new Workflow();
        w.setOrganizationId(orgId);
        w.setType(type);
        w.setPeriodStart(LocalDate.of(2026, 4, 1));
        w.setPeriodEnd(LocalDate.of(2026, 4, 30));
        w.setPeriodKey(periodKey);
        w.setFinancialYear(2026);
        w.setStage(WorkflowStage.BASELINE);
        w.setActive(true);
        w.setCreatedBy("setup");
        return workflowRepository.saveAndFlush(w);
    }

    private String gstr1Json(int inv7Igst) {
        return """
            {
              "gstin": "%s",
              "fp": "042026",
              "b2b": [
                {
                  "ctin": "%s",
                  "inv": [
                    {"inum": "INV-7", "idt": "07-04-2026", "val": %d.00, "pos": "27", "inv_typ": "R",
                     "itms": [{"num": 1, "itm_det": {"rt": 18, "txval": 10000.00, "iamt": %d.00,
                               "camt": 0, "samt": 0, "csamt": 0}}]}
                  ]
                },
                {
                  "ctin": "%s",
                  "inv": [
                    {"inum": "INV-8", "idt": "09-04-2026", "val": 5900.00, "pos": "27", "inv_typ": "R",
                     "itms": [{"num": 1, "itm_det": {"rt": 18, "txval": 5000.00, "iamt": 900.00,
                               "camt": 0, "samt": 0, "csamt": 0}}]}
                  ]
                },
                {
                  "ctin": "%s",
                  "inv": [
                    {"inum": "INV-9", "idt": "12-04-2026", "val": 3540.00, "pos": "27", "inv_typ": "R",
                     "itms": [{"num": 1, "itm_det": {"rt": 18, "txval": 3000.00, "iamt": 540.00,
                               "camt": 0, "samt": 0, "csamt": 0}}]}
                  ]
                }
              ]
            }
            """.formatted(ORG_GSTIN, PARTY_A, 10000 + inv7Igst, inv7Igst, PARTY_B, PARTY_C);
    }

    private void importGstr1(Setup s, int inv7Igst) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "gstr1.json",
                "application/json", gstr1Json(inv7Igst).getBytes());
        mockMvc.perform(multipart("/api/v1/filed-returns/gstr1/json").file(file)
                .param("form", "GSTR1")
                .header("Authorization", "Bearer " + s.accountantJwt()))
                .andExpect(status().isCreated());
    }

    private MvcResult postReconcile(String jwt, UUID workflowId) throws Exception {
        return mockMvc.perform(post("/api/v1/filed-returns/baseline/{workflowId}/reconcile", workflowId)
                .header("Authorization", "Bearer " + jwt))
                .andReturn();
    }

    @Test
    void importTriggersReconAndWritesDiscrepancies() throws Exception {
        Setup s = setup();
        importGstr1(s, 1700);

        var findings = discrepancyRepository.findByWorkflowIdAndSupersededAtIsNull(s.gstr1Row());
        assertEquals(1, findings.size());
        var f = findings.get(0);
        assertEquals(DiscrepancyKind.VALUE_DIFFERENCE, f.getKind());
        assertTrue(f.isMaterial());
        assertEquals("INV-7", f.getDocNumber());
        assertEquals(0, new BigDecimal("1700").compareTo(f.getFiledIgst()));
        assertEquals(0, new BigDecimal("1800").compareTo(f.getBooksIgst()));
        assertEquals("GSTR1|B2B|INV7|" + PARTY_A, f.getFindingKey());

        var imports = baselineImportRepository.findAll().stream()
                .filter(bi -> s.gstr1Row().equals(bi.getWorkflowId())).toList();
        assertEquals(1, imports.size());
        assertEquals("PORTAL_JSON", imports.get(0).getSource());
        assertNotNull(imports.get(0).getFiledReturnRef());
        assertEquals(1, imports.get(0).getDiscrepancyCount());

        MvcResult findingsResult = mockMvc.perform(
                        get("/api/v1/workflows/{id}/findings", s.gstr1Row())
                                .header("Authorization", "Bearer " + s.accountantJwt()))
                .andExpect(status().isOk())
                .andReturn();
        var findingsRoot = objectMapper.readTree(findingsResult.getResponse().getContentAsString());
        var filedReturnFindings = new ArrayList<com.fasterxml.jackson.databind.JsonNode>();
        findingsRoot.get("findings").forEach(n -> {
            if ("FILED_RETURN".equals(n.get("source").asText())) {
                filedReturnFindings.add(n);
            }
        });
        assertEquals(1, filedReturnFindings.size());
        var view = filedReturnFindings.get(0);
        assertEquals("VALUE_DIFFERENCE", view.get("findingType").asText());
        assertEquals("WARNING", view.get("severity").asText());
        assertTrue(view.get("blocking").asBoolean() == false);
        assertTrue(view.get("message").asText().contains("filed IGST 1700.00 vs books IGST 1800.00"));
        assertEquals("T 10000.00 | I 1700.00 | C 0.00 | S 0.00 | Cess 0.00",
                view.get("expectedValue").asText());
        assertEquals("T 10000.00 | I 1800.00 | C 0.00 | S 0.00 | Cess 0.00",
                view.get("actualValue").asText());
        assertEquals("INV-7", view.get("voucherNumber").asText());
        assertEquals(PARTY_A, view.get("ledgerName").asText());

        MvcResult result = postReconcile(s.accountantJwt(), s.gstr1Row());
        assertEquals(200, result.getResponse().getStatus());
        var body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals("RECONCILED", body.get("status").asText());
        assertEquals(1, body.get("material").asInt());
        assertEquals(0, body.get("info").asInt());
    }

    @Test
    void refusesWhileMappingIncomplete() throws Exception {
        Setup s = setup();
        importGstr1(s, 1700);
        assertEquals(1, discrepancyRepository.findByWorkflowIdAndSupersededAtIsNull(s.gstr1Row()).size());

        TaxLedgerMapping victim = taxLedgerMappingRepository.findAll().stream()
                .filter(m -> s.orgId().equals(m.getOrganizationId())).findFirst().orElseThrow();
        taxLedgerMappingRepository.delete(victim);
        taxLedgerMappingRepository.flush();

        MvcResult result = postReconcile(s.accountantJwt(), s.gstr1Row());
        var body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals("MAPPING_INCOMPLETE", body.get("status").asText());
        assertEquals(1, discrepancyRepository.findByWorkflowIdAndSupersededAtIsNull(s.gstr1Row()).size());
        assertEquals(1, baselineImportRepository.findAll().stream()
                .filter(bi -> s.gstr1Row().equals(bi.getWorkflowId())).count());
    }

    @Test
    void approvedDifferenceSurvivesUnchangedRerunAndReopensOnChange() throws Exception {
        Setup s = setup();
        importGstr1(s, 1700);
        var first = discrepancyRepository.findByWorkflowIdAndSupersededAtIsNull(s.gstr1Row());
        assertEquals(1, first.size());
        var approved = first.get(0);
        approved.setResolveStatus(ResolveStatus.APPROVED);
        approved.setReviewedBy("acct");
        discrepancyRepository.saveAndFlush(approved);

        MvcResult rerun = postReconcile(s.accountantJwt(), s.gstr1Row());
        assertEquals("RECONCILED", objectMapper
                .readTree(rerun.getResponse().getContentAsString()).get("status").asText());

        var carried = discrepancyRepository.findByWorkflowIdAndSupersededAtIsNull(s.gstr1Row());
        assertEquals(1, carried.size());
        assertEquals(approved.getFindingKey(), carried.get(0).getFindingKey());
        assertEquals(ResolveStatus.APPROVED, carried.get(0).getResolveStatus());
        assertEquals(approved.getId(), carried.get(0).getCarriedFromFindingId());
        assertNotNull(discrepancyRepository.findById(approved.getId()).orElseThrow().getSupersededAt());

        importGstr1(s, 1600);
        var reopened = discrepancyRepository.findByWorkflowIdAndSupersededAtIsNull(s.gstr1Row());
        assertEquals(1, reopened.size());
        assertEquals(approved.getFindingKey(), reopened.get(0).getFindingKey());
        assertEquals(ResolveStatus.OPEN, reopened.get(0).getResolveStatus());
        assertEquals(0, new BigDecimal("1600").compareTo(reopened.get(0).getFiledIgst()));
    }

    @Test
    void confirmTriggersRecon() throws Exception {
        Setup s = setup();

        mockMvc.perform(put("/api/v1/compliance/responsibility-start")
                .header("Authorization", "Bearer " + s.accountantJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"TDS_PAYMENT\",\"firstPeriodStart\":\"2026-05-01\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/compliance/responsibility-start")
                .header("Authorization", "Bearer " + s.accountantJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"GSTR1\",\"firstPeriodStart\":\"2026-05-01\"}"))
                .andExpect(status().isOk());

        UUID gstr1RowId = workflowRepository.findAll().stream()
                .filter(w -> s.orgId().equals(w.getOrganizationId()))
                .filter(w -> w.getType() == WorkflowType.GSTR1 && w.isActive())
                .findFirst().orElseThrow().getId();

        importGstr1(s, 1700);
        var before = discrepancyRepository.findByWorkflowIdAndSupersededAtIsNull(gstr1RowId);
        assertEquals(1, before.size());
        Instant createdAtBeforeConfirm = before.get(0).getCreatedAt();
        Instant importedAtBefore = baselineImportRepository.findAll().stream()
                .filter(bi -> gstr1RowId.equals(bi.getWorkflowId())).findFirst().orElseThrow()
                .getImportedAt();

        mockMvc.perform(post("/api/v1/compliance/baseline/confirm")
                .header("Authorization", "Bearer " + s.accountantJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isOk());

        var after = discrepancyRepository.findByWorkflowIdAndSupersededAtIsNull(gstr1RowId);
        assertEquals(1, after.size());
        assertEquals(before.get(0).getFindingKey(), after.get(0).getFindingKey());
        assertNotEquals(createdAtBeforeConfirm, after.get(0).getCreatedAt());
        assertNotNull(discrepancyRepository.findById(before.get(0).getId())
                .orElseThrow().getSupersededAt());
        assertEquals(importedAtBefore, baselineImportRepository.findAll().stream()
                .filter(bi -> gstr1RowId.equals(bi.getWorkflowId())).findFirst().orElseThrow()
                .getImportedAt());
    }

    @Test
    void notImportedRowReportsNotImportedAndUnknownRowsGive404() throws Exception {
        Setup s = setup();

        MvcResult result = postReconcile(s.accountantJwt(), s.gstr3bRow());
        var body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals("NOT_IMPORTED", body.get("status").asText());
        assertTrue(baselineImportRepository.findAll().stream()
                .noneMatch(bi -> s.gstr3bRow().equals(bi.getWorkflowId())));

        assertEquals(404, postReconcile(s.accountantJwt(), UUID.randomUUID())
                .getResponse().getStatus());
        Workflow tds = baselineRow(s.orgId(), WorkflowType.TDS_PAYMENT, "2026-04");
        assertEquals(404, postReconcile(s.accountantJwt(), tds.getId())
                .getResponse().getStatus());
    }
}
