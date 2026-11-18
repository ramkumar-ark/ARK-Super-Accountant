package com.arktech.superaccountant.workflow.snapshot;

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
import com.arktech.superaccountant.masters.models.LedgerCategory;
import com.arktech.superaccountant.masters.models.Organization;
import com.arktech.superaccountant.masters.models.ResolveStatus;
import com.arktech.superaccountant.masters.models.UploadJob;
import com.arktech.superaccountant.masters.models.UploadJobStatus;
import com.arktech.superaccountant.masters.models.UploadLedgerSnapshot;
import com.arktech.superaccountant.masters.models.UserOrganization;
import com.arktech.superaccountant.masters.repository.OrganizationRepository;
import com.arktech.superaccountant.masters.repository.UploadJobRepository;
import com.arktech.superaccountant.masters.repository.UploadLedgerSnapshotRepository;
import com.arktech.superaccountant.masters.repository.UserOrganizationRepository;
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
 * Accept outcomes for retro-active TDS flags. A retro flag never rewrites the
 * frozen voucher; approval only records an amendment follow-up for the frozen
 * month. Not @Transactional: compare() runs REQUIRES_NEW and every test uses
 * its own org and members.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = "JWT_SECRET=test-jwt-secret-must-be-at-least-32-characters-long")
class TamperRetroOutcomesIT {

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
    private DayBookUploadJobRepository dayBookUploadJobRepository;
    @Autowired
    private ParsedVoucherRepository parsedVoucherRepository;
    @Autowired
    private WorkflowRepository workflowRepository;
    @Autowired
    private WorkflowEventRepository workflowEventRepository;
    @Autowired
    private UploadJobRepository uploadJobRepository;
    @Autowired
    private UploadLedgerSnapshotRepository uploadLedgerSnapshotRepository;
    @Autowired
    private FrozenVoucherRepository frozenVoucherRepository;
    @Autowired
    private TamperFindingRepository tamperFindingRepository;
    @Autowired
    private AmendmentFollowUpRepository amendmentFollowUpRepository;

    private final ObjectMapper json = new ObjectMapper();

    private UUID orgId;
    private String ownerToken;
    private String operatorToken;
    private String accountantToken;
    private String auditorToken;
    private Workflow tdsApr;
    private Workflow tdsMay;

    private void seedRetroFinding() throws Exception {
        ownerToken = setupOwnerOrg();
        seedTdsLedgers(orgId);
        DayBookUploadJob job1 = completedJob(orgId, LocalDate.of(2025, 4, 1), LocalDate.of(2025, 4, 30));
        seedTdsVoucher(orgId, job1.getId(), "g-apr", "PY/APR/1", LocalDate.of(2025, 4, 10), "h-apr",
                "Contractor ABC", entry("Professional Fees", "20000"));
        tdsApr = workflow(orgId, WorkflowType.TDS_PAYMENT, LocalDate.of(2025, 4, 1),
                LocalDate.of(2025, 4, 30), WorkflowStage.FINALIZED, job1.getId());
        snapshotService.freeze(tdsApr.getId(), orgId, "acc");
        tdsMay = workflow(orgId, WorkflowType.TDS_PAYMENT, LocalDate.of(2025, 5, 1),
                LocalDate.of(2025, 5, 31), WorkflowStage.PREPARE, null);

        operatorToken = memberJwt("operator");
        accountantToken = memberJwt("accountant");
        auditorToken = memberJwt("auditor_ca");

        DayBookUploadJob job2 = completedJob(orgId, LocalDate.of(2025, 4, 1), LocalDate.of(2025, 5, 31));
        seedTdsVoucher(orgId, job2.getId(), "g-apr", "PY/APR/1", LocalDate.of(2025, 4, 10), "h-apr",
                "Contractor ABC", entry("Professional Fees", "20000"));
        seedTdsVoucher(orgId, job2.getId(), "g-may", "PY/MAY/1", LocalDate.of(2025, 5, 10), "h-may",
                "Contractor ABC", entry("Professional Fees", "40000"));
        tamperComparisonService.compare(job2.getId());
    }

    private TamperFinding theFinding() {
        List<TamperFinding> findings = tamperFindingRepository.findByOrganizationId(orgId);
        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).getChangeKind()).isEqualTo(TamperChangeKind.RETRO_FLAGGED);
        return findings.get(0);
    }

    private String actStatus(String token, String action, String note) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/workflows/" + tdsApr.getId() + "/findings/"
                        + theFinding().getId() + "/action")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"action\":\"" + action + "\""
                                + (note == null ? "" : ",\"note\":\"" + note + "\"") + "}"))
                .andReturn();
        return String.valueOf(result.getResponse().getStatus());
    }

    @Test
    void operatorAcknowledge_keepsRetroFlagBlocking() throws Exception {
        seedRetroFinding();

        assertThat(actStatus(operatorToken, "ACKNOWLEDGE", "will amend the quarter")).isEqualTo("200");
        assertThat(theFinding().getResolveStatus()).isEqualTo(ResolveStatus.ACKNOWLEDGED);

        mockMvc.perform(get("/api/v1/workflows/" + tdsApr.getId() + "/findings")
                        .header("Authorization", "Bearer " + operatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blockingCount").value(1))
                .andExpect(jsonPath("$.findings[0].findingType").value("TAMPER_RETRO_FLAGGED"))
                .andExpect(jsonPath("$.findings[0].blocking").value(true));
    }

    @Test
    void accountantApprove_recordsFollowUpAndLeavesFrozenVoucherUntouched() throws Exception {
        seedRetroFinding();
        TamperFinding finding = theFinding();

        assertThat(actStatus(operatorToken, "ACKNOWLEDGE", "will amend the quarter")).isEqualTo("200");
        assertThat(actStatus(accountantToken, "APPROVE", "accept the shortfall")).isEqualTo("200");

        finding = theFinding();
        assertThat(finding.getResolveStatus()).isEqualTo(ResolveStatus.APPROVED);

        List<FrozenVoucher> live = frozenVoucherRepository.findByOrganizationIdAndReleasedAtIsNull(orgId);
        assertThat(live).hasSize(1);
        assertThat(live.get(0).getContentHash()).isEqualTo("h-apr");
        assertThat(live.get(0).getFrozenByWorkflowId()).isEqualTo(tdsApr.getId());
        assertThat(live.get(0).getReleasedAt()).isNull();

        List<AmendmentFollowUp> followUps = amendmentFollowUpRepository
                .findByWorkflowIdOrderByCreatedAt(tdsApr.getId());
        assertThat(followUps).hasSize(1);
        assertThat(followUps.get(0).getStatus()).isEqualTo(AmendmentStatus.OPEN);
        assertThat(followUps.get(0).getTamperFindingId()).isEqualTo(finding.getId());
        assertThat(followUps.get(0).getSummary()).isEqualTo(finding.getMessage());

        List<WorkflowEvent> events = workflowEventRepository
                .findAllByWorkflowIdOrderByOccurredAtAsc(tdsApr.getId()).stream()
                .filter(e -> e.getKind() == WorkflowEventKind.AMENDMENT_RECORDED)
                .toList();
        assertThat(events).hasSize(1);

        mockMvc.perform(get("/api/v1/workflows/" + tdsApr.getId() + "/findings")
                        .header("Authorization", "Bearer " + accountantToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blockingCount").value(0));
    }

    @Test
    void approvedRetroFlag_staysApprovedOnNextUpload() throws Exception {
        seedRetroFinding();
        assertThat(actStatus(accountantToken, "ACKNOWLEDGE", "accept the shortfall")).isEqualTo("200");

        DayBookUploadJob job3 = completedJob(orgId, LocalDate.of(2025, 4, 1), LocalDate.of(2025, 5, 31));
        seedTdsVoucher(orgId, job3.getId(), "g-apr", "PY/APR/1", LocalDate.of(2025, 4, 10), "h-apr",
                "Contractor ABC", entry("Professional Fees", "20000"));
        seedTdsVoucher(orgId, job3.getId(), "g-may", "PY/MAY/1", LocalDate.of(2025, 5, 10), "h-may",
                "Contractor ABC", entry("Professional Fees", "40000"));
        tamperComparisonService.compare(job3.getId());

        assertThat(theFinding().getResolveStatus()).isEqualTo(ResolveStatus.APPROVED);
        mockMvc.perform(get("/api/v1/workflows/" + tdsApr.getId() + "/findings")
                        .header("Authorization", "Bearer " + accountantToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blockingCount").value(0));
    }

    @Test
    void accountantAcknowledge_approvesInOneStep() throws Exception {
        seedRetroFinding();
        assertThat(actStatus(accountantToken, "ACKNOWLEDGE", "accept the shortfall")).isEqualTo("200");
        assertThat(theFinding().getResolveStatus()).isEqualTo(ResolveStatus.APPROVED);
        assertThat(amendmentFollowUpRepository.findByWorkflowIdOrderByCreatedAt(tdsApr.getId())).hasSize(1);
    }

    @Test
    void ownerAndAuditor_actionForbidden_403() throws Exception {
        seedRetroFinding();
        assertThat(actStatus(ownerToken, "ACKNOWLEDGE", "note")).isEqualTo("403");
        assertThat(actStatus(auditorToken, "ACKNOWLEDGE", "note")).isEqualTo("403");
    }

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
                        .content("{\"name\":\"Retro Outcomes Org " + suffix + "\",\"financialYearStart\":4}"))
                .andExpect(status().isCreated())
                .andReturn();
        Map<?, ?> orgBody = json.readValue(createOrgResult.getResponse().getContentAsString(), Map.class);
        orgId = UUID.fromString(orgBody.get("id").toString());
        var org = organizationRepository.findById(orgId).orElseThrow();
        org.setBooksStartDate(LocalDate.of(2025, 4, 1));
        organizationRepository.save(org);
        return selectOrg(token);
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

    private void seedTdsLedgers(UUID orgId) {
        UploadJob mastersJob = new UploadJob();
        mastersJob.setOrganizationId(orgId);
        mastersJob.setFileName("masters.xlsx");
        mastersJob.setUploadedBy("acc");
        mastersJob.setStatus(UploadJobStatus.COMPLETED);
        mastersJob = uploadJobRepository.save(mastersJob);
        ledger(orgId, mastersJob.getId(), "Professional Fees", LedgerCategory.EXPENSE, "194J_B", true, null);
        ledger(orgId, mastersJob.getId(), "Contractor ABC", LedgerCategory.CREDITOR, null, false, "ABCPA1234C");
        ledger(orgId, mastersJob.getId(), "TDS Payable", LedgerCategory.TDS, null, false, null);
    }

    private void ledger(UUID orgId, UUID mastersJobId, String name, LedgerCategory category,
                        String tdsSection, boolean tdsApplicable, String pan) {
        UploadLedgerSnapshot s = new UploadLedgerSnapshot();
        s.setUploadJobId(mastersJobId);
        s.setOrganizationId(orgId);
        s.setLedgerName(name);
        s.setCategory(category);
        s.setTdsSection(tdsSection);
        s.setTdsApplicable(tdsApplicable);
        s.setPan(pan);
        uploadLedgerSnapshotRepository.save(s);
    }

    private DayBookUploadJob completedJob(UUID orgId, LocalDate from, LocalDate to) {
        DayBookUploadJob job = new DayBookUploadJob();
        job.setOrganizationId(orgId);
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

    private ParsedVoucher seedTdsVoucher(UUID orgId, UUID jobId, String guid, String number,
                                         LocalDate date, String contentHash, String party,
                                         ParsedVoucherEntry... entries) {
        ParsedVoucher v = new ParsedVoucher();
        v.setOrganizationId(orgId);
        v.setUploadJobId(jobId);
        v.setVoucherGuid(guid);
        v.setVoucherTypeName("Payment");
        v.setVoucherNumber(number);
        v.setVoucherDate(date);
        v.setContentHash(contentHash);
        v.setPartyLedgerName(party);
        List<ParsedVoucherEntry> list = new ArrayList<>();
        for (ParsedVoucherEntry e : entries) {
            e.setOrganizationId(orgId);
            e.setParsedVoucher(v);
            list.add(e);
        }
        v.setEntries(list);
        return parsedVoucherRepository.save(v);
    }

    private Workflow workflow(UUID orgId, WorkflowType type, LocalDate start, LocalDate end,
                              WorkflowStage stage, UUID uploadJobId) {
        Workflow w = new Workflow();
        w.setOrganizationId(orgId);
        w.setType(type);
        w.setPeriodStart(start);
        w.setPeriodEnd(end);
        w.setPeriodKey(start.toString().substring(0, 7));
        w.setFinancialYear(start.getYear());
        w.setStage(stage);
        w.setActive(true);
        w.setUploadJobId(uploadJobId);
        w.setCreatedBy("acc");
        return workflowRepository.save(w);
    }
}
