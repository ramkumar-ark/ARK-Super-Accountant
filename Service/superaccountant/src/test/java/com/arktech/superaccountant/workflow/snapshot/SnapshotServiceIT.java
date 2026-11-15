package com.arktech.superaccountant.workflow.snapshot;

import com.arktech.superaccountant.daybook.models.ParsedVoucher;
import com.arktech.superaccountant.daybook.models.ParsedVoucherEntry;
import com.arktech.superaccountant.daybook.models.DayBookUploadJob;
import com.arktech.superaccountant.daybook.repository.ParsedVoucherRepository;
import com.arktech.superaccountant.login.models.ERole;
import com.arktech.superaccountant.login.models.Role;
import com.arktech.superaccountant.login.models.User;
import com.arktech.superaccountant.login.repository.RoleRepository;
import com.arktech.superaccountant.login.repository.UserRepository;
import com.arktech.superaccountant.masters.models.Organization;
import com.arktech.superaccountant.masters.models.UserOrganization;
import com.arktech.superaccountant.masters.repository.UserOrganizationRepository;
import com.arktech.superaccountant.masters.models.UploadJobStatus;
import com.arktech.superaccountant.masters.repository.OrganizationRepository;
import com.arktech.superaccountant.workflow.models.Workflow;
import com.arktech.superaccountant.workflow.models.WorkflowEvent;
import com.arktech.superaccountant.workflow.models.WorkflowEventKind;
import com.arktech.superaccountant.workflow.models.WorkflowStage;
import com.arktech.superaccountant.workflow.models.WorkflowType;
import com.arktech.superaccountant.workflow.repository.WorkflowEventRepository;
import com.arktech.superaccountant.workflow.repository.WorkflowRepository;
import com.arktech.superaccountant.workflow.seam.SnapshotService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@Transactional
@TestPropertySource(properties = "JWT_SECRET=test-jwt-secret-must-be-at-least-32-characters-long")
class SnapshotServiceIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

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
    private FrozenThrough frozenThrough;

    private Organization newOrg(String name) {
        Role role = roleRepository.findByName(ERole.ROLE_ACCOUNTANT)
                .orElseGet(() -> roleRepository.save(new Role(ERole.ROLE_ACCOUNTANT)));
        User user = new User(name.toLowerCase() + "-acc", "acc-" + UUID.randomUUID() + "@example.com",
                passwordEncoder.encode("TestPass1!"));
        user.setRole(role);
        user = userRepository.save(user);
        Organization org = new Organization(name);
        org.setFinancialYearStart(4);
        org.setBooksStartDate(LocalDate.of(2025, 4, 1));
        org = organizationRepository.save(org);
        UserOrganization membership = new UserOrganization();
        membership.setUser(user);
        membership.setOrganization(org);
        membership.setRole(ERole.ROLE_ACCOUNTANT);
        userOrganizationRepository.save(membership);
        return org;
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

    private ParsedVoucher seedVoucher(UUID orgId, UUID jobId, String guid, String type, String number,
                                      LocalDate date, String contentHash, String ledger, BigDecimal amount) {
        ParsedVoucher v = new ParsedVoucher();
        v.setOrganizationId(orgId);
        v.setUploadJobId(jobId);
        v.setVoucherGuid(guid);
        v.setVoucherTypeName(type);
        v.setVoucherNumber(number);
        v.setVoucherDate(date);
        v.setContentHash(contentHash);
        v.setPartyLedgerName("Client ABC");
        List<ParsedVoucherEntry> entries = new ArrayList<>();
        if (ledger != null) {
            ParsedVoucherEntry e = new ParsedVoucherEntry();
            e.setOrganizationId(orgId);
            e.setLedgerName(ledger);
            e.setAmount(amount);
            e.setParsedVoucher(v);
            entries.add(e);
        }
        v.setEntries(entries);
        return parsedVoucherRepository.save(v);
    }

    private Workflow workflow(UUID orgId, WorkflowType type, LocalDate start, LocalDate end,
                              WorkflowStage stage, UUID uploadJobId, boolean confirmedBaseline) {
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
        if (confirmedBaseline) {
            w.setBaselineConfirmedAt(java.time.Instant.now());
        }
        return workflowRepository.saveAndFlush(w);
    }

    private Workflow finalizedGstr1(UUID orgId, UUID jobId) {
        return workflow(orgId, WorkflowType.GSTR1, LocalDate.of(2025, 4, 1), LocalDate.of(2025, 4, 30),
                WorkflowStage.FINALIZED, jobId, false);
    }

    @Test
    void freeze_writesLiveRowsThroughPeriodEnd_only() {
        Organization org = newOrg("freeze-org");
        DayBookUploadJob job = completedJob(org.getId(), LocalDate.of(2025, 4, 1), LocalDate.of(2025, 5, 31));
        seedVoucher(org.getId(), job.getId(), "g-1", "Sales", "SB/1", LocalDate.of(2025, 4, 15), "h1",
                "Sales Account", new BigDecimal("100"));
        seedVoucher(org.getId(), job.getId(), "g-2", "Sales", "SB/2", LocalDate.of(2025, 5, 1), "h2",
                "Sales Account", new BigDecimal("200"));
        Workflow w = finalizedGstr1(org.getId(), job.getId());

        snapshotService.freeze(w.getId(), org.getId(), "acc");

        List<FrozenVoucher> rows = frozenVoucherRepository.findByOrganizationIdAndReleasedAtIsNull(org.getId());
        assertThat(rows).hasSize(1);
        FrozenVoucher row = rows.get(0);
        assertThat(row.getVoucherKey()).isEqualTo("G:g-1");
        assertThat(row.getContentHash()).isEqualTo("h1");
        assertThat(row.getFrozenByWorkflowId()).isEqualTo(w.getId());
        assertThat(row.getReleasedAt()).isNull();
        VoucherPayload payload = VoucherPayload.fromJson(row.getPayload());
        assertThat(payload.number()).isEqualTo("SB/1");
        assertThat(payload.entries()).hasSize(1);
        assertThat(payload.entries().get(0).ledger()).isEqualTo("Sales Account");
    }

    @Test
    void freeze_isCompanyWide_allVoucherTypes() {
        Organization org = newOrg("freeze-wide");
        DayBookUploadJob job = completedJob(org.getId(), LocalDate.of(2025, 4, 1), LocalDate.of(2025, 5, 31));
        seedVoucher(org.getId(), job.getId(), "g-1", "Payment", "PY/1", LocalDate.of(2025, 4, 2), "h1",
                "Rent", new BigDecimal("500"));
        seedVoucher(org.getId(), job.getId(), "g-2", "Journal", "JV/1", LocalDate.of(2025, 4, 3), "h2",
                "Adjustment", new BigDecimal("50"));
        Workflow w = finalizedGstr1(org.getId(), job.getId());

        snapshotService.freeze(w.getId(), org.getId(), "acc");

        assertThat(frozenVoucherRepository.findByOrganizationIdAndReleasedAtIsNull(org.getId()))
                .extracting(FrozenVoucher::getVoucherKey)
                .containsExactlyInAnyOrder("G:g-1", "G:g-2");
    }

    @Test
    void freeze_secondFinalize_rewritesChangedRows_N3() {
        Organization org = newOrg("freeze-rewrite");
        DayBookUploadJob job = completedJob(org.getId(), LocalDate.of(2025, 4, 1), LocalDate.of(2025, 5, 31));
        seedVoucher(org.getId(), job.getId(), "g-1", "Sales", "SB/1", LocalDate.of(2025, 4, 15), "old",
                "Sales Account", new BigDecimal("100"));
        Workflow w = finalizedGstr1(org.getId(), job.getId());
        snapshotService.freeze(w.getId(), org.getId(), "acc");
        w.setActive(false);
        workflowRepository.saveAndFlush(w);

        DayBookUploadJob job2 = completedJob(org.getId(), LocalDate.of(2025, 4, 1), LocalDate.of(2025, 5, 31));
        seedVoucher(org.getId(), job2.getId(), "g-1", "Sales", "SB/1", LocalDate.of(2025, 4, 15), "new",
                "Sales Account", new BigDecimal("150"));
        Workflow w2 = workflow(org.getId(), WorkflowType.GSTR1, LocalDate.of(2025, 4, 1), LocalDate.of(2025, 4, 30),
                WorkflowStage.FINALIZED, job2.getId(), false);
        snapshotService.freeze(w2.getId(), org.getId(), "acc");

        List<FrozenVoucher> live = frozenVoucherRepository
                .findByOrganizationIdAndVoucherKeyAndReleasedAtIsNull(org.getId(), "G:g-1").stream().toList();
        assertThat(live).hasSize(1);
        assertThat(live.get(0).getContentHash()).isEqualTo("new");
        assertThat(live.get(0).getFrozenByWorkflowId()).isEqualTo(w2.getId());

        List<WorkflowEvent> events = workflowEventRepository.findAllByWorkflowIdOrderByOccurredAtAsc(w2.getId());
        assertThat(events).anySatisfy(e -> {
            assertThat(e.getKind()).isEqualTo(WorkflowEventKind.SNAPSHOT_ABSORBED);
            assertThat(e.getReason()).isEqualTo("1 rewritten, 0 released");
        });
    }

    @Test
    void freeze_secondFinalize_releasesKeysAbsentFromUpload() {
        Organization org = newOrg("freeze-release-key");
        DayBookUploadJob job = completedJob(org.getId(), LocalDate.of(2025, 4, 1), LocalDate.of(2025, 5, 31));
        seedVoucher(org.getId(), job.getId(), "g-1", "Sales", "SB/1", LocalDate.of(2025, 4, 15), "h1",
                "Sales Account", new BigDecimal("100"));
        Workflow w = finalizedGstr1(org.getId(), job.getId());
        snapshotService.freeze(w.getId(), org.getId(), "acc");
        w.setActive(false);
        workflowRepository.saveAndFlush(w);

        DayBookUploadJob job2 = completedJob(org.getId(), LocalDate.of(2025, 4, 1), LocalDate.of(2025, 5, 31));
        seedVoucher(org.getId(), job2.getId(), "g-2", "Sales", "SB/2", LocalDate.of(2025, 4, 20), "h2",
                "Sales Account", new BigDecimal("200"));
        Workflow w2 = workflow(org.getId(), WorkflowType.GSTR1, LocalDate.of(2025, 4, 1), LocalDate.of(2025, 4, 30),
                WorkflowStage.FINALIZED, job2.getId(), false);
        snapshotService.freeze(w2.getId(), org.getId(), "acc");

        FrozenVoucher released = frozenVoucherRepository
                .findByOrganizationIdAndVoucherKeyAndReleasedAtIsNull(org.getId(), "G:g-1").orElse(null);
        assertThat(released).isNull();
        List<FrozenVoucher> all = frozenVoucherRepository.findByOrganizationIdAndReleasedAtIsNull(org.getId());
        assertThat(all).extracting(FrozenVoucher::getVoucherKey).containsExactly("G:g-2");

        List<WorkflowEvent> events = workflowEventRepository.findAllByWorkflowIdOrderByOccurredAtAsc(w2.getId());
        assertThat(events).anySatisfy(e -> {
            assertThat(e.getKind()).isEqualTo(WorkflowEventKind.SNAPSHOT_ABSORBED);
            assertThat(e.getReason()).isEqualTo("0 rewritten, 1 released");
        });
    }

    @Test
    void freeze_noAbsorbedChanges_noSnapshotAbsorbedEvent() {
        Organization org = newOrg("freeze-nochange");
        DayBookUploadJob job = completedJob(org.getId(), LocalDate.of(2025, 4, 1), LocalDate.of(2025, 5, 31));
        seedVoucher(org.getId(), job.getId(), "g-1", "Sales", "SB/1", LocalDate.of(2025, 4, 15), "h1",
                "Sales Account", new BigDecimal("100"));
        Workflow w = finalizedGstr1(org.getId(), job.getId());
        snapshotService.freeze(w.getId(), org.getId(), "acc");
        w.setActive(false);
        workflowRepository.saveAndFlush(w);

        Workflow w2 = workflow(org.getId(), WorkflowType.GSTR1, LocalDate.of(2025, 4, 1), LocalDate.of(2025, 4, 30),
                WorkflowStage.FINALIZED, job.getId(), false);
        snapshotService.freeze(w2.getId(), org.getId(), "acc");

        List<WorkflowEvent> events = workflowEventRepository.findAllByWorkflowIdOrderByOccurredAtAsc(w2.getId());
        assertThat(events).noneMatch(e -> e.getKind() == WorkflowEventKind.SNAPSHOT_ABSORBED);
    }

    @Test
    void freeze_duplicateKeysInUpload_bothFrozen() {
        Organization org = newOrg("freeze-dup");
        DayBookUploadJob job = completedJob(org.getId(), LocalDate.of(2025, 4, 1), LocalDate.of(2025, 5, 31));
        seedVoucher(org.getId(), job.getId(), null, "Sales", "SB/1", LocalDate.of(2025, 4, 15), "bbb",
                "Sales Account", new BigDecimal("100"));
        seedVoucher(org.getId(), job.getId(), null, "Sales", "SB/1", LocalDate.of(2025, 4, 15), "aaa",
                "Sales Account", new BigDecimal("120"));
        Workflow w = finalizedGstr1(org.getId(), job.getId());

        snapshotService.freeze(w.getId(), org.getId(), "acc");

        List<FrozenVoucher> live = frozenVoucherRepository.findByOrganizationIdAndReleasedAtIsNull(org.getId());
        assertThat(live).extracting(FrozenVoucher::getVoucherKey)
                .containsExactlyInAnyOrder("N:Sales|SB/1|2025-04-15#1", "N:Sales|SB/1|2025-04-15#2");
        assertThat(live).extracting(FrozenVoucher::getContentHash).containsExactlyInAnyOrder("aaa", "bbb");
    }

    @Test
    void freeze_workflowWithoutUpload_noRows() {
        Organization org = newOrg("freeze-noupload");
        Workflow w = finalizedGstr1(org.getId(), null);

        snapshotService.freeze(w.getId(), org.getId(), "acc");

        assertThat(frozenVoucherRepository.findByOrganizationIdAndReleasedAtIsNull(org.getId())).isEmpty();
    }

    @Test
    void frozenThrough_maxOverFinalizedFiledAndConfirmedBaseline() {
        Organization org = newOrg("frozen-through");
        DayBookUploadJob job = completedJob(org.getId(), LocalDate.of(2025, 4, 1), LocalDate.of(2025, 6, 30));
        Workflow apr = workflow(org.getId(), WorkflowType.GSTR1, LocalDate.of(2025, 4, 1),
                LocalDate.of(2025, 4, 30), WorkflowStage.FINALIZED, job.getId(), false);
        Workflow may = workflow(org.getId(), WorkflowType.GSTR3B, LocalDate.of(2025, 5, 1),
                LocalDate.of(2025, 5, 31), WorkflowStage.FILED, job.getId(), false);
        Workflow jun = workflow(org.getId(), WorkflowType.TDS_PAYMENT, LocalDate.of(2025, 6, 1),
                LocalDate.of(2025, 6, 30), WorkflowStage.PREPARE, job.getId(), false);

        assertThat(frozenThrough.of(org.getId())).isPresent();
        assertThat(frozenThrough.of(org.getId()).orElseThrow()).isEqualTo(LocalDate.of(2025, 5, 31));

        workflowRepository.delete(apr);
        workflowRepository.delete(may);
        workflowRepository.delete(jun);
        Workflow baseline = workflow(org.getId(), WorkflowType.TDS_PAYMENT, LocalDate.of(2025, 3, 1),
                LocalDate.of(2025, 3, 31), WorkflowStage.BASELINE, job.getId(), true);
        assertThat(frozenThrough.of(org.getId())).isPresent();
        assertThat(frozenThrough.of(org.getId()).orElseThrow()).isEqualTo(LocalDate.of(2025, 3, 31));
        workflowRepository.delete(baseline);
        assertThat(frozenThrough.of(org.getId())).isEmpty();
    }
}
