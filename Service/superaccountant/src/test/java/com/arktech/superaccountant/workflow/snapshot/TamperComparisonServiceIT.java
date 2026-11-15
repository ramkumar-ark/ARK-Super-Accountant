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
import com.arktech.superaccountant.masters.models.LedgerCategory;
import com.arktech.superaccountant.masters.models.Organization;
import com.arktech.superaccountant.masters.models.UploadJob;
import com.arktech.superaccountant.masters.models.UploadJobStatus;
import com.arktech.superaccountant.masters.models.UploadLedgerSnapshot;
import com.arktech.superaccountant.masters.models.UserOrganization;
import com.arktech.superaccountant.masters.repository.OrganizationRepository;
import com.arktech.superaccountant.masters.repository.UploadJobRepository;
import com.arktech.superaccountant.masters.repository.UploadLedgerSnapshotRepository;
import com.arktech.superaccountant.masters.repository.UserOrganizationRepository;
import com.arktech.superaccountant.workflow.models.Workflow;
import com.arktech.superaccountant.workflow.models.WorkflowStage;
import com.arktech.superaccountant.workflow.models.WorkflowType;
import com.arktech.superaccountant.workflow.repository.WorkflowRepository;
import com.arktech.superaccountant.workflow.seam.SnapshotService;
import com.arktech.superaccountant.masters.models.ResolveStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Not @Transactional: compare() runs REQUIRES_NEW and would not see an
 * uncommitted test transaction. Every test uses its own org.
 */
@SpringBootTest
@Testcontainers
@TestPropertySource(properties = "JWT_SECRET=test-jwt-secret-must-be-at-least-32-characters-long")
class TamperComparisonServiceIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

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
    private UploadJobRepository uploadJobRepository;
    @Autowired
    private UploadLedgerSnapshotRepository uploadLedgerSnapshotRepository;
    @Autowired
    private TamperChangeRepository tamperChangeRepository;
    @Autowired
    private TamperFindingRepository tamperFindingRepository;

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

    private void seedLedgerMap(UUID orgId) {
        UploadJob mastersJob = new UploadJob();
        mastersJob.setOrganizationId(orgId);
        mastersJob.setFileName("masters.xlsx");
        mastersJob.setUploadedBy("acc");
        mastersJob.setStatus(UploadJobStatus.COMPLETED);
        mastersJob = uploadJobRepository.save(mastersJob);
        ledger(orgId, mastersJob.getId(), "Sales Account", LedgerCategory.INCOME, null);
        ledger(orgId, mastersJob.getId(), "Output CGST @9%", LedgerCategory.GST, null);
        ledger(orgId, mastersJob.getId(), "Client ABC", LedgerCategory.OTHER, null);
        ledger(orgId, mastersJob.getId(), "Cement Purchases", LedgerCategory.PURCHASE, null);
        ledger(orgId, mastersJob.getId(), "TDS Payable", LedgerCategory.TDS, null);
        ledger(orgId, mastersJob.getId(), "Adjustment", LedgerCategory.OTHER, null);
        ledger(orgId, mastersJob.getId(), "Contractor Fees", LedgerCategory.EXPENSE, "194C");
    }

    private void ledger(UUID orgId, UUID mastersJobId, String name, LedgerCategory category, String tdsSection) {
        UploadLedgerSnapshot s = new UploadLedgerSnapshot();
        s.setUploadJobId(mastersJobId);
        s.setOrganizationId(orgId);
        s.setLedgerName(name);
        s.setCategory(category);
        s.setTdsSection(tdsSection);
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

    private ParsedVoucher seedVoucher(UUID orgId, UUID jobId, String guid, String type, String number,
                                      LocalDate date, String contentHash, ParsedVoucherEntry... entries) {
        ParsedVoucher v = new ParsedVoucher();
        v.setOrganizationId(orgId);
        v.setUploadJobId(jobId);
        v.setVoucherGuid(guid);
        v.setVoucherTypeName(type);
        v.setVoucherNumber(number);
        v.setVoucherDate(date);
        v.setContentHash(contentHash);
        v.setPartyLedgerName("Client ABC");
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
            w.setBaselineConfirmedAt(Instant.now());
        }
        return workflowRepository.save(w);
    }

    /** Seeds a FINALIZED GSTR1 April workflow over job1 and freezes its vouchers. */
    private Workflow frozenGstr1(UUID orgId, UUID job1Id) {
        Workflow w = workflow(orgId, WorkflowType.GSTR1, LocalDate.of(2025, 4, 1), LocalDate.of(2025, 4, 30),
                WorkflowStage.FINALIZED, job1Id, false);
        snapshotService.freeze(w.getId(), orgId, "acc");
        return w;
    }

    @Test
    void compare_nothingFrozen_noChanges() {
        Organization org = newOrg("cmp-none");
        seedLedgerMap(org.getId());
        DayBookUploadJob job2 = completedJob(org.getId(), LocalDate.of(2025, 4, 1), LocalDate.of(2025, 5, 31));
        seedVoucher(org.getId(), job2.getId(), "g-1", "Sales", "SB/001", LocalDate.of(2025, 4, 15), "h2",
                entry("Sales Account", "100"));

        tamperComparisonService.compare(job2.getId());

        assertThat(tamperChangeRepository.findByOrganizationId(org.getId())).isEmpty();
        assertThat(tamperFindingRepository.findByOrganizationId(org.getId())).isEmpty();
    }

    @Test
    void compare_modifiedSale_inFinalizedScope_blockingFinding() {
        Organization org = newOrg("cmp-modified");
        seedLedgerMap(org.getId());
        DayBookUploadJob job1 = completedJob(org.getId(), LocalDate.of(2025, 4, 1), LocalDate.of(2025, 5, 31));
        seedVoucher(org.getId(), job1.getId(), "g-1", "Sales", "SB/001", LocalDate.of(2025, 4, 15), "h1",
                entry("Sales Account", "100"));
        Workflow gstr1Apr = frozenGstr1(org.getId(), job1.getId());

        DayBookUploadJob job2 = completedJob(org.getId(), LocalDate.of(2025, 4, 1), LocalDate.of(2025, 5, 31));
        seedVoucher(org.getId(), job2.getId(), "g-1", "Sales", "SB/001", LocalDate.of(2025, 4, 15), "h2",
                entry("Sales Account", "200"));

        tamperComparisonService.compare(job2.getId());

        List<TamperChange> changes = tamperChangeRepository.findByOrganizationId(org.getId());
        assertThat(changes).hasSize(1);
        TamperChange change = changes.get(0);
        assertThat(change.getChangeKind()).isEqualTo(TamperChangeKind.MODIFIED);
        assertThat(change.getTouchedScopes()).isEqualTo("GSTR1,GSTR3B");
        assertThat(change.isBlocking()).isTrue();
        assertThat(change.getOldHash()).isEqualTo("h1");
        assertThat(change.getNewHash()).isEqualTo("h2");

        List<TamperFinding> findings = tamperFindingRepository.findByOrganizationId(org.getId());
        assertThat(findings).hasSize(1);
        TamperFinding f = findings.get(0);
        assertThat(f.getChangeKind()).isEqualTo(TamperChangeKind.MODIFIED);
        assertThat(f.getFindingKey()).isEqualTo("MODIFIED|G:g-1");
        assertThat(f.getAffectedTypes()).isEqualTo("GSTR1");
        assertThat(f.getAffectedWorkflowIds()).isEqualTo(gstr1Apr.getId().toString());
        assertThat(f.getChangeDate()).isEqualTo(LocalDate.of(2025, 4, 15));
        assertThat(f.getResolveStatus()).isEqualTo(ResolveStatus.OPEN);
        assertThat(f.getDetectedInUploadJobId()).isEqualTo(job2.getId());
        assertThat(f.getMessage())
                .isEqualTo("Sales SB/001 dated 2025-04-15 was modified after GSTR1 2025-04 was finalized");
    }

    @Test
    void compare_deletedFrozenVoucher_blocking() {
        Organization org = newOrg("cmp-deleted");
        seedLedgerMap(org.getId());
        DayBookUploadJob job1 = completedJob(org.getId(), LocalDate.of(2025, 4, 1), LocalDate.of(2025, 5, 31));
        seedVoucher(org.getId(), job1.getId(), "g-1", "Sales", "SB/001", LocalDate.of(2025, 4, 15), "h1",
                entry("Sales Account", "100"));
        Workflow gstr1Apr = frozenGstr1(org.getId(), job1.getId());

        DayBookUploadJob job2 = completedJob(org.getId(), LocalDate.of(2025, 4, 1), LocalDate.of(2025, 5, 31));
        seedVoucher(org.getId(), job2.getId(), "g-9", "Sales", "SB/009", LocalDate.of(2025, 5, 3), "h9",
                entry("Sales Account", "100"));

        tamperComparisonService.compare(job2.getId());

        List<TamperChange> changes = tamperChangeRepository.findByOrganizationId(org.getId());
        assertThat(changes).hasSize(1);
        TamperChange change = changes.get(0);
        assertThat(change.getChangeKind()).isEqualTo(TamperChangeKind.DELETED);
        assertThat(change.getNewHash()).isNull();
        assertThat(change.getOldDate()).isEqualTo(LocalDate.of(2025, 4, 15));
        assertThat(change.isBlocking()).isTrue();

        List<TamperFinding> findings = tamperFindingRepository.findByOrganizationId(org.getId());
        assertThat(findings).hasSize(1);
        TamperFinding f = findings.get(0);
        assertThat(f.getChangeKind()).isEqualTo(TamperChangeKind.DELETED);
        assertThat(f.getAffectedTypes()).isEqualTo("GSTR1");
        assertThat(f.getAffectedWorkflowIds()).isEqualTo(gstr1Apr.getId().toString());
        assertThat(f.getOldDate()).isEqualTo(LocalDate.of(2025, 4, 15));
        assertThat(f.getNewDate()).isNull();
        assertThat(f.getChangeDate()).isEqualTo(LocalDate.of(2025, 4, 15));
    }

    @Test
    void compare_insertedVoucherOnOrBeforeFrozenThrough() {
        Organization org = newOrg("cmp-inserted");
        seedLedgerMap(org.getId());
        DayBookUploadJob job1 = completedJob(org.getId(), LocalDate.of(2025, 4, 1), LocalDate.of(2025, 5, 31));
        seedVoucher(org.getId(), job1.getId(), "g-1", "Sales", "SB/001", LocalDate.of(2025, 4, 15), "h1",
                entry("Sales Account", "100"));
        frozenGstr1(org.getId(), job1.getId());

        DayBookUploadJob job2 = completedJob(org.getId(), LocalDate.of(2025, 4, 1), LocalDate.of(2025, 5, 31));
        seedVoucher(org.getId(), job2.getId(), "g-1", "Sales", "SB/001", LocalDate.of(2025, 4, 15), "h1",
                entry("Sales Account", "100"));
        seedVoucher(org.getId(), job2.getId(), "g-9", "Sales", "SB/009", LocalDate.of(2025, 4, 28), "h9",
                entry("Sales Account", "100"));

        tamperComparisonService.compare(job2.getId());

        List<TamperChange> changes = tamperChangeRepository.findByOrganizationId(org.getId());
        assertThat(changes).hasSize(1);
        TamperChange change = changes.get(0);
        assertThat(change.getChangeKind()).isEqualTo(TamperChangeKind.INSERTED);
        assertThat(change.getVoucherNumber()).isEqualTo("SB/009");
        assertThat(change.getNewDate()).isEqualTo(LocalDate.of(2025, 4, 28));
        assertThat(change.isBlocking()).isTrue();
        assertThat(tamperFindingRepository.findByOrganizationId(org.getId())).hasSize(1);
    }

    @Test
    void compare_insertedAfterFrozenThrough_ignored() {
        Organization org = newOrg("cmp-inserted-late");
        seedLedgerMap(org.getId());
        DayBookUploadJob job1 = completedJob(org.getId(), LocalDate.of(2025, 4, 1), LocalDate.of(2025, 5, 31));
        seedVoucher(org.getId(), job1.getId(), "g-1", "Sales", "SB/001", LocalDate.of(2025, 4, 15), "h1",
                entry("Sales Account", "100"));
        frozenGstr1(org.getId(), job1.getId());

        DayBookUploadJob job2 = completedJob(org.getId(), LocalDate.of(2025, 4, 1), LocalDate.of(2025, 5, 31));
        seedVoucher(org.getId(), job2.getId(), "g-1", "Sales", "SB/001", LocalDate.of(2025, 4, 15), "h1",
                entry("Sales Account", "100"));
        seedVoucher(org.getId(), job2.getId(), "g-9", "Sales", "SB/009", LocalDate.of(2025, 5, 3), "h9",
                entry("Sales Account", "100"));

        tamperComparisonService.compare(job2.getId());

        assertThat(tamperChangeRepository.findByOrganizationId(org.getId())).isEmpty();
        assertThat(tamperFindingRepository.findByOrganizationId(org.getId())).isEmpty();
    }

    @Test
    void compare_modifiedPaymentOutsideFinalizedScope_informational() {
        Organization org = newOrg("cmp-payment");
        seedLedgerMap(org.getId());
        DayBookUploadJob job1 = completedJob(org.getId(), LocalDate.of(2025, 4, 1), LocalDate.of(2025, 5, 31));
        seedVoucher(org.getId(), job1.getId(), "g-1", "Payment", "PY/1", LocalDate.of(2025, 4, 10), "h1",
                entry("TDS Payable", "500"));
        frozenGstr1(org.getId(), job1.getId());

        DayBookUploadJob job2 = completedJob(org.getId(), LocalDate.of(2025, 4, 1), LocalDate.of(2025, 5, 31));
        seedVoucher(org.getId(), job2.getId(), "g-1", "Payment", "PY/1", LocalDate.of(2025, 4, 10), "h2",
                entry("TDS Payable", "900"));

        tamperComparisonService.compare(job2.getId());

        List<TamperChange> changes = tamperChangeRepository.findByOrganizationId(org.getId());
        assertThat(changes).hasSize(1);
        TamperChange change = changes.get(0);
        assertThat(change.getChangeKind()).isEqualTo(TamperChangeKind.MODIFIED);
        assertThat(change.getTouchedScopes()).isEqualTo("TDS_26Q,TDS_PAYMENT");
        assertThat(change.isBlocking()).isFalse();
        assertThat(tamperFindingRepository.findByOrganizationId(org.getId())).isEmpty();
    }

    @Test
    void compare_scopeOnOldAndNewVersion() {
        Organization org = newOrg("cmp-scope-move");
        seedLedgerMap(org.getId());
        DayBookUploadJob job1 = completedJob(org.getId(), LocalDate.of(2025, 4, 1), LocalDate.of(2025, 5, 31));
        seedVoucher(org.getId(), job1.getId(), "g-1", "Journal", "JV/1", LocalDate.of(2025, 4, 15), "h1",
                entry("Adjustment", "100"));
        frozenGstr1(org.getId(), job1.getId());

        DayBookUploadJob job2 = completedJob(org.getId(), LocalDate.of(2025, 4, 1), LocalDate.of(2025, 5, 31));
        seedVoucher(org.getId(), job2.getId(), "g-1", "Journal", "JV/1", LocalDate.of(2025, 4, 15), "h2",
                entry("Adjustment", "100"), entry("Output CGST @9%", "9"));

        tamperComparisonService.compare(job2.getId());

        List<TamperChange> changes = tamperChangeRepository.findByOrganizationId(org.getId());
        assertThat(changes).hasSize(1);
        assertThat(changes.get(0).getTouchedScopes()).isEqualTo("GSTR1,GSTR3B");
        assertThat(changes.get(0).isBlocking()).isTrue();
        assertThat(tamperFindingRepository.findByOrganizationId(org.getId())).hasSize(1);
    }

    @Test
    void compare_dateMovedOutOfFinalizedPeriod_usesOldDate() {
        Organization org = newOrg("cmp-date-move");
        seedLedgerMap(org.getId());
        DayBookUploadJob job1 = completedJob(org.getId(), LocalDate.of(2025, 4, 1), LocalDate.of(2025, 5, 31));
        seedVoucher(org.getId(), job1.getId(), "g-1", "Sales", "SB/001", LocalDate.of(2025, 4, 15), "h1",
                entry("Sales Account", "100"));
        Workflow gstr1Apr = frozenGstr1(org.getId(), job1.getId());

        DayBookUploadJob job2 = completedJob(org.getId(), LocalDate.of(2025, 4, 1), LocalDate.of(2025, 5, 31));
        seedVoucher(org.getId(), job2.getId(), "g-1", "Sales", "SB/001", LocalDate.of(2025, 5, 2), "h2",
                entry("Sales Account", "100"));

        tamperComparisonService.compare(job2.getId());

        List<TamperChange> changes = tamperChangeRepository.findByOrganizationId(org.getId());
        assertThat(changes).hasSize(1);
        TamperChange change = changes.get(0);
        assertThat(change.getChangeKind()).isEqualTo(TamperChangeKind.MODIFIED);
        assertThat(change.getOldDate()).isEqualTo(LocalDate.of(2025, 4, 15));
        assertThat(change.getNewDate()).isEqualTo(LocalDate.of(2025, 5, 2));
        assertThat(change.isBlocking()).isTrue();

        List<TamperFinding> findings = tamperFindingRepository.findByOrganizationId(org.getId());
        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).getAffectedWorkflowIds()).isEqualTo(gstr1Apr.getId().toString());
        assertThat(findings.get(0).getChangeDate()).isEqualTo(LocalDate.of(2025, 4, 15));
    }

    @Test
    void compare_confirmedBaselineCountsAsFinalized_N9() {
        Organization org = newOrg("cmp-baseline");
        seedLedgerMap(org.getId());
        DayBookUploadJob job1 = completedJob(org.getId(), LocalDate.of(2025, 4, 1), LocalDate.of(2025, 5, 31));
        seedVoucher(org.getId(), job1.getId(), "g-1", "Payment", "PY/1", LocalDate.of(2025, 4, 10), "h1",
                entry("TDS Payable", "500"));
        Workflow tdsApr = workflow(org.getId(), WorkflowType.TDS_PAYMENT, LocalDate.of(2025, 4, 1),
                LocalDate.of(2025, 4, 30), WorkflowStage.BASELINE, job1.getId(), true);
        snapshotService.freeze(tdsApr.getId(), org.getId(), "acc");

        DayBookUploadJob job2 = completedJob(org.getId(), LocalDate.of(2025, 4, 1), LocalDate.of(2025, 5, 31));
        seedVoucher(org.getId(), job2.getId(), "g-1", "Payment", "PY/1", LocalDate.of(2025, 4, 10), "h2",
                entry("TDS Payable", "900"));

        tamperComparisonService.compare(job2.getId());

        List<TamperChange> changes = tamperChangeRepository.findByOrganizationId(org.getId());
        assertThat(changes).hasSize(1);
        assertThat(changes.get(0).isBlocking()).isTrue();

        List<TamperFinding> findings = tamperFindingRepository.findByOrganizationId(org.getId());
        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).getAffectedTypes()).isEqualTo("TDS_PAYMENT");
        assertThat(findings.get(0).getAffectedWorkflowIds()).isEqualTo(tdsApr.getId().toString());
    }

    @Test
    void compare_uploadNotCoveringFrozenDate_noDeletion() {
        Organization org = newOrg("cmp-may-only");
        seedLedgerMap(org.getId());
        DayBookUploadJob job1 = completedJob(org.getId(), LocalDate.of(2025, 4, 1), LocalDate.of(2025, 5, 31));
        seedVoucher(org.getId(), job1.getId(), "g-1", "Sales", "SB/001", LocalDate.of(2025, 4, 15), "h1",
                entry("Sales Account", "100"));
        frozenGstr1(org.getId(), job1.getId());

        DayBookUploadJob job2 = completedJob(org.getId(), LocalDate.of(2025, 5, 1), LocalDate.of(2025, 5, 31));
        seedVoucher(org.getId(), job2.getId(), "g-2", "Sales", "SB/002", LocalDate.of(2025, 5, 10), "h2",
                entry("Sales Account", "100"));

        tamperComparisonService.compare(job2.getId());

        assertThat(tamperChangeRepository.findByOrganizationId(org.getId())).isEmpty();
        assertThat(tamperFindingRepository.findByOrganizationId(org.getId())).isEmpty();
    }

    @Test
    void compare_sameUploadTwice_idempotent() {
        Organization org = newOrg("cmp-idempotent");
        seedLedgerMap(org.getId());
        DayBookUploadJob job1 = completedJob(org.getId(), LocalDate.of(2025, 4, 1), LocalDate.of(2025, 5, 31));
        seedVoucher(org.getId(), job1.getId(), "g-1", "Sales", "SB/001", LocalDate.of(2025, 4, 15), "h1",
                entry("Sales Account", "100"));
        frozenGstr1(org.getId(), job1.getId());

        DayBookUploadJob job2 = completedJob(org.getId(), LocalDate.of(2025, 4, 1), LocalDate.of(2025, 5, 31));
        seedVoucher(org.getId(), job2.getId(), "g-1", "Sales", "SB/001", LocalDate.of(2025, 4, 15), "h2",
                entry("Sales Account", "200"));

        tamperComparisonService.compare(job2.getId());
        assertThat(tamperChangeRepository.findByUploadJobId(job2.getId())).hasSize(1);
        assertThat(tamperFindingRepository.findByOrganizationId(org.getId())).hasSize(1);

        tamperComparisonService.compare(job2.getId());
        assertThat(tamperChangeRepository.findByUploadJobId(job2.getId())).hasSize(1);
        assertThat(tamperFindingRepository.findByOrganizationId(org.getId())).hasSize(1);
    }

    @Test
    void compare_sameChangeOnNextUpload_noDuplicateFinding() {
        Organization org = newOrg("cmp-next-upload");
        seedLedgerMap(org.getId());
        DayBookUploadJob job1 = completedJob(org.getId(), LocalDate.of(2025, 4, 1), LocalDate.of(2025, 5, 31));
        seedVoucher(org.getId(), job1.getId(), "g-1", "Sales", "SB/001", LocalDate.of(2025, 4, 15), "h1",
                entry("Sales Account", "100"));
        frozenGstr1(org.getId(), job1.getId());

        DayBookUploadJob job2 = completedJob(org.getId(), LocalDate.of(2025, 4, 1), LocalDate.of(2025, 5, 31));
        seedVoucher(org.getId(), job2.getId(), "g-1", "Sales", "SB/001", LocalDate.of(2025, 4, 15), "h2",
                entry("Sales Account", "200"));
        tamperComparisonService.compare(job2.getId());
        UUID firstFindingId = tamperFindingRepository.findByOrganizationId(org.getId()).get(0).getId();

        DayBookUploadJob job3 = completedJob(org.getId(), LocalDate.of(2025, 4, 1), LocalDate.of(2025, 5, 31));
        seedVoucher(org.getId(), job3.getId(), "g-1", "Sales", "SB/001", LocalDate.of(2025, 4, 15), "h2",
                entry("Sales Account", "200"));
        tamperComparisonService.compare(job3.getId());

        List<TamperFinding> findings = tamperFindingRepository.findByOrganizationId(org.getId()).stream()
                .filter(f -> f.getResolveStatus() != ResolveStatus.APPROVED
                        && f.getResolveStatus() != ResolveStatus.RESOLVED
                        && f.getResolveStatus() != ResolveStatus.DISCARDED)
                .toList();
        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).getId()).isEqualTo(firstFindingId);
        assertThat(findings.get(0).getDetectedInUploadJobId()).isEqualTo(job2.getId());
    }
}
