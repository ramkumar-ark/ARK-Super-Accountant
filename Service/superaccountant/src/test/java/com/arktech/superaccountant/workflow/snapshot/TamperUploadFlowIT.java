package com.arktech.superaccountant.workflow.snapshot;

import com.arktech.superaccountant.masters.models.UploadJob;
import com.arktech.superaccountant.masters.models.UploadJobStatus;
import com.arktech.superaccountant.masters.models.UploadLedgerSnapshot;
import com.arktech.superaccountant.masters.repository.OrganizationRepository;
import com.arktech.superaccountant.masters.repository.UploadJobRepository;
import com.arktech.superaccountant.masters.repository.UploadLedgerSnapshotRepository;
import com.arktech.superaccountant.workflow.models.Workflow;
import com.arktech.superaccountant.workflow.models.WorkflowStage;
import com.arktech.superaccountant.workflow.repository.WorkflowRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
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
 * End to end over HTTP, not @Transactional: the tamper listener fires on the
 * intake commit, so state must really be committed between steps.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = "JWT_SECRET=test-jwt-secret-must-be-at-least-32-characters-long")
class TamperUploadFlowIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @MockitoSpyBean
    private TamperComparisonService tamperComparisonService;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private OrganizationRepository organizationRepository;
    @Autowired
    private UploadJobRepository uploadJobRepository;
    @Autowired
    private UploadLedgerSnapshotRepository uploadLedgerSnapshotRepository;
    @Autowired
    private WorkflowRepository workflowRepository;

    private UUID orgId;
    private final com.fasterxml.jackson.databind.ObjectMapper json = new ObjectMapper();

    private static final List<String> FIXTURE_LEDGER_NAMES = List.of(
            "Bank Account", "Cement Purchases", "Cement Supplier", "Client ABC", "Depreciation",
            "Input CGST @9%", "Input IGST @18%", "Input SGST @9%", "Output CGST @9%",
            "Output SGST @9%", "Plant & Machinery", "Sales Account", "Steel Purchases",
            "Test Cement Purchases", "Test Purchases");

    @Test
    void finalizeThenEditedUpload_raisesBlockingTamperFindingOnOpenWorkflow() throws Exception {
        String token = setupAccountantOrg();
        UUID gstr1Id = startWorkflow(token, "GSTR1");
        upload(token, gstr1Id, fixtureBytes());
        forcePrepareAndConfirmBaseline(gstr1Id);
        mockMvc.perform(post("/api/v1/workflows/" + gstr1Id + "/finalize")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        UUID gstr3bId = startWorkflow(token, "GSTR3B");
        upload(token, gstr3bId, mutatedFixture());

        MvcResult findings = mockMvc.perform(get("/api/v1/workflows/" + gstr3bId + "/findings")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = json.readTree(findings.getResponse().getContentAsString());
        List<JsonNode> tamperFindings = new java.util.ArrayList<>();
        body.get("findings").forEach(f -> {
            if ("TAMPER".equals(f.get("source").asText())) {
                tamperFindings.add(f);
            }
        });
        assertThat(tamperFindings).hasSize(1);
        assertThat(tamperFindings.get(0).get("findingType").asText()).isEqualTo("TAMPER_MODIFIED");
        assertThat(tamperFindings.get(0).get("blocking").asBoolean()).isTrue();
        assertThat(body.get("blockingCount").asInt()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void listenerFailure_doesNotFailTheUpload() throws Exception {
        Mockito.doThrow(new RuntimeException("boom"))
                .when(tamperComparisonService).compare(Mockito.any(UUID.class));
        try {
            String token = setupAccountantOrg();
            UUID gstr1Id = startWorkflow(token, "GSTR1");
            mockMvc.perform(
                            multipart("/api/v1/workflows/" + gstr1Id + "/upload")
                                    .file(new org.springframework.mock.web.MockMultipartFile("file",
                                            "daybook-multi-type.json", "application/json", fixtureBytes()))
                                    .param("coverageFrom", "2025-04-01")
                                    .param("coverageTo", "2025-05-31")
                                    .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());
        } finally {
            Mockito.doNothing().when(tamperComparisonService).compare(Mockito.any(UUID.class));
        }
    }

    // -- helpers ------------------------------------------------------------

    private byte[] fixtureBytes() throws Exception {
        return getClass().getResourceAsStream("/fixtures/daybook-multi-type.json").readAllBytes();
    }

    /** Mutated copy: every SB/001 ledger amount +100. */
    private byte[] mutatedFixture() throws Exception {
        JsonNode root = json.readTree(fixtureBytes());
        for (JsonNode voucher : root.get("tallymessage")) {
            if (!"SB/001".equals(voucher.path("vouchernumber").asText())) {
                continue;
            }
            for (JsonNode ledgerEntry : voucher.get("allledgerentries")) {
                BigDecimal amount = new BigDecimal(ledgerEntry.get("amount").asText());
                ((ObjectNode) ledgerEntry).put("amount", amount.add(new BigDecimal("100")).toPlainString());
            }
        }
        return json.writeValueAsBytes(root);
    }

    private void upload(String token, UUID workflowId, byte[] fixture) throws Exception {
        mockMvc.perform(multipart("/api/v1/workflows/" + workflowId + "/upload")
                        .file(new MockMultipartFile("file", "daybook-multi-type.json",
                                "application/json", fixture))
                        .param("coverageFrom", "2025-04-01")
                        .param("coverageTo", "2025-05-31")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    private void forcePrepareAndConfirmBaseline(UUID workflowId) {
        Workflow w = workflowRepository.findById(workflowId).orElseThrow();
        w.setStage(WorkflowStage.PREPARE);
        w.setBaselineConfirmedAt(Instant.now());
        workflowRepository.save(w);
    }

    private void seedValidatedMasters(UUID org) {
        UploadJob masters = new UploadJob();
        masters.setOrganizationId(org);
        masters.setFileName("masters-fixture.json");
        masters.setStatus(UploadJobStatus.COMPLETED);
        masters.setTotalLedgersParsed(FIXTURE_LEDGER_NAMES.size());
        masters = uploadJobRepository.save(masters);
        for (String name : FIXTURE_LEDGER_NAMES) {
            UploadLedgerSnapshot snapshot = new UploadLedgerSnapshot();
            snapshot.setUploadJobId(masters.getId());
            snapshot.setOrganizationId(org);
            snapshot.setLedgerName(name);
            uploadLedgerSnapshotRepository.save(snapshot);
        }
    }

    private String setupAccountantOrg() throws Exception {
        return setupOrg("accountant", true);
    }

    private String setupOrg(String role, boolean seedMasters) throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String username = "it_" + suffix;

        mockMvc.perform(post("/api/auth/signup")
                        .contentType("application/json")
                        .content("{\"username\":\"" + username + "\",\"email\":\"" + username
                                + "@test.com\",\"password\":\"TestPass1!\",\"role\":\"" + role + "\"}"))
                .andExpect(status().isOk());

        MvcResult signinResult = mockMvc.perform(post("/api/auth/signin")
                        .contentType("application/json")
                        .content("{\"username\":\"" + username + "\",\"password\":\"TestPass1!\"}"))
                .andExpect(status().isOk())
                .andReturn();
        Map<?, ?> signinBody = json.readValue(
                signinResult.getResponse().getContentAsString(), Map.class);
        String token = (String) signinBody.get("token");

        MvcResult createOrgResult = mockMvc.perform(post("/api/organizations")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"name\":\"Test Org " + suffix + "\",\"financialYearStart\":4}"))
                .andExpect(status().isCreated())
                .andReturn();
        Map<?, ?> orgBody = json.readValue(
                createOrgResult.getResponse().getContentAsString(), Map.class);
        orgId = UUID.fromString(orgBody.get("id").toString());

        var org = organizationRepository.findById(orgId).orElseThrow();
        org.setBooksStartDate(LocalDate.of(2025, 4, 1));
        organizationRepository.save(org);

        if (seedMasters) {
            seedValidatedMasters(orgId);
        }

        MvcResult selectResult = mockMvc.perform(post("/api/organizations/" + orgId + "/select")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        Map<?, ?> selectBody = json.readValue(
                selectResult.getResponse().getContentAsString(), Map.class);
        return (String) selectBody.get("token");
    }

    private UUID startWorkflow(String token, String type) throws Exception {
        MvcResult startResult = mockMvc.perform(post("/api/v1/workflows")
                        .contentType("application/json")
                        .content("{\"type\":\"" + type + "\",\"periodKey\":\"2025-04\"}")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        Map<?, ?> body = json.readValue(startResult.getResponse().getContentAsString(), Map.class);
        return UUID.fromString(body.get("id").toString());
    }
}
