package com.arktech.superaccountant.workflow.snapshot;

import com.arktech.superaccountant.masters.models.Organization;
import com.arktech.superaccountant.masters.repository.OrganizationRepository;
import com.arktech.superaccountant.masters.repository.UploadJobRepository;
import com.arktech.superaccountant.masters.repository.UploadLedgerSnapshotRepository;
import com.arktech.superaccountant.workflow.models.Workflow;
import com.arktech.superaccountant.workflow.models.WorkflowStage;
import com.arktech.superaccountant.workflow.models.WorkflowType;
import com.arktech.superaccountant.workflow.repository.WorkflowRepository;
import com.arktech.superaccountant.workflow.seam.SnapshotService;
import com.fasterxml.jackson.databind.JsonNode;
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

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Masters tamper end to end over HTTP, not @Transactional: the listener fires
 * on the masters intake commit, so state must really be committed between steps.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = "JWT_SECRET=test-jwt-secret-must-be-at-least-32-characters-long")
class MastersTamperFlowIT {

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
    private UploadJobRepository uploadJobRepository;
    @Autowired
    private UploadLedgerSnapshotRepository uploadLedgerSnapshotRepository;

    private final ObjectMapper json = new ObjectMapper();

    // -- tests ---------------------------------------------------------------

    @Test
    void gstinChanged_raisesBlockingFindingOnOpenGstr1AfterFrozenPeriod() throws Exception {
        String token = setupOrg();
        mastersUpload(token, mastersFile("29ABCDE1234F1Z5", true));
        UUID gstr1Id = finalizeGstr1(token);
        UUID gstr1May = startWorkflow(token, "GSTR1", "2025-05");

        mastersUpload(token, mastersFile("29ABCDE1234F1Z6", true));
        awaitChanges();

        JsonNode finding = findings(token, gstr1May).stream()
                .filter(f -> f.get("findingType").asText().startsWith("TAMPER_MASTER_"))
                .findFirst().orElse(null);
        assertThat(finding).isNotNull();
        assertThat(finding.get("findingType").asText()).isEqualTo("TAMPER_MASTER_MODIFIED");
        assertThat(finding.get("blocking").asBoolean()).isTrue();
        assertThat(finding.get("message").asText()).isNotBlank();
        // evidence slots carry old/new value for masters findings
        assertThat(finding.get("expectedValue").asText()).isEqualTo("29ABCDE1234F1Z5");
        assertThat(finding.get("actualValue").asText()).isEqualTo("29ABCDE1234F1Z6");
    }

    @Test
    void gstinChanged_isWarningOnUnaffectedOpenWorkflow() throws Exception {
        String token = setupOrg();
        mastersUpload(token, mastersFile("29ABCDE1234F1Z5", true));
        UUID gstr1Id = finalizeGstr1(token);
        UUID tdsId = startWorkflow(token, "TDS_PAYMENT", "2025-04");

        mastersUpload(token, mastersFile("29ABCDE1234F1Z6", true));
        awaitChanges();

        MvcResult result = mockMvc.perform(get("/api/v1/workflows/" + tdsId + "/findings")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        JsonNode body = json.readTree(result.getResponse().getContentAsString());
        List<JsonNode> masterFindings = new ArrayList<>();
        body.get("findings").forEach(f -> {
            if (f.get("findingType").asText().startsWith("TAMPER_MASTER_")) {
                masterFindings.add(f);
            }
        });
        assertThat(masterFindings).hasSize(1);
        assertThat(masterFindings.get(0).get("blocking").asBoolean()).isFalse();
        assertThat(body.get("blockingCount").asInt()).isZero();
        assertThat(workflowRepository.findById(gstr1Id).orElseThrow()).isNotNull();
    }

    @Test
    void removedLedger_raisesOneRemovedFinding() throws Exception {
        String token = setupOrg();
        mastersUpload(token, mastersFile("29ABCDE1234F1Z5", true));
        UUID gstr1Id = finalizeGstr1(token);
        UUID gstr1May = startWorkflow(token, "GSTR1", "2025-05");

        mastersUpload(token, mastersFile("29ABCDE1234F1Z5", false));
        awaitChanges();

        List<JsonNode> masterFindings = findings(token, gstr1May).stream()
                .filter(f -> f.get("findingType").asText().equals("TAMPER_MASTER_REMOVED"))
                .toList();
        assertThat(masterFindings).hasSize(6);
        assertThat(masterFindings).allSatisfy(f -> assertThat(f.get("blocking").asBoolean()).isTrue());
    }

    @Test
    void caseAndSpaceDrift_noFinding() throws Exception {
        String token = setupOrg();
        mastersUpload(token, mastersFile("29ABCDE1234F1Z5", true));
        UUID gstr1Id = finalizeGstr1(token);
        UUID gstr1May = startWorkflow(token, "GSTR1", "2025-05");

        mastersUpload(token, mastersFileCaseDrift("29ABCDE1234F1Z5"));
        awaitChanges();

        assertThat(findings(token, gstr1May).stream()
                .filter(f -> f.get("findingType").asText().startsWith("TAMPER_MASTER_")).count()).isZero();
    }

    @Test
    void secondIdenticalUpload_noNewFinding() throws Exception {
        String token = setupOrg();
        mastersUpload(token, mastersFile("29ABCDE1234F1Z5", true));
        UUID gstr1Id = finalizeGstr1(token);
        UUID gstr1May = startWorkflow(token, "GSTR1", "2025-05");

        mastersUpload(token, mastersFile("29ABCDE1234F1Z6", true));
        awaitChanges();
        mastersUpload(token, mastersFile("29ABCDE1234F1Z6", true));
        awaitChanges();

        long open = tamperFindingRepository.findAll().stream()
                .filter(f -> f.getOrganizationId().equals(orgId()))
                .filter(f -> f.getFindingKey().startsWith("MASTER_"))
                .filter(f -> f.getResolveStatus().name().equals("OPEN"))
                .count();
        assertThat(open).isEqualTo(1);
    }

    @Test
    void restoredValue_autoResolvesOpenFinding() throws Exception {
        String token = setupOrg();
        mastersUpload(token, mastersFile("29ABCDE1234F1Z5", true));
        UUID gstr1Id = finalizeGstr1(token);
        UUID gstr1May = startWorkflow(token, "GSTR1", "2025-05");

        mastersUpload(token, mastersFile("29ABCDE1234F1Z6", true));
        awaitChanges();
        mastersUpload(token, mastersFile("29ABCDE1234F1Z5", true));
        awaitChanges();

        List<TamperFinding> rows = tamperFindingRepository.findAll().stream()
                .filter(f -> f.getOrganizationId().equals(orgId()))
                .filter(f -> f.getFindingKey().startsWith("MASTER_")).toList();
        assertThat(rows).isNotEmpty();
        assertThat(rows).allSatisfy(f -> {
            assertThat(f.getResolveStatus()).isEqualTo(com.arktech.superaccountant.masters.models.ResolveStatus.RESOLVED);
            assertThat(f.getResolveNote()).contains("back to the frozen value");
        });
    }

    @Test
    void categoryMove_scopedUnderOldAndNew() throws Exception {
        String token = setupOrg();
        mastersUpload(token, mastersFile("29ABCDE1234F1Z5", true));
        UUID gstr1Id = finalizeGstr1(token);
        UUID gstr1May = startWorkflow(token, "GSTR1", "2025-05");

        mastersUpload(token, mastersFileCategoryMove("29ABCDE1234F1Z5"));
        awaitChanges();

        List<JsonNode> masterFindings = findings(token, gstr1May).stream()
                .filter(f -> f.get("findingType").asText().startsWith("TAMPER_MASTER_")).toList();
        assertThat(masterFindings).isNotEmpty();
        assertThat(masterFindings.get(0).get("ledgerName").asText()).isEqualTo("Acme Traders");
    }

    @Test
    void failedMastersUpload_publishesNothing() throws Exception {
        String token = setupOrg();
        mastersUpload(token, mastersFile("29ABCDE1234F1Z5", true));
        UUID gstr1Id = finalizeGstr1(token);

        MockMultipartFile bad = new MockMultipartFile("file", "bad.json", "application/json",
                "{not json".getBytes());
        mockMvc.perform(multipart("/api/v1/uploads").file(bad)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
        Thread.sleep(500);
        assertThat(tamperChangeRepository.findAll().stream()
                .filter(c -> c.getOrganizationId().equals(currentOrgId)).count()).isZero();
    }

    // -- helpers -------------------------------------------------------------

    private UUID currentOrgId;

    private UUID orgId() {
        return currentOrgId;
    }

    private void awaitChanges() throws Exception {
        long deadline = System.currentTimeMillis() + 10_000;
        long seen = 0;
        while (System.currentTimeMillis() < deadline) {
            seen = tamperChangeRepository.count();
            if (seen > 0) {
                return;
            }
            Thread.sleep(200);
        }
        assertThat(seen).as("masters tamper changes never appeared").isGreaterThan(0);
    }

    private List<JsonNode> findings(String token, UUID workflowId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/workflows/" + workflowId + "/findings")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        JsonNode body = json.readTree(result.getResponse().getContentAsString());
        List<JsonNode> out = new ArrayList<>();
        body.get("findings").forEach(out::add);
        return out;
    }

    private void mastersUpload(String token, byte[] content) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "masters.json", "application/json", content);
        mockMvc.perform(multipart("/api/v1/uploads").file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated());
    }

    private byte[] mastersFile(String gstin, boolean includeAcme) throws Exception {
        return mastersJson("Acme Traders", "Sales Accounts", gstin, includeAcme);
    }

    private byte[] mastersFileCaseDrift(String gstin) throws Exception {
        return mastersJson(" acme traders ", "Sales Accounts", gstin, true);
    }

    private byte[] mastersFileCategoryMove(String gstin) throws Exception {
        return mastersJson("Acme Traders", "Sundry Creditors", gstin, true);
    }

    private byte[] mastersJson(String ledgerName, String parentGroup, String gstin, boolean includeAcme)
            throws Exception {
        ObjectNode root = json.createObjectNode();
        var entries = root.putArray("tallymessage");
        ObjectNode salesGroup = entries.addObject();
        salesGroup.putObject("group").put("name", "Sales Accounts").put("parent", "");
        ObjectNode creditorGroup = entries.addObject();
        creditorGroup.putObject("group").put("name", "Sundry Creditors").put("parent", "");
        if (includeAcme) {
            ObjectNode acme = entries.addObject();
            acme.putObject("ledger").put("name", ledgerName)
                    .put("guid", "guid-acme-1")
                    .put("parent", parentGroup)
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

    /** Seeds a GSTR1 2025-04 workflow, freezes it (via the seam) and marks it FINALIZED. */
    private UUID finalizeGstr1(String token) {
        Workflow w = workflowRepository.saveAndFlush(workflowRow("GSTR1", "2025-04",
                LocalDate.of(2025, 4, 30), WorkflowStage.FINALIZED));
        snapshotService.freeze(w.getId(), w.getOrganizationId(), "acc");
        return w.getId();
    }

    private Workflow workflowRow(String type, String periodKey, LocalDate periodEnd, WorkflowStage stage) {
        UUID orgId = currentOrgId;
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
                                + "@test.com\",\"password\":\"TestPass1!\",\"role\":\"accountant\"}"))
                .andExpect(status().isOk());

        MvcResult signinResult = mockMvc.perform(post("/api/auth/signin")
                        .contentType("application/json")
                        .content("{\"username\":\"" + username + "\",\"password\":\"TestPass1!\"}"))
                .andExpect(status().isOk())
                .andReturn();
        Map<?, ?> signinBody = json.readValue(signinResult.getResponse().getContentAsString(), Map.class);
        String token = (String) signinBody.get("token");

        MvcResult createOrgResult = mockMvc.perform(post("/api/organizations")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"name\":\"Test Org " + suffix + "\",\"pan\":\"AAACA1234A\",\"financialYearStart\":4}"))
                .andExpect(status().isCreated())
                .andReturn();
        Map<?, ?> orgBody = json.readValue(createOrgResult.getResponse().getContentAsString(), Map.class);
        UUID org = UUID.fromString(orgBody.get("id").toString());
        currentOrgId = org;

        var organization = organizationRepository.findById(org).orElseThrow();
        organization.setBooksStartDate(LocalDate.of(2025, 4, 1));
        organizationRepository.save(organization);

        MvcResult selectResult = mockMvc.perform(post("/api/organizations/" + org + "/select")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        Map<?, ?> selectBody = json.readValue(selectResult.getResponse().getContentAsString(), Map.class);
        return (String) selectBody.get("token");
    }
}
