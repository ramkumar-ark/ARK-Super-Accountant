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

@SpringBootTest
@Testcontainers
@TestPropertySource(properties = "JWT_SECRET=test-jwt-secret-must-be-at-least-32-characters-long")
class TamperRetroFlagIT {

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
    private DayBookUploadJobRepository dayBookUploadJobRepository;
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

    private Workflow frozenTdsMonth(UUID orgId, UUID jobId, LocalDate start, LocalDate end) {
        Workflow w = workflow(orgId, WorkflowType.TDS_PAYMENT, start, end, WorkflowStage.FINALIZED, jobId);
        snapshotService.freeze(w.getId(), orgId, "acc");
        return w;
    }

    @Test
    void crossingAfterFinalizedMonth_flagsEarlierVoucherAndOnlyItsWorkflow() {
        Organization org = newOrg("retro-flag");
        seedTdsLedgers(org.getId());
        DayBookUploadJob job1 = completedJob(org.getId(), LocalDate.of(2025, 4, 1), LocalDate.of(2025, 4, 30));
        ParsedVoucher apr = seedTdsVoucher(org.getId(), job1.getId(), "g-apr", "PY/APR/1",
                LocalDate.of(2025, 4, 10), "h-apr", "Contractor ABC", entry("Professional Fees", "20000"));
        Workflow month1 = frozenTdsMonth(org.getId(), job1.getId(),
                LocalDate.of(2025, 4, 1), LocalDate.of(2025, 4, 30));
        Workflow month2 = workflow(org.getId(), WorkflowType.TDS_PAYMENT,
                LocalDate.of(2025, 5, 1), LocalDate.of(2025, 5, 31), WorkflowStage.PREPARE, null);

        DayBookUploadJob job2 = completedJob(org.getId(), LocalDate.of(2025, 4, 1), LocalDate.of(2025, 5, 31));
        seedTdsVoucher(org.getId(), job2.getId(), "g-apr", "PY/APR/1",
                LocalDate.of(2025, 4, 10), "h-apr", "Contractor ABC", entry("Professional Fees", "20000"));
        seedTdsVoucher(org.getId(), job2.getId(), "g-may", "PY/MAY/1",
                LocalDate.of(2025, 5, 10), "h-may", "Contractor ABC", entry("Professional Fees", "40000"));

        tamperComparisonService.compare(job2.getId());

        List<TamperFinding> findings = tamperFindingRepository.findByOrganizationId(org.getId());
        assertThat(findings).hasSize(1);
        TamperFinding finding = findings.get(0);
        assertThat(finding.getChangeKind()).isEqualTo(TamperChangeKind.RETRO_FLAGGED);
        assertThat(finding.getAffectedWorkflowIds()).isEqualTo(month1.getId().toString());
        assertThat(finding.getAffectedWorkflowIds()).doesNotContain(month2.getId().toString());
        assertThat(finding.getChangeDate()).isEqualTo(LocalDate.of(2025, 4, 10));
        assertThat(finding.getVoucherKey()).isEqualTo(VoucherKeys.of(apr));
        assertThat(finding.getMessage()).contains("194J_B").contains("Contractor ABC").contains("2000");
        assertThat(finding.getResolveStatus()).isEqualTo(com.arktech.superaccountant.masters.models.ResolveStatus.OPEN);

        List<TamperChange> changes = tamperChangeRepository.findByOrganizationId(org.getId());
        assertThat(changes).hasSize(1);
        TamperChange change = changes.get(0);
        assertThat(change.getChangeKind()).isEqualTo(TamperChangeKind.RETRO_FLAGGED);
        assertThat(change.getVoucherKey()).isEqualTo(VoucherKeys.of(apr));
        assertThat(change.getTouchedScopes()).isEqualTo("TDS_26Q,TDS_PAYMENT");
        assertThat(change.isBlocking()).isTrue();
        assertThat(change.getBlockingWorkflowIds()).isEqualTo(month1.getId().toString());
    }

    @Test
    void crossingWhileEarlierMonthStillOpen_leavesNoTamperRow() {
        Organization org = newOrg("retro-open");
        seedTdsLedgers(org.getId());
        DayBookUploadJob job1 = completedJob(org.getId(), LocalDate.of(2025, 4, 1), LocalDate.of(2025, 4, 30));
        seedTdsVoucher(org.getId(), job1.getId(), "g-apr", "PY/APR/1",
                LocalDate.of(2025, 4, 10), "h-apr", "Contractor ABC", entry("Professional Fees", "20000"));
        workflow(org.getId(), WorkflowType.TDS_PAYMENT,
                LocalDate.of(2025, 4, 1), LocalDate.of(2025, 4, 30), WorkflowStage.PREPARE, job1.getId());
        workflow(org.getId(), WorkflowType.TDS_PAYMENT,
                LocalDate.of(2025, 5, 1), LocalDate.of(2025, 5, 31), WorkflowStage.PREPARE, null);

        DayBookUploadJob job2 = completedJob(org.getId(), LocalDate.of(2025, 4, 1), LocalDate.of(2025, 5, 31));
        seedTdsVoucher(org.getId(), job2.getId(), "g-apr", "PY/APR/1",
                LocalDate.of(2025, 4, 10), "h-apr", "Contractor ABC", entry("Professional Fees", "20000"));
        seedTdsVoucher(org.getId(), job2.getId(), "g-may", "PY/MAY/1",
                LocalDate.of(2025, 5, 10), "h-may", "Contractor ABC", entry("Professional Fees", "40000"));

        tamperComparisonService.compare(job2.getId());

        assertThat(tamperChangeRepository.findByOrganizationId(org.getId())).isEmpty();
        assertThat(tamperFindingRepository.findByOrganizationId(org.getId())).isEmpty();
    }

    @Test
    void crossingRemoved_restoresRetroFinding() {
        Organization org = newOrg("retro-restore");
        seedTdsLedgers(org.getId());
        DayBookUploadJob job1 = completedJob(org.getId(), LocalDate.of(2025, 4, 1), LocalDate.of(2025, 4, 30));
        ParsedVoucher apr = seedTdsVoucher(org.getId(), job1.getId(), "g-apr", "PY/APR/1",
                LocalDate.of(2025, 4, 10), "h-apr", "Contractor ABC", entry("Professional Fees", "20000"));
        frozenTdsMonth(org.getId(), job1.getId(), LocalDate.of(2025, 4, 1), LocalDate.of(2025, 4, 30));

        DayBookUploadJob job2 = completedJob(org.getId(), LocalDate.of(2025, 4, 1), LocalDate.of(2025, 5, 31));
        seedTdsVoucher(org.getId(), job2.getId(), "g-apr", "PY/APR/1",
                LocalDate.of(2025, 4, 10), "h-apr", "Contractor ABC", entry("Professional Fees", "20000"));
        seedTdsVoucher(org.getId(), job2.getId(), "g-may", "PY/MAY/1",
                LocalDate.of(2025, 5, 10), "h-may", "Contractor ABC", entry("Professional Fees", "40000"));
        tamperComparisonService.compare(job2.getId());
        assertThat(tamperFindingRepository.findByOrganizationId(org.getId())).hasSize(1);

        DayBookUploadJob job3 = completedJob(org.getId(), LocalDate.of(2025, 4, 1), LocalDate.of(2025, 5, 31));
        seedTdsVoucher(org.getId(), job3.getId(), "g-apr", "PY/APR/1",
                LocalDate.of(2025, 4, 10), "h-apr", "Contractor ABC", entry("Professional Fees", "20000"));
        tamperComparisonService.compare(job3.getId());

        List<TamperFinding> findings = tamperFindingRepository.findByOrganizationId(org.getId());
        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).getVoucherKey()).isEqualTo(VoucherKeys.of(apr));
        assertThat(findings.get(0).getResolveStatus())
                .isEqualTo(com.arktech.superaccountant.masters.models.ResolveStatus.RESOLVED);
        assertThat(findings.get(0).getResolveNote()).startsWith("Restored");
    }

    @Test
    void crossingPlusEditedEarlierVoucher_oneRetroAndOneModified() {
        Organization org = newOrg("retro-edit");
        seedTdsLedgers(org.getId());
        DayBookUploadJob job1 = completedJob(org.getId(), LocalDate.of(2025, 4, 1), LocalDate.of(2025, 4, 30));
        seedTdsVoucher(org.getId(), job1.getId(), "g-apr", "PY/APR/1",
                LocalDate.of(2025, 4, 10), "h-apr", "Contractor ABC", entry("Professional Fees", "20000"));
        Workflow month1 = frozenTdsMonth(org.getId(), job1.getId(),
                LocalDate.of(2025, 4, 1), LocalDate.of(2025, 4, 30));

        DayBookUploadJob job2 = completedJob(org.getId(), LocalDate.of(2025, 4, 1), LocalDate.of(2025, 5, 31));
        seedTdsVoucher(org.getId(), job2.getId(), "g-apr", "PY/APR/1",
                LocalDate.of(2025, 4, 10), "h-apr-edit", "Contractor ABC", entry("Professional Fees", "20000"));
        seedTdsVoucher(org.getId(), job2.getId(), "g-may", "PY/MAY/1",
                LocalDate.of(2025, 5, 10), "h-may", "Contractor ABC", entry("Professional Fees", "40000"));

        tamperComparisonService.compare(job2.getId());

        List<TamperFinding> findings = tamperFindingRepository.findByOrganizationId(org.getId());
        assertThat(findings).hasSize(2);
        assertThat(findings).extracting(TamperFinding::getChangeKind)
                .containsExactlyInAnyOrder(TamperChangeKind.MODIFIED, TamperChangeKind.RETRO_FLAGGED);
        assertThat(findings).allSatisfy(f ->
                assertThat(f.getAffectedWorkflowIds()).isEqualTo(month1.getId().toString()));
    }
}
