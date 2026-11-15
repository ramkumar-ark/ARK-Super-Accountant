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
import com.arktech.superaccountant.masters.models.UploadJobStatus;
import com.arktech.superaccountant.masters.repository.OrganizationRepository;
import com.arktech.superaccountant.masters.repository.UserOrganizationRepository;
import com.arktech.superaccountant.workflow.models.Workflow;
import com.arktech.superaccountant.workflow.models.WorkflowStage;
import com.arktech.superaccountant.workflow.models.WorkflowType;
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
import org.springframework.transaction.annotation.Transactional;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Release on un-finalize over HTTP. Rows are soft-released, never deleted;
 * amendment follow-ups are untouched.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Transactional
@TestPropertySource(properties = "JWT_SECRET=test-jwt-secret-must-be-at-least-32-characters-long")
class SnapshotReleaseIT {

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
    private FrozenVoucherRepository frozenVoucherRepository;
    @Autowired
    private com.arktech.superaccountant.workflow.snapshot.AmendmentFollowUpRepository amendmentFollowUpRepository;
    @Autowired
    private TamperFindingRepository tamperFindingRepository;

    private final ObjectMapper json = new ObjectMapper();

    private UUID orgId;
    private String accountantToken;

    @Test
    void unfinalizeOnlyFinalized_releasesAllRows() throws Exception {
        setup("release-all");
        DayBookUploadJob job = completedJob(orgId, LocalDate.of(2025, 4, 1), LocalDate.of(2025, 5, 31));
        seedVoucher(orgId, job.getId(), "g-1", "Sales", "SB/1", LocalDate.of(2025, 4, 15), "h1",
                "Sales Account", "100");
        seedVoucher(orgId, job.getId(), "g-2", "Sales", "SB/2", LocalDate.of(2025, 4, 20), "h2",
                "Sales Account", "100");
        Workflow apr = workflow(orgId, WorkflowType.GSTR1, LocalDate.of(2025, 4, 1),
                LocalDate.of(2025, 4, 30), job.getId());
        snapshotService.freeze(apr.getId(), orgId, "acc");
        assertThat(frozenVoucherRepository.findByOrganizationIdAndReleasedAtIsNull(orgId)).hasSize(2);

        unfinalize(apr.getId());

        List<FrozenVoucher> all = frozenVoucherRepository.findByOrganizationId(orgId);
        assertThat(all).hasSize(2);
        assertThat(all).allSatisfy(v -> assertThat(v.getReleasedAt()).isNotNull());
        assertThat(frozenVoucherRepository.findByOrganizationIdAndReleasedAtIsNull(orgId)).isEmpty();
    }

    @Test
    void unfinalizeLatestMonth_keepsEarlierMonthRows() throws Exception {
        setup("release-earlier");
        DayBookUploadJob job = completedJob(orgId, LocalDate.of(2025, 4, 1), LocalDate.of(2025, 5, 31));
        seedVoucher(orgId, job.getId(), "g-1", "Sales", "SB/1", LocalDate.of(2025, 4, 15), "h1",
                "Sales Account", "100");
        Workflow apr = workflow(orgId, WorkflowType.GSTR1, LocalDate.of(2025, 4, 1),
                LocalDate.of(2025, 4, 30), job.getId());
        snapshotService.freeze(apr.getId(), orgId, "acc");
        DayBookUploadJob job2 = completedJob(orgId, LocalDate.of(2025, 5, 1), LocalDate.of(2025, 5, 31));
        seedVoucher(orgId, job2.getId(), "g-2", "Sales", "SB/2", LocalDate.of(2025, 5, 10), "h2",
                "Sales Account", "100");
        Workflow may = workflow(orgId, WorkflowType.GSTR1, LocalDate.of(2025, 5, 1),
                LocalDate.of(2025, 5, 31), job2.getId());
        snapshotService.freeze(may.getId(), orgId, "acc");

        unfinalize(may.getId());

        List<FrozenVoucher> live = frozenVoucherRepository.findByOrganizationIdAndReleasedAtIsNull(orgId);
        assertThat(live).hasSize(1);
        assertThat(live.get(0).getVoucherNumber()).isEqualTo("SB/1");
    }

    @Test
    void release_otherTypeStillFinalized_keepsRows() throws Exception {
        setup("release-other");
        DayBookUploadJob job = completedJob(orgId, LocalDate.of(2025, 4, 1), LocalDate.of(2025, 4, 30));
        seedVoucher(orgId, job.getId(), "g-1", "Sales", "SB/1", LocalDate.of(2025, 4, 15), "h1",
                "Sales Account", "100");
        Workflow apr = workflow(orgId, WorkflowType.GSTR1, LocalDate.of(2025, 4, 1),
                LocalDate.of(2025, 4, 30), job.getId());
        snapshotService.freeze(apr.getId(), orgId, "acc");
        workflow(orgId, WorkflowType.TDS_PAYMENT, LocalDate.of(2025, 4, 1), LocalDate.of(2025, 4, 30), null);

        unfinalize(apr.getId());

        assertThat(frozenVoucherRepository.findByOrganizationIdAndReleasedAtIsNull(orgId)).hasSize(1);
    }

    @Test
    void unfinalize_keepsAmendmentFollowUps() throws Exception {
        setup("release-followups");
        DayBookUploadJob job = completedJob(orgId, LocalDate.of(2025, 4, 1), LocalDate.of(2025, 4, 30));
        seedVoucher(orgId, job.getId(), "g-1", "Sales", "SB/1", LocalDate.of(2025, 4, 15), "h1",
                "Sales Account", "100");
        Workflow apr = workflow(orgId, WorkflowType.GSTR1, LocalDate.of(2025, 4, 1),
                LocalDate.of(2025, 4, 30), job.getId());
        snapshotService.freeze(apr.getId(), orgId, "acc");

        TamperFinding finding = new TamperFinding();
        finding.setOrganizationId(orgId);
        finding.setChangeKind(TamperChangeKind.MODIFIED);
        finding.setVoucherKey("N:SB/1");
        finding.setFindingKey("TAMPER_MODIFIED|N:SB/1");
        finding.setEvidenceHash("e");
        finding.setAffectedTypes("GSTR1");
        finding.setAffectedWorkflowIds(apr.getId().toString());
        finding.setMessage("Sales SB/1 was modified");
        finding = tamperFindingRepository.save(finding);
        AmendmentFollowUp followUp = new AmendmentFollowUp();
        followUp.setOrganizationId(orgId);
        followUp.setWorkflowId(apr.getId());
        followUp.setTamperFindingId(finding.getId());
        followUp.setVoucherKey("N:SB/1");
        followUp.setSummary("Sales SB/1 was modified");
        followUp.setStatus(AmendmentStatus.OPEN);
        amendmentFollowUpRepository.save(followUp);

        unfinalize(apr.getId());

        assertThat(amendmentFollowUpRepository.findByWorkflowIdOrderByCreatedAt(apr.getId())).hasSize(1);
        assertThat(amendmentFollowUpRepository.findByWorkflowIdOrderByCreatedAt(apr.getId())
                .get(0).getStatus()).isEqualTo(AmendmentStatus.OPEN);
    }

    @Test
    void refinalizeAfterRelease_createsNewLiveRows() throws Exception {
        setup("release-refinalize");
        DayBookUploadJob job = completedJob(orgId, LocalDate.of(2025, 4, 1), LocalDate.of(2025, 5, 31));
        seedVoucher(orgId, job.getId(), "g-1", "Sales", "SB/1", LocalDate.of(2025, 4, 15), "h1",
                "Sales Account", "100");
        Workflow apr = workflow(orgId, WorkflowType.GSTR1, LocalDate.of(2025, 4, 1),
                LocalDate.of(2025, 4, 30), job.getId());
        snapshotService.freeze(apr.getId(), orgId, "acc");

        unfinalize(apr.getId());
        assertThat(frozenVoucherRepository.findByOrganizationIdAndReleasedAtIsNull(orgId)).isEmpty();

        snapshotService.freeze(apr.getId(), orgId, "acc");
        List<FrozenVoucher> live = frozenVoucherRepository.findByOrganizationIdAndReleasedAtIsNull(orgId);
        assertThat(live).hasSize(1);
        assertThat(live.get(0).getContentHash()).isEqualTo("h1");
    }

    // -- helpers ------------------------------------------------------------

    private void setup(String name) throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String username = "rel" + suffix;
        String orgName = name + " " + suffix;
        MvcResult signupResult = mockMvc.perform(post("/api/auth/signup")
                        .contentType("application/json")
                        .content("{\"username\":\"" + username + "\",\"email\":\"" + username
                                + "@test.com\",\"password\":\"TestPass1!\",\"role\":\"accountant\"}"))
                .andReturn();
        if (signupResult.getResponse().getStatus() != 200) {
            throw new IllegalStateException("signup failed status="
                    + signupResult.getResponse().getStatus()
                    + " body=" + signupResult.getResponse().getContentAsString()
                    + " ex=" + signupResult.getResolvedException());
        }
        String token = signin(username);
        MvcResult createOrgResult = mockMvc.perform(post("/api/organizations")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"name\":\"" + orgName + "\",\"financialYearStart\":4}"))
                .andExpect(status().isCreated())
                .andReturn();
        Map<?, ?> orgBody = json.readValue(createOrgResult.getResponse().getContentAsString(), Map.class);
        orgId = UUID.fromString(orgBody.get("id").toString());
        var org = organizationRepository.findById(orgId).orElseThrow();
        org.setBooksStartDate(LocalDate.of(2025, 4, 1));
        organizationRepository.save(org);
        accountantToken = selectOrg(token);
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

    private void unfinalize(UUID workflowId) throws Exception {
        mockMvc.perform(post("/api/v1/workflows/" + workflowId + "/unfinalize")
                        .header("Authorization", "Bearer " + accountantToken)
                        .contentType("application/json")
                        .content("{\"reason\":\"need to correct something\"}"))
                .andExpect(status().isOk());
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

    private ParsedVoucher seedVoucher(UUID org, UUID jobId, String guid, String type, String number,
                                      LocalDate date, String contentHash, String ledger, String amount) {
        ParsedVoucher v = new ParsedVoucher();
        v.setOrganizationId(org);
        v.setUploadJobId(jobId);
        v.setVoucherGuid(guid);
        v.setVoucherTypeName(type);
        v.setVoucherNumber(number);
        v.setVoucherDate(date);
        v.setContentHash(contentHash);
        v.setPartyLedgerName("Client ABC");
        ParsedVoucherEntry e = new ParsedVoucherEntry();
        e.setOrganizationId(org);
        e.setLedgerName(ledger);
        e.setAmount(new BigDecimal(amount));
        e.setParsedVoucher(v);
        v.setEntries(List.of(e));
        return parsedVoucherRepository.save(v);
    }

    /** Saves a workflow already in FINALIZED stage. */
    private Workflow workflow(UUID org, WorkflowType type, LocalDate start, LocalDate end, UUID uploadJobId) {
        Workflow w = new Workflow();
        w.setOrganizationId(org);
        w.setType(type);
        w.setPeriodStart(start);
        w.setPeriodEnd(end);
        w.setPeriodKey(start.toString().substring(0, 7));
        w.setFinancialYear(start.getYear());
        w.setStage(WorkflowStage.FINALIZED);
        w.setActive(true);
        w.setUploadJobId(uploadJobId);
        w.setFinalizedAt(Instant.now());
        w.setFinalizedBy("acc");
        w.setCreatedBy("acc");
        return workflowRepository.saveAndFlush(w);
    }
}
