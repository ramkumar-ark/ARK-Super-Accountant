package com.arktech.superaccountant.gstr1.controllers;

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

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Transactional
@TestPropertySource(properties = "JWT_SECRET=test-jwt-secret-must-be-at-least-32-characters-long")
class Gstr1PortalUploadIT {

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
    private WorkflowEventRepository workflowEventRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

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
                    {"name":"Gstr1 Upload Org","financialYearStart":4}
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

    private UUID startedGstr1Workflow(String accountantJwt) throws Exception {
        MvcResult startResult = mockMvc.perform(post("/api/v1/workflows")
                .header("Authorization", "Bearer " + accountantJwt)
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

    private void setStage(UUID workflowId, WorkflowStage stage) {
        var workflow = workflowRepository.findById(workflowId).orElseThrow();
        workflow.setStage(stage);
        workflowRepository.save(workflow);
    }

    private void seedDownloadEvent(UUID workflowId, String sha256) {
        WorkflowEvent e = new WorkflowEvent();
        e.setWorkflowId(workflowId);
        e.setKind(WorkflowEventKind.PORTAL_FILE_DOWNLOADED);
        e.setOccurredAt(Instant.now());
        e.setPayload("{\"sha256\":\"" + sha256 + "\",\"bytes\":100,"
                + "\"writerVersion\":\"test\",\"parts\":1}");
        workflowEventRepository.save(e);
    }

    @Test
    void portalUpload_writesEventWithoutChangingStage() throws Exception {
        String ownerJwt = signupAndLogin("puownera", "puownera@test.com", "owner");
        UUID orgId = createOrganization(ownerJwt);
        ownerJwt = selectOrg(ownerJwt, orgId);
        String accountantJwt = memberJwt(ownerJwt, orgId, "accountant",
                "puaccountanta", "puaccountanta@test.com");

        UUID workflowId = startedGstr1Workflow(accountantJwt);
        setStage(workflowId, WorkflowStage.FINALIZED);

        mockMvc.perform(post("/api/v1/workflows/{id}/gstr1/portal-upload", workflowId)
                .header("Authorization", "Bearer " + accountantJwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"fileHash\":\"" + "a".repeat(64) + "\"}"))
                .andExpect(status().isOk());

        assertThat(workflowRepository.findById(workflowId).orElseThrow()
                .getStage()).isEqualTo(WorkflowStage.FINALIZED);
        var events = workflowEventRepository
                .findAllByWorkflowIdOrderByOccurredAtAsc(workflowId);
        var uploadEvents = events.stream()
                .filter(e -> e.getKind() == WorkflowEventKind.PORTAL_UPLOADED)
                .toList();
        assertThat(uploadEvents).hasSize(1);
        var payload = objectMapper.readTree(uploadEvents.get(0).getPayload());
        assertThat(payload.get("sha256").asText()).isEqualTo("a".repeat(64));
    }

    @Test
    void portalUpload_hashDefaultsToLatestDownloadEvent() throws Exception {
        String ownerJwt = signupAndLogin("puownerb", "puownerb@test.com", "owner");
        UUID orgId = createOrganization(ownerJwt);
        ownerJwt = selectOrg(ownerJwt, orgId);
        String accountantJwt = memberJwt(ownerJwt, orgId, "accountant",
                "puaccountantb", "puaccountantb@test.com");

        UUID workflowId = startedGstr1Workflow(accountantJwt);
        setStage(workflowId, WorkflowStage.FINALIZED);
        seedDownloadEvent(workflowId, "1".repeat(64));
        seedDownloadEvent(workflowId, "2".repeat(64));

        mockMvc.perform(post("/api/v1/workflows/{id}/gstr1/portal-upload", workflowId)
                .header("Authorization", "Bearer " + accountantJwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isOk());

        var events = workflowEventRepository
                .findAllByWorkflowIdOrderByOccurredAtAsc(workflowId);
        var uploadEvents = events.stream()
                .filter(e -> e.getKind() == WorkflowEventKind.PORTAL_UPLOADED)
                .toList();
        assertThat(uploadEvents).hasSize(1);
        var payload = objectMapper.readTree(uploadEvents.get(0).getPayload());
        assertThat(payload.get("sha256").asText()).isEqualTo("2".repeat(64));
    }

    @Test
    void portalUpload_refusedOutsideFinalized() throws Exception {
        String ownerJwt = signupAndLogin("puownerc", "puownerc@test.com", "owner");
        UUID orgId = createOrganization(ownerJwt);
        ownerJwt = selectOrg(ownerJwt, orgId);
        String accountantJwt = memberJwt(ownerJwt, orgId, "accountant",
                "puaccountantc", "puaccountantc@test.com");

        UUID workflowId = startedGstr1Workflow(accountantJwt);
        setStage(workflowId, WorkflowStage.PREPARE);

        mockMvc.perform(post("/api/v1/workflows/{id}/gstr1/portal-upload", workflowId)
                .header("Authorization", "Bearer " + accountantJwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isConflict());

        setStage(workflowId, WorkflowStage.FILED);
        mockMvc.perform(post("/api/v1/workflows/{id}/gstr1/portal-upload", workflowId)
                .header("Authorization", "Bearer " + accountantJwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isConflict());
    }

    @Test
    void portalUpload_operatorForbidden() throws Exception {
        String ownerJwt = signupAndLogin("puownerd", "puownerd@test.com", "owner");
        UUID orgId = createOrganization(ownerJwt);
        ownerJwt = selectOrg(ownerJwt, orgId);
        String operatorJwt = memberJwt(ownerJwt, orgId, "operator",
                "puoperatord", "puoperatord@test.com");

        UUID workflowId = startedGstr1Workflow(operatorJwt);
        setStage(workflowId, WorkflowStage.FINALIZED);

        mockMvc.perform(post("/api/v1/workflows/{id}/gstr1/portal-upload", workflowId)
                .header("Authorization", "Bearer " + operatorJwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void filing_storesFilingChannel() throws Exception {
        String ownerJwt = signupAndLogin("puownere", "puownere@test.com", "owner");
        UUID orgId = createOrganization(ownerJwt);
        ownerJwt = selectOrg(ownerJwt, orgId);
        String accountantJwt = memberJwt(ownerJwt, orgId, "accountant",
                "puaccountante", "puaccountante@test.com");

        UUID workflowId = startedGstr1Workflow(accountantJwt);
        setStage(workflowId, WorkflowStage.FINALIZED);

        mockMvc.perform(post("/api/v1/workflows/{id}/filing", workflowId)
                .header("Authorization", "Bearer " + accountantJwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"ref":"ARN123","date":"2026-05-10","channel":"PORTAL_FILE"}
                    """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filingChannel").value("PORTAL_FILE"));

        assertThat(workflowRepository.findById(workflowId).orElseThrow()
                .getFilingChannel()).isEqualTo("PORTAL_FILE");
    }

    @Test
    void filing_channelDefaultsToPortalFile() throws Exception {
        String ownerJwt = signupAndLogin("puownerf", "puownerf@test.com", "owner");
        UUID orgId = createOrganization(ownerJwt);
        ownerJwt = selectOrg(ownerJwt, orgId);
        String accountantJwt = memberJwt(ownerJwt, orgId, "accountant",
                "puaccountantf", "puaccountantf@test.com");

        UUID workflowId = startedGstr1Workflow(accountantJwt);
        setStage(workflowId, WorkflowStage.FINALIZED);

        mockMvc.perform(post("/api/v1/workflows/{id}/filing", workflowId)
                .header("Authorization", "Bearer " + accountantJwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"ref":"ARN456","date":"2026-05-11"}
                    """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filingChannel").value("PORTAL_FILE"));
    }

    @Test
    void filingOperatorForbidden() throws Exception {
        String ownerJwt = signupAndLogin("puownerg", "puownerg@test.com", "owner");
        UUID orgId = createOrganization(ownerJwt);
        ownerJwt = selectOrg(ownerJwt, orgId);
        String operatorJwt = memberJwt(ownerJwt, orgId, "operator",
                "puoperatorg", "puoperatorg@test.com");

        UUID workflowId = startedGstr1Workflow(operatorJwt);
        setStage(workflowId, WorkflowStage.FINALIZED);

        mockMvc.perform(post("/api/v1/workflows/{id}/filing", workflowId)
                .header("Authorization", "Bearer " + operatorJwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"ref":"ARN789","date":"2026-05-12"}
                    """))
                .andExpect(status().isForbidden());
    }
}
