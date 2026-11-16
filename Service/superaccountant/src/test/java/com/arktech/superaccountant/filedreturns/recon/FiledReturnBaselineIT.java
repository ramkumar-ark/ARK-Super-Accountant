package com.arktech.superaccountant.filedreturns.recon;

import com.arktech.superaccountant.daybook.models.DayBookUploadJob;
import com.arktech.superaccountant.daybook.models.ParsedVoucher;
import com.arktech.superaccountant.daybook.models.ParsedVoucherEntry;
import com.arktech.superaccountant.daybook.repository.DayBookUploadJobRepository;
import com.arktech.superaccountant.daybook.repository.ParsedVoucherRepository;
import com.arktech.superaccountant.gst.mapping.TaxLedgerMappingRepository;
import com.arktech.superaccountant.gst.mapping.TaxMappingTestData;
import com.arktech.superaccountant.masters.models.LedgerCategory;
import com.arktech.superaccountant.masters.models.UploadJob;
import com.arktech.superaccountant.masters.models.UploadJobStatus;
import com.arktech.superaccountant.masters.models.UploadLedgerSnapshot;
import com.arktech.superaccountant.masters.repository.OrganizationRepository;
import com.arktech.superaccountant.masters.repository.UploadJobRepository;
import com.arktech.superaccountant.masters.repository.UploadLedgerSnapshotRepository;
import com.arktech.superaccountant.masters.services.FindingOverrideMasterService;
import com.arktech.superaccountant.workflow.models.Workflow;
import com.arktech.superaccountant.workflow.models.WorkflowStage;
import com.arktech.superaccountant.workflow.models.WorkflowType;
import com.arktech.superaccountant.workflow.repository.WorkflowRepository;
import com.fasterxml.jackson.databind.JsonNode;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The baseline overview API and the live discrepancy count (real Postgres via Testcontainers):
 * the overview lists reconciliation status and per-head variance per baseline row, the status
 * endpoint's discrepancyCount is live and informational, and explaining a finding lowers it.
 * Needs Docker.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = "JWT_SECRET=test-jwt-secret-must-be-at-least-32-characters-long")
class FiledReturnBaselineIT {

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
                    {"name":"Baseline View Org","financialYearStart":4}
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
        String ownerJwt = signupAndLogin("bviewer" + unique(), "bv" + unique() + "@test.com", "owner");
        UUID orgId = createOrganization(ownerJwt);
        var org = organizationRepository.findById(orgId).orElseThrow();
        org.setGstin(ORG_GSTIN);
        org.setBooksStartDate(LocalDate.of(2026, 4, 1));
        organizationRepository.save(org);
        ownerJwt = selectOrg(ownerJwt, orgId);
        String accountantJwt = memberJwt(ownerJwt, orgId, "accountant",
                "bvacc" + unique(), "bvacc" + unique() + "@test.com");
        String operatorJwt = memberJwt(ownerJwt, orgId, "operator",
                "bvoper" + unique(), "bvoper" + unique() + "@test.com");

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

    private JsonNode baselineOverview(Setup s) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/filed-returns/baseline")
                .header("Authorization", "Bearer " + s.accountantJwt()))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode complianceStatus(Setup s) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/compliance/baseline")
                .header("Authorization", "Bearer " + s.accountantJwt()))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode filedReturnFinding(Setup s, UUID workflowId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/workflows/{id}/findings", workflowId)
                .header("Authorization", "Bearer " + s.accountantJwt()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        for (JsonNode f : root.get("findings")) {
            if ("FILED_RETURN".equals(f.get("source").asText())) {
                return f;
            }
        }
        return null;
    }

    private MvcResult findingAction(Setup s, UUID workflowId, UUID findingId,
                                    String actor, String body) throws Exception {
        return mockMvc.perform(post("/api/v1/workflows/{id}/findings/{fid}/action", workflowId, findingId)
                .header("Authorization", "Bearer " + actor)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andReturn();
    }

    @Test
    void overviewListsRowsWithStatusAndCounts() throws Exception {
        Setup s = setup();
        importGstr1(s, 1700);

        JsonNode rows = baselineOverview(s);
        assertEquals(2, rows.size());
        JsonNode gstr1 = rows.get(0);
        assertEquals("GSTR1", gstr1.get("type").asText());
        assertEquals("2026-04", gstr1.get("periodKey").asText());
        assertTrue(gstr1.get("confirmed").asBoolean() == false);
        assertEquals("RECONCILED", gstr1.get("status").asText());
        assertEquals(1, gstr1.get("materialOpen").asInt());
        assertEquals(0, gstr1.get("materialExplained").asInt());
        assertEquals(0, gstr1.get("info").asInt());
        assertEquals(0, gstr1.get("snapshots").size() == 1 ? 0 : 1);
        JsonNode igst = gstr1.get("variance").get("IGST");
        assertEquals(0, igst.get("filed").decimalValue().compareTo(new BigDecimal("1700")));
        assertEquals(0, igst.get("books").decimalValue().compareTo(new BigDecimal("1800")));
        assertEquals(0, igst.get("diff").decimalValue().compareTo(new BigDecimal("-100")));

        JsonNode gstr3b = rows.get(1);
        assertEquals("GSTR3B", gstr3b.get("type").asText());
        assertEquals("NOT_IMPORTED", gstr3b.get("status").asText());
        assertEquals(0, gstr3b.get("materialOpen").asInt());
        JsonNode g3bIgst = gstr3b.get("variance").get("IGST");
        assertEquals(0, g3bIgst.get("filed").decimalValue().compareTo(BigDecimal.ZERO));
        assertEquals(0, g3bIgst.get("books").decimalValue().compareTo(BigDecimal.ZERO));
        assertEquals(0, g3bIgst.get("diff").decimalValue().compareTo(BigDecimal.ZERO));
    }

    @Test
    void statusDiscrepancyCountIsLiveAndInformational() throws Exception {
        Setup s = setup();
        importGstr1(s, 1700);

        JsonNode before = complianceStatus(s);
        assertEquals(1, before.get("discrepancyCount").asInt());
        assertEquals("available", before.get("importedFiledData").asText());

        // Confirm succeeds while a material difference is open: informational only.
        mockMvc.perform(post("/api/v1/compliance/baseline/confirm")
                .header("Authorization", "Bearer " + s.accountantJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isOk());
        assertEquals(1, complianceStatus(s).get("discrepancyCount").asInt());

        JsonNode finding = filedReturnFinding(s, s.gstr1Row());
        assertTrue(finding != null, "FILED_RETURN finding should be listed");
        UUID findingId = UUID.fromString(finding.get("id").asText());
        assertEquals(400, findingAction(s, s.gstr1Row(), findingId, s.operatorJwt(),
                "{\"action\":\"ACKNOWLEDGE\"}").getResponse().getStatus());
        assertEquals(200, findingAction(s, s.gstr1Row(), findingId, s.operatorJwt(),
                "{\"action\":\"ACKNOWLEDGE\",\"note\":\"Checked with client\"}")
                .getResponse().getStatus());
        assertEquals(200, findingAction(s, s.gstr1Row(), findingId, s.accountantJwt(),
                "{\"action\":\"APPROVE\"}").getResponse().getStatus());

        assertEquals(0, complianceStatus(s).get("discrepancyCount").asInt());
    }

    @Test
    void countIsNullWhenNothingImported() throws Exception {
        Setup s = setup();

        JsonNode status = complianceStatus(s);
        assertTrue(status.get("discrepancyCount").isNull(), "count must be null, not 0");
        assertEquals("not available", status.get("importedFiledData").asText());
    }

    @Test
    void countIgnoresInactiveBaselineRows() throws Exception {
        Setup s = setup();
        importGstr1(s, 1700);
        assertEquals(1, complianceStatus(s).get("discrepancyCount").asInt());

        Workflow row = workflowRepository.findById(s.gstr1Row()).orElseThrow();
        row.setActive(false);
        workflowRepository.saveAndFlush(row);

        assertTrue(complianceStatus(s).get("discrepancyCount").isNull());
        assertTrue(filedReturnFinding(s, s.gstr1Row()) == null,
                "the source must not list findings on inactive rows");

        JsonNode rows = baselineOverview(s);
        assertEquals(1, rows.size());
        assertEquals("GSTR3B", rows.get(0).get("type").asText());
    }

    @Test
    void explainingNeedsANote() throws Exception {
        Setup s = setup();
        importGstr1(s, 1700);

        JsonNode finding = filedReturnFinding(s, s.gstr1Row());
        UUID findingId = UUID.fromString(finding.get("id").asText());
        MvcResult result = findingAction(s, s.gstr1Row(), findingId, s.operatorJwt(),
                "{\"action\":\"ACKNOWLEDGE\"}");
        assertEquals(400, result.getResponse().getStatus());
        assertFalse(filedReturnFinding(s, s.gstr1Row()).get("resolveStatus").asText().isBlank());
        assertEquals("OPEN", filedReturnFinding(s, s.gstr1Row()).get("resolveStatus").asText());
    }
}
