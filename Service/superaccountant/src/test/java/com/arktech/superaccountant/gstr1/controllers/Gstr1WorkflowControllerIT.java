package com.arktech.superaccountant.gstr1.controllers;

import com.arktech.superaccountant.gstr1.models.PreparedReturn;
import com.arktech.superaccountant.gstr1.models.PreparedReturnRow;
import com.arktech.superaccountant.gstr1.repository.PreparedReturnRepository;
import com.arktech.superaccountant.gstr1.repository.PreparedReturnRowRepository;
import com.arktech.superaccountant.masters.models.Organization;
import com.arktech.superaccountant.masters.repository.OrganizationRepository;
import com.arktech.superaccountant.workflow.models.WorkflowEvent;
import com.arktech.superaccountant.workflow.models.WorkflowEventKind;
import com.arktech.superaccountant.workflow.models.WorkflowStage;
import com.arktech.superaccountant.workflow.repository.WorkflowEventRepository;
import com.arktech.superaccountant.workflow.repository.WorkflowRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Transactional
@TestPropertySource(properties = "JWT_SECRET=test-jwt-secret-must-be-at-least-32-characters-long")
class Gstr1WorkflowControllerIT {

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
    private WorkflowRepository workflowRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private PreparedReturnRepository preparedReturnRepository;

    @Autowired
    private PreparedReturnRowRepository preparedReturnRowRepository;

    @Autowired
    private WorkflowEventRepository workflowEventRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String signupAndLogin(String username, String email) throws Exception {
        return signupAndLogin(username, email, "owner");
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
                    {"name":"Gstr1 Prepare Org","pan":"AAACA1234A","financialYearStart":4}
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

    private UUID startedGstr1Workflow(String operatorJwt) throws Exception {
        MvcResult startResult = mockMvc.perform(post("/api/v1/workflows")
                .header("Authorization", "Bearer " + operatorJwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"type":"GSTR1","periodKey":"2026-04"}
                    """))
                .andExpect(status().isOk())
                .andReturn();
        return UUID.fromString(
                objectMapper.readTree(startResult.getResponse().getContentAsString())
                        .get("id").asText());
    }

    @Test
    void getTables_operatorReads200_withTableRowsAndTotals() throws Exception {
        String ownerJwt = signupAndLogin("g1ownera", "g1ownera@test.com");
        UUID orgId = createOrganization(ownerJwt);
        ownerJwt = selectOrg(ownerJwt, orgId);
        String operatorJwt = memberJwt(ownerJwt, orgId, "operator",
                "g1operatora", "g1operatora@test.com");

        UUID workflowId = startedGstr1Workflow(operatorJwt);

        mockMvc.perform(get("/api/v1/workflows/{id}/gstr1/tables", workflowId)
                .header("Authorization", "Bearer " + operatorJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tables").isArray())
                .andExpect(jsonPath("$.fillIns").isArray());
    }

    @Test
    void getValidation_operatorReads200_withErrorsAndWarnings() throws Exception {
        String ownerJwt = signupAndLogin("g1ownerd", "g1ownerd@test.com");
        UUID orgId = createOrganization(ownerJwt);
        ownerJwt = selectOrg(ownerJwt, orgId);
        String operatorJwt = memberJwt(ownerJwt, orgId, "operator",
                "g1operatord", "g1operatord@test.com");

        UUID workflowId = startedGstr1Workflow(operatorJwt);

        mockMvc.perform(get("/api/v1/workflows/{id}/gstr1/validation", workflowId)
                .header("Authorization", "Bearer " + operatorJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.warnings").isArray());
    }

    @Test
    void putFillIn_asAccountant200_andListedAsFixedInPrepare() throws Exception {
        String ownerJwt = signupAndLogin("g1ownerb", "g1ownerb@test.com");
        UUID orgId = createOrganization(ownerJwt);
        ownerJwt = selectOrg(ownerJwt, orgId);
        String operatorJwt = memberJwt(ownerJwt, orgId, "operator",
                "g1operatorb", "g1operatorb@test.com");
        String accountantJwt = memberJwt(ownerJwt, orgId, "accountant",
                "g1accountantb", "g1accountantb@test.com");

        UUID workflowId = startedGstr1Workflow(operatorJwt);
        setStage(workflowId, WorkflowStage.PREPARE);

        mockMvc.perform(put("/api/v1/workflows/{id}/gstr1/fill-ins", workflowId)
                .header("Authorization", "Bearer " + accountantJwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"scope":"LEDGER","key":"Input GST - CGST","attribute":"HSN","value":"996334"}
                    """))
                .andExpect(status().isOk());

        MvcResult tablesResult = mockMvc.perform(
                get("/api/v1/workflows/{id}/gstr1/tables", workflowId)
                        .header("Authorization", "Bearer " + accountantJwt))
                .andExpect(status().isOk())
                .andReturn();
        var fillIns = objectMapper.readTree(tablesResult.getResponse().getContentAsString())
                .get("fillIns");
        assertThat(fillIns.isArray()).isTrue();
        assertThat(fillIns.size()).isEqualTo(1);
        assertThat(fillIns.get(0).get("attribute").asText()).isEqualTo("HSN");
        assertThat(fillIns.get(0).get("fixedInPrepare").asBoolean()).isTrue();
    }

    @Test
    void putFillIn_asOperator403() throws Exception {
        String ownerJwt = signupAndLogin("g1ownerc", "g1ownerc@test.com");
        UUID orgId = createOrganization(ownerJwt);
        ownerJwt = selectOrg(ownerJwt, orgId);
        String operatorJwt = memberJwt(ownerJwt, orgId, "operator",
                "g1operatorc", "g1operatorc@test.com");

        UUID workflowId = startedGstr1Workflow(operatorJwt);
        setStage(workflowId, WorkflowStage.PREPARE);

        mockMvc.perform(put("/api/v1/workflows/{id}/gstr1/fill-ins", workflowId)
                .header("Authorization", "Bearer " + operatorJwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"scope":"LEDGER","key":"Input GST - CGST","attribute":"HSN","value":"996334"}
                    """))
                .andExpect(status().isForbidden());
    }

    @Test
    void putFillIn_asOwner403() throws Exception {
        String ownerJwt = signupAndLogin("g1ownerd", "g1ownerd@test.com");
        UUID orgId = createOrganization(ownerJwt);
        ownerJwt = selectOrg(ownerJwt, orgId);
        String operatorJwt = memberJwt(ownerJwt, orgId, "operator",
                "g1operatord", "g1operatord@test.com");

        UUID workflowId = startedGstr1Workflow(operatorJwt);
        setStage(workflowId, WorkflowStage.PREPARE);

        mockMvc.perform(put("/api/v1/workflows/{id}/gstr1/fill-ins", workflowId)
                .header("Authorization", "Bearer " + ownerJwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"scope":"LEDGER","key":"Input GST - CGST","attribute":"HSN","value":"996334"}
                    """))
                .andExpect(status().isForbidden());
    }

    @Test
    void putFillIn_asAuditor403() throws Exception {
        String ownerJwt = signupAndLogin("g1ownere", "g1ownere@test.com");
        UUID orgId = createOrganization(ownerJwt);
        ownerJwt = selectOrg(ownerJwt, orgId);
        String operatorJwt = memberJwt(ownerJwt, orgId, "operator",
                "g1operatore", "g1operatore@test.com");
        String auditorJwt = memberJwt(ownerJwt, orgId, "auditor_ca",
                "g1auditore", "g1auditore@test.com");

        UUID workflowId = startedGstr1Workflow(operatorJwt);
        setStage(workflowId, WorkflowStage.PREPARE);

        mockMvc.perform(put("/api/v1/workflows/{id}/gstr1/fill-ins", workflowId)
                .header("Authorization", "Bearer " + auditorJwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"scope":"LEDGER","key":"Input GST - CGST","attribute":"HSN","value":"996334"}
                    """))
                .andExpect(status().isForbidden());
    }

    @Test
    void putFillIn_asAccountant_invalidScope_returns400() throws Exception {
        String ownerJwt = signupAndLogin("g1ownerf", "g1ownerf@test.com");
        UUID orgId = createOrganization(ownerJwt);
        ownerJwt = selectOrg(ownerJwt, orgId);
        String operatorJwt = memberJwt(ownerJwt, orgId, "operator",
                "g1operatorf", "g1operatorf@test.com");
        String accountantJwt = memberJwt(ownerJwt, orgId, "accountant",
                "g1accountantf", "g1accountantf@test.com");

        UUID workflowId = startedGstr1Workflow(operatorJwt);
        setStage(workflowId, WorkflowStage.PREPARE);

        mockMvc.perform(put("/api/v1/workflows/{id}/gstr1/fill-ins", workflowId)
                .header("Authorization", "Bearer " + accountantJwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"scope":"NOT_A_SCOPE","key":"Input GST - CGST","attribute":"HSN","value":"996334"}
                    """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put("/api/v1/workflows/{id}/gstr1/fill-ins", workflowId)
                .header("Authorization", "Bearer " + accountantJwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"scope":"LEDGER","key":"Input GST - CGST","attribute":"NOT_AN_ATTRIBUTE","value":"996334"}
                    """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put("/api/v1/workflows/{id}/gstr1/fill-ins", workflowId)
                .header("Authorization", "Bearer " + accountantJwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"scope":"LEDGER","attribute":"HSN","value":"996334"}
                    """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void export_asAccountant200_withContentDisposition() throws Exception {
        String ownerJwt = signupAndLogin("g1ownerg", "g1ownerg@test.com");
        UUID orgId = createOrganization(ownerJwt);
        ownerJwt = selectOrg(ownerJwt, orgId);
        setOrgGstin(orgId, "27ABCDE1234F1Z5");
        String accountantJwt = memberJwt(ownerJwt, orgId, "accountant",
                "g1accountantg", "g1accountantg@test.com");

        UUID workflowId = startedGstr1Workflow(accountantJwt);
        setStage(workflowId, WorkflowStage.PREPARE);

        MvcResult result = mockMvc.perform(
                get("/api/v1/workflows/{id}/gstr1/export", workflowId)
                        .param("includeIrp", "false")
                        .header("Authorization", "Bearer " + accountantJwt))
                .andExpect(status().isOk())
                .andReturn();
        String disposition = result.getResponse().getHeader("Content-Disposition");
        assertThat(disposition).isNotNull();
        assertThat(disposition).contains("attachment");
        assertThat(disposition).contains(".json");
        assertThat(result.getResponse().getContentAsByteArray()).isNotEmpty();
    }

    @Test
    void export_asOperator403() throws Exception {
        String ownerJwt = signupAndLogin("g1ownerh", "g1ownerh@test.com");
        UUID orgId = createOrganization(ownerJwt);
        ownerJwt = selectOrg(ownerJwt, orgId);
        setOrgGstin(orgId, "27ABCDE1234F1Z5");
        String operatorJwt = memberJwt(ownerJwt, orgId, "operator",
                "g1operatorh", "g1operatorh@test.com");

        UUID workflowId = startedGstr1Workflow(operatorJwt);
        setStage(workflowId, WorkflowStage.PREPARE);

        mockMvc.perform(get("/api/v1/workflows/{id}/gstr1/export", workflowId)
                .header("Authorization", "Bearer " + operatorJwt))
                .andExpect(status().isForbidden());
    }

    @Test
    void export_inPrepare_withValidationErrors409() throws Exception {
        String ownerJwt = signupAndLogin("g1owneri", "g1owneri@test.com");
        UUID orgId = createOrganization(ownerJwt);
        ownerJwt = selectOrg(ownerJwt, orgId);
        setOrgGstin(orgId, "27ABCDE1234F1Z5");
        String accountantJwt = memberJwt(ownerJwt, orgId, "accountant",
                "g1accountanti", "g1accountanti@test.com");

        UUID workflowId = startedGstr1Workflow(accountantJwt);
        setStage(workflowId, WorkflowStage.PREPARE);
        // trigger a prepared return, then seed a B2B row whose payload has no gstin
        mockMvc.perform(get("/api/v1/workflows/{id}/gstr1/tables", workflowId)
                .header("Authorization", "Bearer " + accountantJwt))
                .andExpect(status().isOk());
        seedErrorRow(workflowId);

        mockMvc.perform(get("/api/v1/workflows/{id}/gstr1/export", workflowId)
                .header("Authorization", "Bearer " + accountantJwt))
                .andExpect(status().isConflict());
    }

    @Test
    void export_beforePrepare409() throws Exception {
        String ownerJwt = signupAndLogin("g1ownerj", "g1ownerj@test.com");
        UUID orgId = createOrganization(ownerJwt);
        ownerJwt = selectOrg(ownerJwt, orgId);
        setOrgGstin(orgId, "27ABCDE1234F1Z5");
        String accountantJwt = memberJwt(ownerJwt, orgId, "accountant",
                "g1accountantj", "g1accountantj@test.com");

        UUID workflowId = startedGstr1Workflow(accountantJwt);
        setStage(workflowId, WorkflowStage.STARTED);

        mockMvc.perform(get("/api/v1/workflows/{id}/gstr1/export", workflowId)
                .header("Authorization", "Bearer " + accountantJwt))
                .andExpect(status().isConflict());
    }

    @Test
    void export_writesPortalFileDownloadedEvent_withBodySha256() throws Exception {
        String ownerJwt = signupAndLogin("g1ownerk", "g1ownerk@test.com");
        UUID orgId = createOrganization(ownerJwt);
        ownerJwt = selectOrg(ownerJwt, orgId);
        setOrgGstin(orgId, "27ABCDE1234F1Z5");
        String accountantJwt = memberJwt(ownerJwt, orgId, "accountant",
                "g1accountantk", "g1accountantk@test.com");

        UUID workflowId = startedGstr1Workflow(accountantJwt);
        setStage(workflowId, WorkflowStage.PREPARE);

        MvcResult result = mockMvc.perform(
                get("/api/v1/workflows/{id}/gstr1/export", workflowId)
                        .header("Authorization", "Bearer " + accountantJwt))
                .andExpect(status().isOk())
                .andReturn();
        byte[] body = result.getResponse().getContentAsByteArray();
        String expectedSha = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(body));

        var events = workflowEventRepository
                .findAllByWorkflowIdOrderByOccurredAtAsc(workflowId);
        var downloadEvents = events.stream()
                .filter(e -> e.getKind() == WorkflowEventKind.PORTAL_FILE_DOWNLOADED)
                .toList();
        assertThat(downloadEvents).hasSize(1);
        var payload = objectMapper.readTree(downloadEvents.get(0).getPayload());
        assertThat(payload.get("sha256").asText()).isEqualTo(expectedSha);
        assertThat(payload.get("bytes").asLong()).isEqualTo((long) body.length);
        assertThat(payload.has("writerVersion")).isTrue();
        assertThat(payload.has("parts")).isTrue();
    }

    @Test
    void export_finalizedHashMatchesPrepareHash_forUnchangedData() throws Exception {
        String ownerJwt = signupAndLogin("g1ownerl", "g1ownerl@test.com");
        UUID orgId = createOrganization(ownerJwt);
        ownerJwt = selectOrg(ownerJwt, orgId);
        setOrgGstin(orgId, "27ABCDE1234F1Z5");
        String accountantJwt = memberJwt(ownerJwt, orgId, "accountant",
                "g1accountantl", "g1accountantl@test.com");

        UUID workflowId = startedGstr1Workflow(accountantJwt);
        setStage(workflowId, WorkflowStage.PREPARE);

        MvcResult prepareResult = mockMvc.perform(
                get("/api/v1/workflows/{id}/gstr1/export", workflowId)
                        .header("Authorization", "Bearer " + accountantJwt))
                .andExpect(status().isOk())
                .andReturn();
        byte[] prepareBody = prepareResult.getResponse().getContentAsByteArray();

        setStage(workflowId, WorkflowStage.FINALIZED);
        MvcResult finalizedResult = mockMvc.perform(
                get("/api/v1/workflows/{id}/gstr1/export", workflowId)
                        .header("Authorization", "Bearer " + accountantJwt))
                .andExpect(status().isOk())
                .andReturn();
        byte[] finalizedBody = finalizedResult.getResponse().getContentAsByteArray();

        assertThat(finalizedBody).isEqualTo(prepareBody);
    }

    private void setOrgGstin(UUID orgId, String gstin) {
        Organization org = organizationRepository.findById(orgId).orElseThrow();
        org.setGstin(gstin);
        organizationRepository.save(org);
    }

    private void seedErrorRow(UUID workflowId) {
        PreparedReturn prepared = preparedReturnRepository
                .findByWorkflowIdAndReturnKind(workflowId, "GSTR1").orElseThrow();
        // freeze so export's buildOrRebuild keeps the seeded row instead of rebuilding
        prepared.setFrozenAt(Instant.now());
        preparedReturnRepository.save(prepared);
        PreparedReturnRow row = new PreparedReturnRow();
        row.setPreparedReturnId(prepared.getId());
        row.setTableCode("B2B");
        row.setRowKey("seeded-row-1");
        row.setPayload(objectMapper.valueToTree(
                new LinkedHashMap<>(Map.of("invoiceNumber", "SEED-1"))).toString());
        row.setAmendment(false);
        preparedReturnRowRepository.save(row);
    }

    private void setStage(UUID workflowId, WorkflowStage stage) {
        var workflow = workflowRepository.findById(workflowId).orElseThrow();
        workflow.setStage(stage);
        workflowRepository.save(workflow);
    }
}
