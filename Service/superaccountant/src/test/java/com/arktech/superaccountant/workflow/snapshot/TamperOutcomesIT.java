package com.arktech.superaccountant.workflow.snapshot;

import com.arktech.superaccountant.daybook.models.DayBookUploadJob;
import com.arktech.superaccountant.daybook.models.ParsedVoucher;
import com.arktech.superaccountant.daybook.models.ParsedVoucherEntry;
import com.arktech.superaccountant.daybook.repository.ParsedVoucherRepository;
import com.arktech.superaccountant.login.models.ERole;
import com.arktech.superaccountant.login.models.Role;
import com.arktech.superaccountant.login.models.User;
import com.arktech.superaccountant.login.repository.RoleRepository;
import com.arktech.superaccountant.login.repository.UserRepository;
import com.arktech.superaccountant.masters.models.Organization;
import com.arktech.superaccountant.masters.models.ResolveStatus;
import com.arktech.superaccountant.masters.models.UploadJobStatus;
import com.arktech.superaccountant.masters.repository.OrganizationRepository;
import com.arktech.superaccountant.masters.repository.UserOrganizationRepository;
import com.arktech.superaccountant.review.EvidenceHash;
import com.arktech.superaccountant.workflow.models.Workflow;
import com.arktech.superaccountant.workflow.models.WorkflowEvent;
import com.arktech.superaccountant.workflow.models.WorkflowEventKind;
import com.arktech.superaccountant.workflow.models.WorkflowStage;
import com.arktech.superaccountant.workflow.models.WorkflowType;
import com.arktech.superaccountant.workflow.repository.WorkflowEventRepository;
import com.arktech.superaccountant.workflow.repository.WorkflowRepository;
import com.arktech.superaccountant.workflow.seam.SnapshotService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Accept/restore outcomes over HTTP. Not @Transactional: compare() runs
 * REQUIRES_NEW and would not see an uncommitted test transaction; every test
 * uses its own org and members.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = "JWT_SECRET=test-jwt-secret-must-be-at-least-32-characters-long")
class TamperOutcomesIT {

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
    private TamperComparisonService tamperComparisonService;
    @Autowired
    private SnapshotService snapshotService;
    @Autowired
    private OrganizationRepository organizationRepository;
    @Autowired
    private UserOrganizationRepository userOrganizationRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private com.arktech.superaccountant.daybook.repository.DayBookUploadJobRepository dayBookUploadJobRepository;
    @Autowired
    private ParsedVoucherRepository parsedVoucherRepository;
    @Autowired
    private WorkflowRepository workflowRepository;
    @Autowired
    private WorkflowEventRepository workflowEventRepository;
    @Autowired
    private FrozenVoucherRepository frozenVoucherRepository;
    @Autowired
    private TamperFindingRepository tamperFindingRepository;
    @Autowired
    private TamperChangeRepository tamperChangeRepository;
    @Autowired
    private com.arktech.superaccountant.workflow.snapshot.AmendmentFollowUpRepository amendmentFollowUpRepository;
    @Autowired
    private com.arktech.superaccountant.gstr1.repository.PreparedReturnRepository preparedReturnRepository;
    @Autowired
    private com.arktech.superaccountant.gstr1.repository.PreparedReturnRowRepository preparedReturnRowRepository;

    private final ObjectMapper json = new ObjectMapper();

    private UUID orgId;
    private String ownerToken;
    private String operatorToken;
    private String accountantToken;
    private String auditorToken;
    private Workflow gstr1Apr;
    private Workflow gstr3bApr;

    /** Seeds a finalized GSTR1 April, an open GSTR3B April and a blocking MODIFIED finding. */
    private void seedModifiedFinding(String originalHash, String mutatedHash) throws Exception {
        ownerToken = setupOwnerOrg();
        Organization org = organizationRepository.findById(orgId).orElseThrow();
        DayBookUploadJob job1 = completedJob(org.getId(), LocalDate.of(2025, 4, 1), LocalDate.of(2025, 5, 31));
        seedVoucher(org.getId(), job1.getId(), "g-1", "Sales", "SB/001", LocalDate.of(2025, 4, 15), originalHash,
                entry("Sales Account", "100"));
        gstr1Apr = workflow(org.getId(), WorkflowType.GSTR1, LocalDate.of(2025, 4, 1), LocalDate.of(2025, 4, 30),
                WorkflowStage.FINALIZED, job1.getId(), false);
        snapshotService.freeze(gstr1Apr.getId(), org.getId(), "acc");
        gstr3bApr = workflow(org.getId(), WorkflowType.GSTR3B, LocalDate.of(2025, 4, 1), LocalDate.of(2025, 4, 30),
                WorkflowStage.STARTED, null, false);

        operatorToken = memberJwt("operator");
        accountantToken = memberJwt("accountant");
        auditorToken = memberJwt("auditor_ca");

        DayBookUploadJob job2 = completedJob(org.getId(), LocalDate.of(2025, 4, 1), LocalDate.of(2025, 5, 31));
        seedVoucher(org.getId(), job2.getId(), "g-1", "Sales", "SB/001", LocalDate.of(2025, 4, 15), mutatedHash,
                entry("Sales Account", "200"));
        tamperComparisonService.compare(job2.getId());
    }

    private TamperFinding theFinding() {
        return tamperFindingRepository.findByOrganizationId(orgId).get(0);
    }

    private void act(String token, String action, String note) throws Exception {
        mockMvc.perform(post("/api/v1/workflows/" + gstr3bApr.getId() + "/findings/"
                        + theFinding().getId() + "/action")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"action\":\"" + action + "\"" + (note == null ? "" : ",\"note\":\"" + note + "\"") + "}"))
                .andReturn();
    }

    private String actStatus(String token, String action, String note) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/workflows/" + gstr3bApr.getId() + "/findings/"
                        + theFinding().getId() + "/action")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"action\":\"" + action + "\"" + (note == null ? "" : ",\"note\":\"" + note + "\"") + "}"))
                .andReturn();
        return String.valueOf(result.getResponse().getStatus());
    }

    @Test
    void operatorAcknowledge_requiresNote_400() throws Exception {
        seedModifiedFinding("h1", "h2");
        assertThat(actStatus(operatorToken, "ACKNOWLEDGE", null)).isEqualTo("400");
        assertThat(actStatus(operatorToken, "ACKNOWLEDGE", "  ")).isEqualTo("400");
    }

    @Test
    void operatorAcknowledge_thenAccountantApprove_rewritesSnapshotAndRecordsFollowUp() throws Exception {
        seedModifiedFinding("h1", "h2");
        TamperFinding finding = theFinding();

        assertThat(actStatus(operatorToken, "ACKNOWLEDGE", "looks intentional")).isEqualTo("200");
        finding = theFinding();
        assertThat(finding.getResolveStatus()).isEqualTo(ResolveStatus.ACKNOWLEDGED);

        assertThat(actStatus(accountantToken, "APPROVE", "accept as correction")).isEqualTo("200");
        finding = theFinding();
        assertThat(finding.getResolveStatus()).isEqualTo(ResolveStatus.APPROVED);

        List<FrozenVoucher> live = frozenVoucherRepository.findByOrganizationIdAndReleasedAtIsNull(orgId);
        assertThat(live).hasSize(1);
        assertThat(live.get(0).getContentHash()).isEqualTo("h2");
        assertThat(live.get(0).getFrozenByWorkflowId()).isEqualTo(gstr1Apr.getId());

        List<AmendmentFollowUp> followUps = amendmentFollowUpRepository
                .findByWorkflowIdOrderByCreatedAt(gstr1Apr.getId());
        assertThat(followUps).hasSize(1);
        assertThat(followUps.get(0).getStatus()).isEqualTo(AmendmentStatus.OPEN);
        assertThat(followUps.get(0).getTamperFindingId()).isEqualTo(finding.getId());
        assertThat(followUps.get(0).getSummary()).isEqualTo(finding.getMessage());

        List<WorkflowEvent> events = workflowEventRepository
                .findAllByWorkflowIdOrderByOccurredAtAsc(gstr1Apr.getId()).stream()
                .filter(e -> e.getKind() == WorkflowEventKind.AMENDMENT_RECORDED)
                .toList();
        assertThat(events).hasSize(1);

        mockMvc.perform(get("/api/v1/workflows/" + gstr3bApr.getId() + "/findings")
                        .header("Authorization", "Bearer " + accountantToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blockingCount").value(0));
    }

    @Test
    void accountantAcknowledge_approvesInOneStep() throws Exception {
        seedModifiedFinding("h1", "h2");
        assertThat(actStatus(accountantToken, "ACKNOWLEDGE", "accept as correction")).isEqualTo("200");
        assertThat(theFinding().getResolveStatus()).isEqualTo(ResolveStatus.APPROVED);
        assertThat(amendmentFollowUpRepository.findByWorkflowIdOrderByCreatedAt(gstr1Apr.getId())).hasSize(1);
    }

    @Test
    void acceptDeletion_releasesFrozenRow() throws Exception {
        ownerToken = setupOwnerOrg();
        Organization org = organizationRepository.findById(orgId).orElseThrow();
        DayBookUploadJob job1 = completedJob(org.getId(), LocalDate.of(2025, 4, 1), LocalDate.of(2025, 5, 31));
        seedVoucher(org.getId(), job1.getId(), "g-1", "Sales", "SB/001", LocalDate.of(2025, 4, 15), "h1",
                entry("Sales Account", "100"));
        gstr1Apr = workflow(org.getId(), WorkflowType.GSTR1, LocalDate.of(2025, 4, 1), LocalDate.of(2025, 4, 30),
                WorkflowStage.FINALIZED, job1.getId(), false);
        snapshotService.freeze(gstr1Apr.getId(), org.getId(), "acc");
        gstr3bApr = workflow(org.getId(), WorkflowType.GSTR3B, LocalDate.of(2025, 4, 1), LocalDate.of(2025, 4, 30),
                WorkflowStage.STARTED, null, false);
        accountantToken = memberJwt("accountant");

        DayBookUploadJob job2 = completedJob(org.getId(), LocalDate.of(2025, 4, 1), LocalDate.of(2025, 5, 31));
        seedVoucher(org.getId(), job2.getId(), "g-9", "Sales", "SB/009", LocalDate.of(2025, 5, 3), "h9",
                entry("Sales Account", "100"));
        tamperComparisonService.compare(job2.getId());

        assertThat(actStatus(accountantToken, "ACKNOWLEDGE", "accept the deletion")).isEqualTo("200");

        List<FrozenVoucher> live = frozenVoucherRepository.findByOrganizationIdAndReleasedAtIsNull(orgId);
        assertThat(live).isEmpty();
        List<FrozenVoucher> all = frozenVoucherRepository.findByOrganizationId(orgId);
        assertThat(all).hasSize(1);
        assertThat(all.get(0).getReleasedAt()).isNotNull();
        assertThat(amendmentFollowUpRepository.findByWorkflowIdOrderByCreatedAt(gstr1Apr.getId())).hasSize(1);
    }

    @Test
    void acceptInsertion_addsFrozenRow() throws Exception {
        ownerToken = setupOwnerOrg();
        Organization org = organizationRepository.findById(orgId).orElseThrow();
        DayBookUploadJob job1 = completedJob(org.getId(), LocalDate.of(2025, 4, 1), LocalDate.of(2025, 5, 31));
        seedVoucher(org.getId(), job1.getId(), "g-1", "Sales", "SB/001", LocalDate.of(2025, 4, 15), "h1",
                entry("Sales Account", "100"));
        gstr1Apr = workflow(org.getId(), WorkflowType.GSTR1, LocalDate.of(2025, 4, 1), LocalDate.of(2025, 4, 30),
                WorkflowStage.FINALIZED, job1.getId(), false);
        snapshotService.freeze(gstr1Apr.getId(), org.getId(), "acc");
        gstr3bApr = workflow(org.getId(), WorkflowType.GSTR3B, LocalDate.of(2025, 4, 1), LocalDate.of(2025, 4, 30),
                WorkflowStage.STARTED, null, false);
        accountantToken = memberJwt("accountant");

        DayBookUploadJob job2 = completedJob(org.getId(), LocalDate.of(2025, 4, 1), LocalDate.of(2025, 5, 31));
        seedVoucher(org.getId(), job2.getId(), "g-1", "Sales", "SB/001", LocalDate.of(2025, 4, 15), "h1",
                entry("Sales Account", "100"));
        seedVoucher(org.getId(), job2.getId(), "g-9", "Sales", "SB/009", LocalDate.of(2025, 4, 28), "h9",
                entry("Sales Account", "100"));
        tamperComparisonService.compare(job2.getId());

        assertThat(actStatus(accountantToken, "ACKNOWLEDGE", "accept the insertion")).isEqualTo("200");

        List<FrozenVoucher> live = frozenVoucherRepository.findByOrganizationIdAndReleasedAtIsNull(orgId);
        assertThat(live).hasSize(2);
        FrozenVoucher added = live.stream()
                .filter(v -> "SB/009".equals(v.getVoucherNumber())).findFirst().orElseThrow();
        assertThat(added.getContentHash()).isEqualTo("h9");
        assertThat(added.getFrozenByWorkflowId()).isEqualTo(gstr1Apr.getId());
        assertThat(amendmentFollowUpRepository.findByWorkflowIdOrderByCreatedAt(gstr1Apr.getId())).hasSize(1);
    }

    @Test
    void reject_returnsToOpen_keepsNote() throws Exception {
        seedModifiedFinding("h1", "h2");
        assertThat(actStatus(operatorToken, "ACKNOWLEDGE", "looks intentional")).isEqualTo("200");
        assertThat(actStatus(accountantToken, "REJECT", "needs a second look")).isEqualTo("200");
        TamperFinding finding = theFinding();
        assertThat(finding.getResolveStatus()).isEqualTo(ResolveStatus.OPEN);
        assertThat(finding.getReviewNote()).isEqualTo("needs a second look");
    }

    @Test
    void ownerAndAuditor_actionForbidden_403() throws Exception {
        seedModifiedFinding("h1", "h2");
        assertThat(actStatus(ownerToken, "ACKNOWLEDGE", "note")).isEqualTo("403");
        assertThat(actStatus(auditorToken, "ACKNOWLEDGE", "note")).isEqualTo("403");
    }

    @Test
    void afterAccept_sameUploadComparedAgain_noNewFinding() throws Exception {
        seedModifiedFinding("h1", "h2");
        assertThat(actStatus(accountantToken, "ACKNOWLEDGE", "accept as correction")).isEqualTo("200");

        Organization org = organizationRepository.findById(orgId).orElseThrow();
        DayBookUploadJob job3 = completedJob(org.getId(), LocalDate.of(2025, 4, 1), LocalDate.of(2025, 5, 31));
        seedVoucher(org.getId(), job3.getId(), "g-1", "Sales", "SB/001", LocalDate.of(2025, 4, 15), "h2",
                entry("Sales Account", "200"));
        tamperComparisonService.compare(job3.getId());

        assertThat(tamperChangeRepository.findByOrganizationId(orgId)).hasSize(1);
        assertThat(tamperFindingRepository.findByOrganizationId(orgId)).hasSize(1);
        assertThat(theFinding().getResolveStatus()).isEqualTo(ResolveStatus.APPROVED);
    }

    @Test
    void compare_restoredInTally_autoResolves() throws Exception {
        seedModifiedFinding("h1", "h2");
        TamperFinding before = theFinding();

        Organization org = organizationRepository.findById(orgId).orElseThrow();
        DayBookUploadJob job3 = completedJob(org.getId(), LocalDate.of(2025, 4, 1), LocalDate.of(2025, 5, 31));
        seedVoucher(org.getId(), job3.getId(), "g-1", "Sales", "SB/001", LocalDate.of(2025, 4, 15), "h1",
                entry("Sales Account", "100"));
        tamperComparisonService.compare(job3.getId());

        TamperFinding after = theFinding();
        assertThat(after.getId()).isEqualTo(before.getId());
        assertThat(after.getResolveStatus()).isEqualTo(ResolveStatus.RESOLVED);
        assertThat(after.getResolvedBy()).isEqualTo("system");
        assertThat(after.getResolveNote()).isEqualTo("Restored: no longer found in upload " + job3.getId());
    }

    @Test
    void compare_restoreOutsideCoverage_staysOpen() throws Exception {
        seedModifiedFinding("h1", "h2");

        Organization org = organizationRepository.findById(orgId).orElseThrow();
        DayBookUploadJob job3 = completedJob(org.getId(), LocalDate.of(2025, 5, 1), LocalDate.of(2025, 5, 31));
        seedVoucher(org.getId(), job3.getId(), "g-2", "Sales", "SB/002", LocalDate.of(2025, 5, 10), "h2b",
                entry("Sales Account", "100"));
        tamperComparisonService.compare(job3.getId());

        assertThat(theFinding().getResolveStatus()).isEqualTo(ResolveStatus.OPEN);
    }

    @Test
    void compare_changedEvidence_resetsToOpenAndClearsProposal() throws Exception {
        seedModifiedFinding("h1", "h2");
        assertThat(actStatus(operatorToken, "ACKNOWLEDGE", "looks intentional")).isEqualTo("200");
        TamperFinding acknowledged = theFinding();
        assertThat(acknowledged.getResolveStatus()).isEqualTo(ResolveStatus.ACKNOWLEDGED);
        String originalEvidence = acknowledged.getEvidenceHash();

        Organization org = organizationRepository.findById(orgId).orElseThrow();
        DayBookUploadJob job3 = completedJob(org.getId(), LocalDate.of(2025, 4, 1), LocalDate.of(2025, 5, 31));
        seedVoucher(org.getId(), job3.getId(), "g-1", "Sales", "SB/001", LocalDate.of(2025, 4, 15), "h3",
                entry("Sales Account", "300"));
        tamperComparisonService.compare(job3.getId());

        TamperFinding after = theFinding();
        assertThat(after.getId()).isEqualTo(acknowledged.getId());
        assertThat(after.getResolveStatus()).isEqualTo(ResolveStatus.OPEN);
        assertThat(after.getProposedValue()).isNull();
        assertThat(after.getSubmittedBy()).isNull();
        assertThat(after.getSubmittedAt()).isNull();
        assertThat(after.getReviewedBy()).isNull();
        assertThat(after.getReviewedAt()).isNull();
        assertThat(after.getReviewNote()).isNull();
        assertThat(after.getCarriedFromFindingId()).isNull();
        assertThat(after.getOldHash()).isEqualTo("h1");
        assertThat(after.getNewHash()).isEqualTo("h3");
        assertThat(after.getEvidenceHash()).isNotEqualTo(originalEvidence);
        assertThat(after.getEvidenceHash()).isEqualTo(EvidenceHash.of("TAMPER_MODIFIED", after.getVoucherKey(),
                "h1", "h3", "2025-04-15"));
    }

    @Test
    void acceptModified_followUpRecordsStructuredOrigin() throws Exception {
        seedModifiedFinding("h1", "h2");
        seedPreparedReturn(gstr1Apr.getId(), "B2B", "B2B/1", "G:g-1");

        assertThat(actStatus(accountantToken, "ACKNOWLEDGE", "accept as correction")).isEqualTo("200");

        List<AmendmentFollowUp> followUps = amendmentFollowUpRepository
                .findByWorkflowIdOrderByCreatedAt(gstr1Apr.getId());
        assertThat(followUps).hasSize(1);
        AmendmentFollowUp followUp = followUps.get(0);
        assertThat(followUp.getChange()).isEqualTo(AmendmentChangeKind.CHANGED);
        assertThat(followUp.getTableCode()).isEqualTo("B2B");
        assertThat(followUp.getOriginalDocKey()).isEqualTo("B2B/1");
        assertThat(followUp.getOriginalWorkflowId()).isEqualTo(gstr1Apr.getId());

        mockMvc.perform(get("/api/v1/workflows/" + gstr1Apr.getId() + "/amendments")
                        .header("Authorization", "Bearer " + accountantToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.followUps[0].change").value("CHANGED"))
                .andExpect(jsonPath("$.followUps[0].tableCode").value("B2B"))
                .andExpect(jsonPath("$.followUps[0].originalDocKey").value("B2B/1"));
    }

    @Test
    void acceptModified_noPreparedReturnRow_followUpRecordsNulls() throws Exception {
        seedModifiedFinding("h1", "h2");

        assertThat(actStatus(accountantToken, "ACKNOWLEDGE", "accept as correction")).isEqualTo("200");

        List<AmendmentFollowUp> followUps = amendmentFollowUpRepository
                .findByWorkflowIdOrderByCreatedAt(gstr1Apr.getId());
        assertThat(followUps).hasSize(1);
        assertThat(followUps.get(0).getChange()).isEqualTo(AmendmentChangeKind.CHANGED);
        assertThat(followUps.get(0).getTableCode()).isNull();
        assertThat(followUps.get(0).getOriginalDocKey()).isNull();
        assertThat(followUps.get(0).getOriginalWorkflowId()).isEqualTo(gstr1Apr.getId());
    }

    @Test
    void acceptInsertion_followUpRecordsLate() throws Exception {
        ownerToken = setupOwnerOrg();
        Organization org = organizationRepository.findById(orgId).orElseThrow();
        DayBookUploadJob job1 = completedJob(org.getId(), LocalDate.of(2025, 4, 1), LocalDate.of(2025, 5, 31));
        seedVoucher(org.getId(), job1.getId(), "g-1", "Sales", "SB/001", LocalDate.of(2025, 4, 15), "h1",
                entry("Sales Account", "100"));
        gstr1Apr = workflow(org.getId(), WorkflowType.GSTR1, LocalDate.of(2025, 4, 1), LocalDate.of(2025, 4, 30),
                WorkflowStage.FINALIZED, job1.getId(), false);
        snapshotService.freeze(gstr1Apr.getId(), org.getId(), "acc");
        gstr3bApr = workflow(org.getId(), WorkflowType.GSTR3B, LocalDate.of(2025, 4, 1), LocalDate.of(2025, 4, 30),
                WorkflowStage.STARTED, null, false);
        accountantToken = memberJwt("accountant");

        DayBookUploadJob job2 = completedJob(org.getId(), LocalDate.of(2025, 4, 1), LocalDate.of(2025, 5, 31));
        seedVoucher(org.getId(), job2.getId(), "g-1", "Sales", "SB/001", LocalDate.of(2025, 4, 15), "h1",
                entry("Sales Account", "100"));
        seedVoucher(org.getId(), job2.getId(), "g-9", "Sales", "SB/009", LocalDate.of(2025, 4, 28), "h9",
                entry("Sales Account", "100"));
        tamperComparisonService.compare(job2.getId());

        assertThat(actStatus(accountantToken, "ACKNOWLEDGE", "accept the insertion")).isEqualTo("200");

        List<AmendmentFollowUp> followUps = amendmentFollowUpRepository
                .findByWorkflowIdOrderByCreatedAt(gstr1Apr.getId());
        assertThat(followUps).hasSize(1);
        assertThat(followUps.get(0).getChange()).isEqualTo(AmendmentChangeKind.LATE);
        assertThat(followUps.get(0).getTableCode()).isNull();
        assertThat(followUps.get(0).getOriginalDocKey()).isNull();
        assertThat(followUps.get(0).getOriginalWorkflowId()).isNull();
    }

    @Test
    void acceptDeletion_followUpRecordsDeletedWithOrigin() throws Exception {
        ownerToken = setupOwnerOrg();
        Organization org = organizationRepository.findById(orgId).orElseThrow();
        DayBookUploadJob job1 = completedJob(org.getId(), LocalDate.of(2025, 4, 1), LocalDate.of(2025, 5, 31));
        seedVoucher(org.getId(), job1.getId(), "g-1", "Sales", "SB/001", LocalDate.of(2025, 4, 15), "h1",
                entry("Sales Account", "100"));
        gstr1Apr = workflow(org.getId(), WorkflowType.GSTR1, LocalDate.of(2025, 4, 1), LocalDate.of(2025, 4, 30),
                WorkflowStage.FINALIZED, job1.getId(), false);
        snapshotService.freeze(gstr1Apr.getId(), org.getId(), "acc");
        gstr3bApr = workflow(org.getId(), WorkflowType.GSTR3B, LocalDate.of(2025, 4, 1), LocalDate.of(2025, 4, 30),
                WorkflowStage.STARTED, null, false);
        accountantToken = memberJwt("accountant");
        seedPreparedReturn(gstr1Apr.getId(), "B2B", "B2B/1", "G:g-1");

        DayBookUploadJob job2 = completedJob(org.getId(), LocalDate.of(2025, 4, 1), LocalDate.of(2025, 5, 31));
        seedVoucher(org.getId(), job2.getId(), "g-9", "Sales", "SB/009", LocalDate.of(2025, 5, 3), "h9",
                entry("Sales Account", "100"));
        tamperComparisonService.compare(job2.getId());

        assertThat(actStatus(accountantToken, "ACKNOWLEDGE", "accept the deletion")).isEqualTo("200");

        List<AmendmentFollowUp> followUps = amendmentFollowUpRepository
                .findByWorkflowIdOrderByCreatedAt(gstr1Apr.getId());
        assertThat(followUps).hasSize(1);
        assertThat(followUps.get(0).getChange()).isEqualTo(AmendmentChangeKind.DELETED);
        assertThat(followUps.get(0).getTableCode()).isEqualTo("B2B");
        assertThat(followUps.get(0).getOriginalDocKey()).isEqualTo("B2B/1");
        assertThat(followUps.get(0).getOriginalWorkflowId()).isEqualTo(gstr1Apr.getId());
    }

    private void seedPreparedReturn(UUID workflowId, String tableCode, String rowKey, String voucherKey) {
        com.arktech.superaccountant.gstr1.models.PreparedReturn prepared =
                new com.arktech.superaccountant.gstr1.models.PreparedReturn();
        prepared.setWorkflowId(workflowId);
        prepared.setReturnKind("GSTR1");
        prepared.setBuiltAt(Instant.now());
        prepared = preparedReturnRepository.save(prepared);
        com.arktech.superaccountant.gstr1.models.PreparedReturnRow row =
                new com.arktech.superaccountant.gstr1.models.PreparedReturnRow();
        row.setPreparedReturnId(prepared.getId());
        row.setTableCode(tableCode);
        row.setRowKey(rowKey);
        row.setSourceVoucherKey(voucherKey);
        row.setPayload("{\"rows\":[]}");
        row.setAmendment(false);
        preparedReturnRowRepository.save(row);
    }

    // -- helpers ------------------------------------------------------------

    private String setupOwnerOrg() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String username = "it_" + suffix;
        mockMvc.perform(post("/api/auth/signup")
                        .contentType("application/json")
                        .content("{\"username\":\"" + username + "\",\"email\":\"" + username
                                + "@test.com\",\"password\":\"TestPass1!\",\"role\":\"owner\"}"))
                .andExpect(status().isOk());
        String token = signin(username);
        MvcResult createOrgResult = mockMvc.perform(post("/api/organizations")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"name\":\"Tamper Outcomes Org " + suffix + "\",\"financialYearStart\":4}"))
                .andExpect(status().isCreated())
                .andReturn();
        Map<?, ?> orgBody = json.readValue(createOrgResult.getResponse().getContentAsString(), Map.class);
        orgId = UUID.fromString(orgBody.get("id").toString());
        var org = organizationRepository.findById(orgId).orElseThrow();
        org.setBooksStartDate(LocalDate.of(2025, 4, 1));
        organizationRepository.save(org);
        return selectOrg(token);
    }

    /** Invites a fresh signup into the org and returns its scoped token. */
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

    private String selectOrg(String token) throws Exception {
        MvcResult selectResult = mockMvc.perform(post("/api/organizations/" + orgId + "/select")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
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

    private Workflow workflow(UUID org, WorkflowType type, LocalDate start, LocalDate end,
                              WorkflowStage stage, UUID uploadJobId, boolean confirmedBaseline) {
        Workflow w = new Workflow();
        w.setOrganizationId(org);
        w.setType(type);
        w.setPeriodStart(start);
        w.setPeriodEnd(end);
        w.setPeriodKey(start.toString().substring(0, 7));
        w.setFinancialYear(start.getYear());
        w.setStage(stage);
        w.setActive(true);
        w.setUploadJobId(uploadJobId);
        w.setCreatedBy("acc");
        if (confirmedBaseline) {
            w.setBaselineConfirmedAt(Instant.now());
        }
        return workflowRepository.save(w);
    }
}
