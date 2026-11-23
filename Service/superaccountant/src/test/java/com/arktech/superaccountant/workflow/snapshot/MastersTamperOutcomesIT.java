package com.arktech.superaccountant.workflow.snapshot;

import com.arktech.superaccountant.daybook.models.DayBookUploadJob;
import com.arktech.superaccountant.daybook.models.ParsedVoucher;
import com.arktech.superaccountant.daybook.models.ParsedVoucherEntry;
import com.arktech.superaccountant.daybook.repository.ParsedVoucherRepository;
import com.arktech.superaccountant.masters.models.Organization;
import com.arktech.superaccountant.masters.models.ResolveStatus;
import com.arktech.superaccountant.masters.models.UploadJobStatus;
import com.arktech.superaccountant.masters.repository.OrganizationRepository;
import com.arktech.superaccountant.masters.repository.UploadJobRepository;
import com.arktech.superaccountant.masters.repository.UploadLedgerSnapshotRepository;
import com.arktech.superaccountant.workflow.models.Workflow;
import com.arktech.superaccountant.workflow.models.WorkflowEventKind;
import com.arktech.superaccountant.workflow.models.WorkflowStage;
import com.arktech.superaccountant.workflow.models.WorkflowType;
import com.arktech.superaccountant.workflow.repository.WorkflowEventRepository;
import com.arktech.superaccountant.workflow.repository.WorkflowRepository;
import com.arktech.superaccountant.workflow.seam.SnapshotService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Masters tamper accept outcomes over HTTP. Not @Transactional: the masters
 * listener commits on its own, so state must really be committed between steps.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = "JWT_SECRET=test-jwt-secret-must-be-at-least-32-characters-long")
class MastersTamperOutcomesIT {

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
    @Autowired
    private OrganizationRepository organizationRepository;
    @Autowired
    private WorkflowRepository workflowRepository;
    @Autowired
    private SnapshotService snapshotService;
    @Autowired
    private TamperChangeRepository tamperChangeRepository;
    @Autowired
    private TamperFindingRepository tamperFindingRepository;
    @Autowired
    private FrozenMasterAttributeRepository frozenMasterAttributeRepository;
    @Autowired
    private AmendmentFollowUpRepository amendmentFollowUpRepository;
    @Autowired
    private WorkflowEventRepository workflowEventRepository;
    @Autowired
    private UploadJobRepository uploadJobRepository;
    @Autowired
    private UploadLedgerSnapshotRepository uploadLedgerSnapshotRepository;
    @Autowired
    private com.arktech.superaccountant.daybook.repository.DayBookUploadJobRepository dayBookUploadJobRepository;
    @Autowired
    private ParsedVoucherRepository parsedVoucherRepository;
    @Autowired
    private FrozenVoucherRepository frozenVoucherRepository;
    @Autowired
    private TamperComparisonService tamperComparisonService;

    private final ObjectMapper json = new ObjectMapper();

    private UUID orgId;
    private String ownerToken;
    private String operatorToken;
    private String accountantToken;
    private Workflow frozenGstr1Apr;
    private Workflow openGstr1May;

    private static final String OLD_GSTIN = "29ABCDE1234F1Z5";
    private static final String NEW_GSTIN = "29ABCDE1234F1Z6";
    private static final String LEDGER_KEY = "acme traders";

    /** Freezes GSTR1 2025-04 with the old GSTIN, opens GSTR1 2025-05, then uploads the changed masters. */
    private void seedModifiedFinding() throws Exception {
        ownerToken = setupOrg();
        accountantToken = memberJwt("accountant");
        operatorToken = memberJwt("operator");

        mastersUpload(accountantToken, mastersFile(OLD_GSTIN, true));
        frozenGstr1Apr = freezeGstr1April();
        UUID gstr1May = startWorkflow(accountantToken, "GSTR1", "2025-05");
        openGstr1May = workflowRepository.findById(gstr1May).orElseThrow();

        mastersUpload(accountantToken, mastersFile(NEW_GSTIN, true));
        awaitMasterChanges();
    }

    private TamperFinding masterFinding() {
        return tamperFindingRepository.findByOrganizationId(orgId).stream()
                .filter(f -> f.getFindingKey().startsWith("MASTER_"))
                .filter(f -> f.getFindingKey().endsWith("|gstin"))
                .findFirst().orElseThrow();
    }

    private List<TamperFinding> masterFindings() {
        return tamperFindingRepository.findByOrganizationId(orgId).stream()
                .filter(f -> f.getFindingKey().startsWith("MASTER_")).toList();
    }

    private String actStatus(UUID workflowId, String action, String value, String note) throws Exception {
        return actStatusOn(workflowId, masterFinding(),
                "APPROVE".equals(action) ? accountantToken : operatorToken, action, value, note);
    }

    private String actStatusOn(UUID workflowId, TamperFinding finding, String token, String action,
                               String value, String note) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/workflows/" + workflowId + "/findings/"
                        + finding.getId() + "/action")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(body(action, value, note)))
                .andReturn();
        return String.valueOf(result.getResponse().getStatus());
    }

    private String body(String action, String value, String note) {
        StringBuilder sb = new StringBuilder("{\"action\":\"").append(action).append("\"");
        if (value != null) {
            sb.append(",\"value\":\"").append(value).append("\"");
        }
        if (note != null) {
            sb.append(",\"note\":\"").append(note).append("\"");
        }
        return sb.append("}").toString();
    }

    @Test
    void correction_accountant_rewritesFrozenValue_andRecordsFollowUps() throws Exception {
        seedModifiedFinding();

        assertThat(actStatusOn(openGstr1May.getId(), masterFinding(), accountantToken,
                "ACKNOWLEDGE", null, "accept as correction")).isEqualTo("200");

        TamperFinding finding = masterFinding();
        assertThat(finding.getResolveStatus()).isEqualTo(ResolveStatus.APPROVED);

        List<FrozenMasterAttribute> live = frozenMasterAttributeRepository
                .findByOrganizationIdAndLedgerKeyAndAttributeAndWorkflowTypeAndReleasedAtIsNull(
                        orgId, LEDGER_KEY, "gstin", WorkflowType.GSTR1);
        assertThat(live).hasSize(1);
        assertThat(live.get(0).getValue()).isEqualTo(NEW_GSTIN);
        assertThat(live.get(0).getPeriodEnd()).isEqualTo(LocalDate.of(2025, 4, 30));

        List<AmendmentFollowUp> followUps = amendmentFollowUpRepository
                .findByWorkflowIdOrderByCreatedAt(frozenGstr1Apr.getId());
        assertThat(followUps).hasSize(1);
        assertThat(followUps.get(0).getStatus()).isEqualTo(AmendmentStatus.OPEN);
        assertThat(followUps.get(0).getVoucherKey()).isEqualTo("MASTER:" + LEDGER_KEY + ":gstin");
        assertThat(followUps.get(0).getSummary()).isEqualTo(finding.getMessage());

        List<com.arktech.superaccountant.workflow.models.WorkflowEvent> events = workflowEventRepository
                .findAllByWorkflowIdOrderByOccurredAtAsc(frozenGstr1Apr.getId()).stream()
                .filter(e -> e.getKind() == WorkflowEventKind.AMENDMENT_RECORDED).toList();
        assertThat(events).hasSize(1);

        mockMvc.perform(get("/api/v1/workflows/" + openGstr1May.getId() + "/findings")
                        .header("Authorization", "Bearer " + accountantToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blockingCount").value(0));
    }

    @Test
    void correction_removed_releasesRows() throws Exception {
        seedModifiedFinding();

        mastersUpload(accountantToken, mastersFile(OLD_GSTIN, false));
        awaitMasterChanges();
        TamperFinding removed = masterFindings().stream()
                .filter(f -> f.getFindingKey().equals("MASTER_REMOVED|" + LEDGER_KEY + "|gstin"))
                .findFirst().orElseThrow();

        assertThat(actStatusOn(openGstr1May.getId(), removed, accountantToken,
                "ACKNOWLEDGE", null, "accept removal")).isEqualTo("200");

        List<FrozenMasterAttribute> live = frozenMasterAttributeRepository
                .findByOrganizationIdAndLedgerKeyAndAttributeAndWorkflowTypeAndReleasedAtIsNull(
                        orgId, LEDGER_KEY, "gstin", WorkflowType.GSTR1);
        assertThat(live).isEmpty();
        List<AmendmentFollowUp> followUps = amendmentFollowUpRepository
                .findByWorkflowIdOrderByCreatedAt(frozenGstr1Apr.getId());
        assertThat(followUps).hasSize(1);
    }

    @Test
    void validFrom_operatorSubmits_accountantApproves_keepsOldRow() throws Exception {
        seedModifiedFinding();

        assertThat(actStatus(openGstr1May.getId(), "ACKNOWLEDGE", "VALID_FROM:2025-05-01",
                "effective from May")).isEqualTo("200");
        assertThat(masterFinding().getResolveStatus()).isEqualTo(ResolveStatus.ACKNOWLEDGED);

        assertThat(actStatus(openGstr1May.getId(), "APPROVE", null, "approved")).isEqualTo("200");

        TamperFinding finding = masterFinding();
        assertThat(finding.getResolveStatus()).isEqualTo(ResolveStatus.APPROVED);

        List<FrozenMasterAttribute> live = frozenMasterAttributeRepository
                .findByOrganizationIdAndLedgerKeyAndAttributeAndWorkflowTypeAndReleasedAtIsNull(
                        orgId, LEDGER_KEY, "gstin", WorkflowType.GSTR1);
        assertThat(live).hasSize(2);
        FrozenMasterAttribute oldRow = live.stream()
                .filter(r -> OLD_GSTIN.equals(r.getValue())).findFirst().orElseThrow();
        assertThat(oldRow.getPeriodEnd()).isEqualTo(LocalDate.of(2025, 4, 30));
        assertThat(oldRow.getValidFrom()).isNull();
        FrozenMasterAttribute newRow = live.stream()
                .filter(r -> NEW_GSTIN.equals(r.getValue())).findFirst().orElseThrow();
        assertThat(newRow.getValidFrom()).isEqualTo(LocalDate.of(2025, 5, 1));
        assertThat(newRow.getPeriodEnd()).isNull();

        assertThat(amendmentFollowUpRepository
                .findByWorkflowIdOrderByCreatedAt(frozenGstr1Apr.getId())).isEmpty();
    }

    @Test
    void validFrom_thenNextUploadWithNewValue_raisesNothing() throws Exception {
        seedModifiedFinding();

        assertThat(actStatus(openGstr1May.getId(), "ACKNOWLEDGE", "VALID_FROM:2025-05-01",
                "effective from May")).isEqualTo("200");
        assertThat(actStatus(openGstr1May.getId(), "APPROVE", null, "approved")).isEqualTo("200");

        int before = tamperChangeRepository.findByOrganizationId(orgId).size();
        mastersUpload(accountantToken, mastersFile(NEW_GSTIN, true));
        Thread.sleep(2000);

        assertThat(tamperChangeRepository.findByOrganizationId(orgId).size()).isEqualTo(before);
        assertThat(masterFindings().stream()
                .filter(f -> f.getResolveStatus() == ResolveStatus.OPEN).count()).isZero();
    }

    @Test
    void validFrom_removedLedger_thenNextUploadStillWithoutIt_raisesNothing() throws Exception {
        seedModifiedFinding();
        mastersUpload(accountantToken, mastersFile(OLD_GSTIN, false));
        awaitMasterChanges();
        TamperFinding removed = masterFindings().stream()
                .filter(f -> f.getFindingKey().equals("MASTER_REMOVED|" + LEDGER_KEY + "|gstin"))
                .findFirst().orElseThrow();

        assertThat(actStatusOn(openGstr1May.getId(), removed, operatorToken,
                "ACKNOWLEDGE", "VALID_FROM:2025-05-01", "removed from May")).isEqualTo("200");
        assertThat(actStatusOn(openGstr1May.getId(), removed, accountantToken,
                "APPROVE", null, "approved")).isEqualTo("200");

        long before = tamperChangeRepository.findByOrganizationId(orgId).stream()
                .filter(c -> "gstin".equals(c.getAttributeName())).count();
        mastersUpload(accountantToken, mastersFile(OLD_GSTIN, false));
        Thread.sleep(2000);

        assertThat(tamperChangeRepository.findByOrganizationId(orgId).stream()
                .filter(c -> "gstin".equals(c.getAttributeName())).count()).isEqualTo(before);
    }

    @Test
    void validFrom_notAfterFrozenPeriodEnd_400() throws Exception {
        seedModifiedFinding();
        MvcResult result = mockMvc.perform(post("/api/v1/workflows/" + openGstr1May.getId() + "/findings/"
                        + masterFinding().getId() + "/action")
                        .header("Authorization", "Bearer " + operatorToken)
                        .contentType("application/json")
                        .content(body("ACKNOWLEDGE", "VALID_FROM:2025-04-30", "note")))
                .andExpect(status().isBadRequest()).andReturn();
        assertThat(result.getResponse().getContentAsString())
                .contains("Valid-from date must be after 2025-04-30");
    }

    @Test
    void validFrom_badDate_400() throws Exception {
        seedModifiedFinding();
        MvcResult result = mockMvc.perform(post("/api/v1/workflows/" + openGstr1May.getId() + "/findings/"
                        + masterFinding().getId() + "/action")
                        .header("Authorization", "Bearer " + operatorToken)
                        .contentType("application/json")
                        .content(body("ACKNOWLEDGE", "VALID_FROM:not-a-date", "note")))
                .andExpect(status().isBadRequest()).andReturn();
        assertThat(result.getResponse().getContentAsString()).contains("Valid-from date must be yyyy-MM-dd");
    }

    @Test
    void unknownProposal_400() throws Exception {
        seedModifiedFinding();
        MvcResult result = mockMvc.perform(post("/api/v1/workflows/" + openGstr1May.getId() + "/findings/"
                        + masterFinding().getId() + "/action")
                        .header("Authorization", "Bearer " + operatorToken)
                        .contentType("application/json")
                        .content(body("ACKNOWLEDGE", "rewrite it please", "note")))
                .andExpect(status().isBadRequest()).andReturn();
        assertThat(result.getResponse().getContentAsString())
                .contains("Unknown proposal for a masters tamper finding.");
    }

    @Test
    void correction_requiresNote_400() throws Exception {
        seedModifiedFinding();
        assertThat(actStatus(openGstr1May.getId(), "ACKNOWLEDGE", null, null)).isEqualTo("400");
    }

    @Test
    void voucherTamperAccept_unchanged() throws Exception {
        seedModifiedFinding();

        // Regression: S's day-book voucher tamper accept still rewrites the frozen voucher.
        Organization org = organizationRepository.findById(orgId).orElseThrow();
        DayBookUploadJob job1 = completedJob(org.getId(), LocalDate.of(2025, 4, 1), LocalDate.of(2025, 5, 31));
        seedVoucher(org.getId(), job1.getId(), "g-1", "Sales", "SB/001", LocalDate.of(2025, 4, 15), "h1",
                entry("Sales Account", "100"));
        Workflow gstr3bApr = workflow(org.getId(), WorkflowType.GSTR3B, LocalDate.of(2025, 4, 1),
                LocalDate.of(2025, 4, 30), WorkflowStage.STARTED, null);
        accountantToken = memberJwt("accountant");

        DayBookUploadJob job2 = completedJob(org.getId(), LocalDate.of(2025, 4, 1), LocalDate.of(2025, 5, 31));
        seedVoucher(org.getId(), job2.getId(), "g-1", "Sales", "SB/001", LocalDate.of(2025, 4, 15), "h2",
                entry("Sales Account", "200"));
        tamperComparisonService.compare(job2.getId());

        TamperFinding voucherFinding = tamperFindingRepository.findByOrganizationId(orgId).stream()
                .filter(f -> !f.getFindingKey().startsWith("MASTER_")).findFirst().orElseThrow();
        mockMvc.perform(post("/api/v1/workflows/" + gstr3bApr.getId() + "/findings/"
                        + voucherFinding.getId() + "/action")
                        .header("Authorization", "Bearer " + accountantToken)
                        .contentType("application/json")
                        .content("{\"action\":\"ACKNOWLEDGE\",\"note\":\"accept as correction\"}"))
                .andExpect(status().isOk());

        List<FrozenVoucher> live = frozenVoucherRepository.findByOrganizationIdAndReleasedAtIsNull(orgId);
        assertThat(live).hasSize(1);
        assertThat(live.get(0).getContentHash()).isEqualTo("h2");
    }

    // -- helpers -------------------------------------------------------------

    private void awaitMasterChanges() throws Exception {
        awaitMasterChangeCount(0);
    }

    private void awaitMasterChangeCount(int before) throws Exception {
        long deadline = System.currentTimeMillis() + 10_000;
        int seen = tamperChangeRepository.findByOrganizationId(orgId).size();
        while (System.currentTimeMillis() < deadline && seen <= before) {
            seen = tamperChangeRepository.findByOrganizationId(orgId).size();
            Thread.sleep(200);
        }
        assertThat(seen).as("masters tamper changes never appeared").isGreaterThan(before);
    }

    private void mastersUpload(String token, byte[] content) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "masters.json", "application/json", content);
        mockMvc.perform(multipart("/api/v1/uploads").file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated());
    }

    private byte[] mastersFile(String gstin, boolean includeAcme) throws Exception {
        ObjectNode root = json.createObjectNode();
        var entries = root.putArray("tallymessage");
        ObjectNode salesGroup = entries.addObject();
        salesGroup.putObject("group").put("name", "Sales Accounts").put("parent", "");
        ObjectNode creditorGroup = entries.addObject();
        creditorGroup.putObject("group").put("name", "Sundry Creditors").put("parent", "");
        if (includeAcme) {
            ObjectNode acme = entries.addObject();
            acme.putObject("ledger").put("name", "Acme Traders")
                    .put("guid", "guid-acme-1")
                    .put("parent", "Sales Accounts")
                    .put("gstin", gstin)
                    .put("gstregistrationtype", "REGULAR")
                    .put("taxtype", "GST")
                    .put("gstapplicabilitytype", "TAXABLE")
                    .put("hsnsaccode", "1234");
        }
        ObjectNode contractor = entries.addObject();
        contractor.putObject("ledger").put("name", "Contractor A")
                .put("guid", "guid-contractor-1")
                .put("parent", "Sundry Creditors")
                .put("incometaxnumber", "ABCDE1234F");
        return json.writeValueAsBytes(root);
    }

    private Workflow freezeGstr1April() {
        Workflow w = workflowRepository.saveAndFlush(workflowRow("GSTR1", "2025-04",
                LocalDate.of(2025, 4, 30), WorkflowStage.FINALIZED));
        snapshotService.freeze(w.getId(), w.getOrganizationId(), "acc");
        return w;
    }

    private Workflow workflow(UUID org, WorkflowType type, LocalDate start, LocalDate end,
                              WorkflowStage stage, UUID uploadJobId) {
        Workflow w = workflowRow(type.name(), start.toString().substring(0, 7), end, stage);
        w.setUploadJobId(uploadJobId);
        return workflowRepository.save(w);
    }

    private Workflow workflowRow(String type, String periodKey, LocalDate periodEnd, WorkflowStage stage) {
        Workflow w = new Workflow();
        w.setOrganizationId(orgId);
        w.setType(WorkflowType.valueOf(type));
        w.setPeriodStart(periodEnd.minusDays(29));
        w.setPeriodEnd(periodEnd);
        w.setPeriodKey(periodKey);
        w.setFinancialYear(periodEnd.getYear());
        w.setStage(stage);
        w.setActive(true);
        w.setCreatedBy("acc");
        return w;
    }

    private UUID startWorkflow(String token, String type, String periodKey) throws Exception {
        MvcResult startResult = mockMvc.perform(post("/api/v1/workflows")
                        .contentType("application/json")
                        .content("{\"type\":\"" + type + "\",\"periodKey\":\"" + periodKey + "\"}")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        Map<?, ?> body = json.readValue(startResult.getResponse().getContentAsString(), Map.class);
        return UUID.fromString(body.get("id").toString());
    }

    private String setupOrg() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String username = "it_" + suffix;
        mockMvc.perform(post("/api/auth/signup")
                        .contentType("application/json")
                        .content("{\"username\":\"" + username + "\",\"email\":\"" + username
                                + "@test.com\",\"password\":\"TestPass1!\",\"role\":\"owner\"}"))
                .andExpect(status().isOk());
        MvcResult signinResult = mockMvc.perform(post("/api/auth/signin")
                        .contentType("application/json")
                        .content("{\"username\":\"" + username + "\",\"password\":\"TestPass1!\"}"))
                .andExpect(status().isOk()).andReturn();
        String token = (String) json.readValue(signinResult.getResponse().getContentAsString(), Map.class).get("token");
        MvcResult createOrgResult = mockMvc.perform(post("/api/organizations")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"name\":\"Masters Outcomes Org " + suffix + "\",\"pan\":\"AAACA1234A\",\"financialYearStart\":4}"))
                .andExpect(status().isCreated()).andReturn();
        Map<?, ?> orgBody = json.readValue(createOrgResult.getResponse().getContentAsString(), Map.class);
        orgId = UUID.fromString(orgBody.get("id").toString());
        var organization = organizationRepository.findById(orgId).orElseThrow();
        organization.setBooksStartDate(LocalDate.of(2025, 4, 1));
        organizationRepository.save(organization);
        return selectOrg(signin(username));
    }

    private String memberJwt(String role) throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String username = "member_" + suffix;
        mockMvc.perform(post("/api/auth/signup")
                        .contentType("application/json")
                        .content("{\"username\":\"" + username + "\",\"email\":\"" + username
                                + "@test.com\",\"password\":\"TestPass1!\",\"role\":\"" + role + "\"}"))
                .andExpect(status().isOk());
        MvcResult inviteResult = mockMvc.perform(post("/api/organizations/" + orgId + "/invites")
                        .param("role", role)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk()).andReturn();
        String inviteToken = json.readTree(inviteResult.getResponse().getContentAsString()).get("token").asText();
        mockMvc.perform(post("/api/organizations/invites/" + inviteToken + "/accept")
                        .header("Authorization", "Bearer " + signin(username)))
                .andExpect(status().isOk());
        return selectOrg(signin(username));
    }

    private String signin(String username) throws Exception {
        MvcResult signinResult = mockMvc.perform(post("/api/auth/signin")
                        .contentType("application/json")
                        .content("{\"username\":\"" + username + "\",\"password\":\"TestPass1!\"}"))
                .andExpect(status().isOk()).andReturn();
        return (String) json.readValue(signinResult.getResponse().getContentAsString(), Map.class).get("token");
    }

    private String selectOrg(String token) throws Exception {
        MvcResult selectResult = mockMvc.perform(post("/api/organizations/" + orgId + "/select")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        return (String) json.readValue(selectResult.getResponse().getContentAsString(), Map.class).get("token");
    }

    private DayBookUploadJob completedJob(UUID org, LocalDate from, LocalDate to) {
        DayBookUploadJob job = new DayBookUploadJob();
        job.setOrganizationId(org);
        job.setFileName("daybook.xml");
        job.setUploadedBy("acc");
        job.setStatus(UploadJobStatus.COMPLETED);
        job.setCoverageFrom(from);
        job.setCoverageTo(to);
        return dayBookUploadJobRepository.save(job);
    }

    private ParsedVoucherEntry entry(String ledgerName, String amount) {
        ParsedVoucherEntry e = new ParsedVoucherEntry();
        e.setLedgerName(ledgerName);
        e.setAmount(new BigDecimal(amount));
        return e;
    }

    private ParsedVoucher seedVoucher(UUID org, UUID jobId, String guid, String type, String number,
                                      LocalDate date, String contentHash, ParsedVoucherEntry... entries) {
        ParsedVoucher v = new ParsedVoucher();
        v.setOrganizationId(org);
        v.setUploadJobId(jobId);
        v.setVoucherGuid(guid);
        v.setVoucherTypeName(type);
        v.setVoucherNumber(number);
        v.setVoucherDate(date);
        v.setContentHash(contentHash);
        v.setPartyLedgerName("Client ABC");
        List<ParsedVoucherEntry> list = new ArrayList<>();
        for (ParsedVoucherEntry e : entries) {
            e.setOrganizationId(org);
            e.setParsedVoucher(v);
            list.add(e);
        }
        v.setEntries(list);
        return parsedVoucherRepository.save(v);
    }
}
