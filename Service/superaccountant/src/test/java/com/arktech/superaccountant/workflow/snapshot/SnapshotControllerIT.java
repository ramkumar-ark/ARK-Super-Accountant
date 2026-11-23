package com.arktech.superaccountant.workflow.snapshot;

import com.arktech.superaccountant.daybook.models.DayBookUploadJob;
import com.arktech.superaccountant.masters.models.Organization;
import com.arktech.superaccountant.masters.models.UploadJobStatus;
import com.arktech.superaccountant.masters.repository.OrganizationRepository;
import com.arktech.superaccountant.workflow.models.Workflow;
import com.arktech.superaccountant.workflow.models.WorkflowStage;
import com.arktech.superaccountant.workflow.models.WorkflowType;
import com.arktech.superaccountant.workflow.repository.WorkflowRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Change-list and amendment follow-up endpoints. Data is seeded directly;
 * the full compare/accept flows are covered by the other snapshot ITs.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Transactional
@TestPropertySource(properties = "JWT_SECRET=test-jwt-secret-must-be-at-least-32-characters-long")
class SnapshotControllerIT {

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
    private com.arktech.superaccountant.daybook.repository.DayBookUploadJobRepository dayBookUploadJobRepository;
    @Autowired
    private TamperChangeRepository tamperChangeRepository;
    @Autowired
    private AmendmentFollowUpRepository amendmentFollowUpRepository;
    @Autowired
    private com.arktech.superaccountant.gstr1.repository.PreparedReturnRepository preparedReturnRepository;
    @Autowired
    private com.arktech.superaccountant.gstr1.repository.PreparedReturnRowRepository preparedReturnRowRepository;
    @Autowired
    private WorkflowRepository workflowRepository;
    @Autowired
    private OrganizationRepository organizationRepository;
    @Autowired
    private com.arktech.superaccountant.masters.repository.UploadJobRepository uploadJobRepository;

    private final ObjectMapper json = new ObjectMapper();

    private UUID orgId;
    private String ownerToken;
    private String accountantToken;
    private String operatorToken;
    private String auditorToken;

    @Test
    void changes_listsInformationalAndBlocking_blockingFirst() throws Exception {
        setup();
        DayBookUploadJob job = completedJob();
        DayBookUploadJob other = completedJob();
        TamperChange blocking = change(job.getId(), true, LocalDate.of(2025, 4, 20));
        change(other.getId(), false, LocalDate.of(2025, 4, 10));

        mockMvc.perform(get("/api/v1/day-book/uploads/" + job.getId() + "/changes")
                        .header("Authorization", "Bearer " + accountantToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changes.length()").value(1))
                .andExpect(jsonPath("$.changes[0].id").value(blocking.getId().toString()))
                .andExpect(jsonPath("$.changes[0].changeKind").value("MODIFIED"))
                .andExpect(jsonPath("$.changes[0].blocking").value(true))
                .andExpect(jsonPath("$.changes[0].touchedScopes.length()").value(2))
                .andExpect(jsonPath("$.changes[0].touchedScopes[0]").value("GSTR1"));
    }

    @Test
    void changes_otherOrgUpload_404() throws Exception {
        setup();
        DayBookUploadJob otherOrgJob = completedJob(UUID.randomUUID());

        mockMvc.perform(get("/api/v1/day-book/uploads/" + otherOrgJob.getId() + "/changes")
                        .header("Authorization", "Bearer " + accountantToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Upload not found"));
    }

    @Test
    void changes_auditorCanRead_200() throws Exception {
        setup();
        DayBookUploadJob job = completedJob();
        change(job.getId(), true, LocalDate.of(2025, 4, 20));

        mockMvc.perform(get("/api/v1/day-book/uploads/" + job.getId() + "/changes")
                        .header("Authorization", "Bearer " + auditorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changes.length()").value(1));
    }

    @Test
    void amendments_listedForAffectedWorkflow() throws Exception {
        setup();
        Workflow w = workflow(WorkflowStage.STARTED);
        followUp(w.getId(), AmendmentStatus.OPEN, null, null, null);

        mockMvc.perform(get("/api/v1/workflows/" + w.getId() + "/amendments")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.followUps.length()").value(1))
                .andExpect(jsonPath("$.followUps[0].voucherKey").value("G:g-1"))
                .andExpect(jsonPath("$.followUps[0].status").value("OPEN"))
                .andExpect(jsonPath("$.followUps[0].summary").value("Sales SB/1 was modified"));
    }

    @Test
    void amendments_includeProducedRowRef_whenARowExists() throws Exception {
        setup();
        Workflow w = workflow(WorkflowStage.STARTED);
        AmendmentFollowUp withRow = followUp(w.getId(), AmendmentStatus.OPEN, null, null, null);
        AmendmentFollowUp withoutRow = followUp(w.getId(), AmendmentStatus.OPEN, null, null, null);

        Workflow later = new Workflow();
        later.setOrganizationId(orgId);
        later.setType(WorkflowType.GSTR1);
        later.setPeriodStart(LocalDate.of(2025, 5, 1));
        later.setPeriodEnd(LocalDate.of(2025, 5, 31));
        later.setPeriodKey("2025-05");
        later.setFinancialYear(2025);
        later.setStage(WorkflowStage.STARTED);
        later.setActive(true);
        later.setCreatedBy("acc");
        later = workflowRepository.save(later);
        com.arktech.superaccountant.gstr1.models.PreparedReturn prepared =
                new com.arktech.superaccountant.gstr1.models.PreparedReturn();
        prepared.setWorkflowId(later.getId());
        prepared.setReturnKind("GSTR1");
        prepared.setBuiltAt(java.time.Instant.now());
        prepared = preparedReturnRepository.save(prepared);
        com.arktech.superaccountant.gstr1.models.PreparedReturnRow row =
                new com.arktech.superaccountant.gstr1.models.PreparedReturnRow();
        row.setPreparedReturnId(prepared.getId());
        row.setTableCode("B2BA");
        row.setRowKey("b2ba:G:g-1");
        row.setPayload("{}");
        row.setAmendment(true);
        row.setFollowUpId(withRow.getId());
        preparedReturnRowRepository.save(row);

        mockMvc.perform(get("/api/v1/workflows/" + w.getId() + "/amendments")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.followUps.length()").value(2))
                .andExpect(jsonPath("$.followUps[0].producedRow.tableCode").value("B2BA"))
                .andExpect(jsonPath("$.followUps[0].producedRow.rowKey").value("b2ba:G:g-1"))
                .andExpect(jsonPath("$.followUps[0].producedRow.lateReporting").value(false))
                .andExpect(jsonPath("$.followUps[1].producedRow").doesNotExist());
    }

    @Test
    void markDone_accountant_setsDone() throws Exception {
        setup();
        Workflow w = workflow(WorkflowStage.STARTED);
        AmendmentFollowUp f = followUp(w.getId(), AmendmentStatus.OPEN, null, null, null);

        mockMvc.perform(post("/api/v1/workflows/" + w.getId() + "/amendments/" + f.getId() + "/done")
                        .header("Authorization", "Bearer " + accountantToken)
                        .contentType("application/json")
                        .content("{\"note\":\"Amended in GSTR-1 May\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(f.getId().toString()))
                .andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.doneNote").value("Amended in GSTR-1 May"))
                .andExpect(jsonPath("$.doneBy").isNotEmpty());
    }

    @Test
    void markDone_operator_403() throws Exception {
        setup();
        Workflow w = workflow(WorkflowStage.STARTED);
        AmendmentFollowUp f = followUp(w.getId(), AmendmentStatus.OPEN, null, null, null);

        mockMvc.perform(post("/api/v1/workflows/" + w.getId() + "/amendments/" + f.getId() + "/done")
                        .header("Authorization", "Bearer " + operatorToken)
                        .contentType("application/json")
                        .content("{\"note\":\"trying\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error")
                        .value("Only an accountant can close an amendment follow-up."));
    }

    @Test
    void markDone_blankNote_400() throws Exception {
        setup();
        Workflow w = workflow(WorkflowStage.STARTED);
        AmendmentFollowUp f = followUp(w.getId(), AmendmentStatus.OPEN, null, null, null);

        mockMvc.perform(post("/api/v1/workflows/" + w.getId() + "/amendments/" + f.getId() + "/done")
                        .header("Authorization", "Bearer " + accountantToken)
                        .contentType("application/json")
                        .content("{\"note\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("A note is required."));
    }

    @Test
    void markDone_twice_409() throws Exception {
        setup();
        Workflow w = workflow(WorkflowStage.STARTED);
        AmendmentFollowUp f = followUp(w.getId(), AmendmentStatus.OPEN, null, null, null);

        mockMvc.perform(post("/api/v1/workflows/" + w.getId() + "/amendments/" + f.getId() + "/done")
                        .header("Authorization", "Bearer " + accountantToken)
                        .contentType("application/json")
                        .content("{\"note\":\"first\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/workflows/" + w.getId() + "/amendments/" + f.getId() + "/done")
                        .header("Authorization", "Bearer " + accountantToken)
                        .contentType("application/json")
                        .content("{\"note\":\"second\"}"))
                .andExpect(status().isConflict());
    }

    // -- helpers ------------------------------------------------------------

    private void setup() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String username = "s5_" + suffix;
        mockMvc.perform(post("/api/auth/signup")
                        .contentType("application/json")
                        .content("{\"username\":\"" + username + "\",\"email\":\"" + username
                                + "@test.com\",\"password\":\"TestPass1!\",\"role\":\"owner\"}"))
                .andExpect(status().isOk());
        ownerToken = signin(username);
        orgId = createOrg(ownerToken, suffix);
        ownerToken = selectOrg(ownerToken);
        accountantToken = memberJwt("accountant");
        operatorToken = memberJwt("operator");
        auditorToken = memberJwt("auditor_ca");
    }

    private String memberJwt(String role) throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String username = "m5_" + suffix;
        mockMvc.perform(post("/api/auth/signup")
                        .contentType("application/json")
                        .content("{\"username\":\"" + username + "\",\"email\":\"" + username
                                + "@test.com\",\"password\":\"TestPass1!\",\"role\":\"" + role + "\"}"))
                .andExpect(status().isOk());
        MvcResult inviteResult = mockMvc.perform(post("/api/organizations/" + orgId + "/invites")
                        .param("role", role)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andReturn();
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
                .andExpect(status().isOk())
                .andReturn();
        return (String) json.readValue(signinResult.getResponse().getContentAsString(), Map.class).get("token");
    }

    private UUID createOrg(String token, String suffix) throws Exception {
        MvcResult createOrgResult = mockMvc.perform(post("/api/organizations")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"name\":\"Snapshot S5 Org " + suffix + "\",\"pan\":\"AAACA1234A\",\"financialYearStart\":4}"))
                .andExpect(status().isCreated())
                .andReturn();
        UUID id = UUID.fromString(json.readValue(createOrgResult.getResponse().getContentAsString(),
                Map.class).get("id").toString());
        Organization org = organizationRepository.findById(id).orElseThrow();
        org.setBooksStartDate(LocalDate.of(2025, 4, 1));
        organizationRepository.save(org);
        return id;
    }

    private String selectOrg(String token) throws Exception {
        MvcResult selectResult = mockMvc.perform(post("/api/organizations/" + orgId + "/select")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return (String) json.readValue(selectResult.getResponse().getContentAsString(), Map.class).get("token");
    }

    private DayBookUploadJob completedJob() {
        return completedJob(orgId);
    }

    private DayBookUploadJob completedJob(UUID org) {
        DayBookUploadJob job = new DayBookUploadJob();
        job.setOrganizationId(org);
        job.setFileName("daybook.xml");
        job.setUploadedBy("acc");
        job.setStatus(UploadJobStatus.COMPLETED);
        job.setCoverageFrom(LocalDate.of(2025, 4, 1));
        job.setCoverageTo(LocalDate.of(2025, 5, 31));
        return dayBookUploadJobRepository.save(job);
    }

    private TamperChange change(UUID jobId, boolean blocking, LocalDate changeDate) {
        TamperChange c = new TamperChange();
        c.setOrganizationId(orgId);
        c.setUploadJobId(jobId);
        c.setVoucherKey("G:g-" + changeDate.getDayOfMonth());
        c.setChangeKind(TamperChangeKind.MODIFIED);
        c.setOldHash("h1");
        c.setNewHash("h2");
        c.setOldDate(changeDate);
        c.setVoucherTypeName("Sales");
        c.setVoucherNumber("SB/" + changeDate.getDayOfMonth());
        c.setPartyLedgerName("Client ABC");
        c.setTouchedScopes("GSTR1,GSTR3B");
        c.setBlocking(blocking);
        return tamperChangeRepository.save(c);
    }

    private Workflow workflow(WorkflowStage stage) {
        Workflow w = new Workflow();
        w.setOrganizationId(orgId);
        w.setType(WorkflowType.GSTR1);
        w.setPeriodStart(LocalDate.of(2025, 4, 1));
        w.setPeriodEnd(LocalDate.of(2025, 4, 30));
        w.setPeriodKey("2025-04");
        w.setFinancialYear(2025);
        w.setStage(stage);
        w.setActive(true);
        w.setCreatedBy("acc");
        return workflowRepository.save(w);
    }

    private AmendmentFollowUp followUp(UUID workflowId, AmendmentStatus status,
                                       String doneBy, java.time.Instant doneAt, String doneNote) {
        AmendmentFollowUp f = new AmendmentFollowUp();
        f.setOrganizationId(orgId);
        f.setWorkflowId(workflowId);
        f.setTamperFindingId(UUID.randomUUID());
        f.setVoucherKey("G:g-1");
        f.setSummary("Sales SB/1 was modified");
        f.setStatus(status);
        f.setDoneBy(doneBy);
        f.setDoneAt(doneAt);
        f.setDoneNote(doneNote);
        return amendmentFollowUpRepository.save(f);
    }

    @Test
    void mastersChanges_listsMasterChangesForUpload() throws Exception {
        setup();
        com.arktech.superaccountant.masters.models.UploadJob masters = mastersJob();
        TamperChange c = masterChange(masters.getId(), true, "29ABCDE1234F1Z5", "29ABCDE1234F1Z6");

        mockMvc.perform(get("/api/v1/masters/uploads/" + masters.getId() + "/changes")
                        .header("Authorization", "Bearer " + accountantToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changes.length()").value(1))
                .andExpect(jsonPath("$.changes[0].id").value(c.getId().toString()))
                .andExpect(jsonPath("$.changes[0].changeKind").value("MASTER_MODIFIED"))
                .andExpect(jsonPath("$.changes[0].ledgerName").value("Acme Traders"))
                .andExpect(jsonPath("$.changes[0].attributeName").value("gstin"))
                .andExpect(jsonPath("$.changes[0].oldValue").value("29ABCDE1234F1Z5"))
                .andExpect(jsonPath("$.changes[0].newValue").value("29ABCDE1234F1Z6"))
                .andExpect(jsonPath("$.changes[0].touchedScopes[0]").value("GSTR1"))
                .andExpect(jsonPath("$.changes[0].blocking").value(true))
                .andExpect(jsonPath("$.changes[0].createdAt").isNotEmpty());
    }

    @Test
    void mastersChanges_otherOrgUpload_404() throws Exception {
        setup();
        com.arktech.superaccountant.masters.models.UploadJob otherOrgJob =
                new com.arktech.superaccountant.masters.models.UploadJob();
        otherOrgJob.setOrganizationId(UUID.randomUUID());
        otherOrgJob.setFileName("other.json");
        otherOrgJob.setStatus(UploadJobStatus.COMPLETED);
        otherOrgJob = uploadJobRepository.save(otherOrgJob);

        mockMvc.perform(get("/api/v1/masters/uploads/" + otherOrgJob.getId() + "/changes")
                        .header("Authorization", "Bearer " + accountantToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Upload not found"));
    }

    private com.arktech.superaccountant.masters.models.UploadJob mastersJob() {
        com.arktech.superaccountant.masters.models.UploadJob job =
                new com.arktech.superaccountant.masters.models.UploadJob();
        job.setOrganizationId(orgId);
        job.setFileName("masters.json");
        job.setStatus(UploadJobStatus.COMPLETED);
        return uploadJobRepository.save(job);
    }

    private TamperChange masterChange(UUID jobId, boolean blocking, String oldValue, String newValue) {
        TamperChange c = new TamperChange();
        c.setOrganizationId(orgId);
        c.setUploadJobId(jobId);
        c.setChangeKind(TamperChangeKind.MASTER_MODIFIED);
        c.setLedgerName("Acme Traders");
        c.setAttributeName("gstin");
        c.setOldValue(oldValue);
        c.setNewValue(newValue);
        c.setTouchedScopes("GSTR1,GSTR3B");
        c.setBlocking(blocking);
        c.setCreatedAt(java.time.Instant.now());
        return tamperChangeRepository.save(c);
    }
}
