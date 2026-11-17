package com.arktech.superaccountant.tds.workflow;

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
import com.arktech.superaccountant.login.security.services.UserDetailsImpl;
import com.arktech.superaccountant.masters.models.LedgerCategory;
import com.arktech.superaccountant.masters.models.Organization;
import com.arktech.superaccountant.masters.models.ResolveStatus;
import com.arktech.superaccountant.masters.models.UploadJob;
import com.arktech.superaccountant.masters.models.UploadJobStatus;
import com.arktech.superaccountant.masters.models.UploadLedgerSnapshot;
import com.arktech.superaccountant.masters.repository.OrganizationRepository;
import com.arktech.superaccountant.masters.repository.UploadJobRepository;
import com.arktech.superaccountant.masters.repository.UploadLedgerSnapshotRepository;
import com.arktech.superaccountant.tds.models.TdsFinding;
import com.arktech.superaccountant.tds.models.TdsFindingType;
import com.arktech.superaccountant.tds.models.TdsPaymentDetail;
import com.arktech.superaccountant.tds.repository.TdsFindingRepository;
import com.arktech.superaccountant.tds.repository.TdsPaymentDetailRepository;
import com.arktech.superaccountant.workflow.analysis.WorkflowAnalysisService;
import com.arktech.superaccountant.workflow.models.Workflow;
import com.arktech.superaccountant.workflow.models.WorkflowStage;
import com.arktech.superaccountant.workflow.models.WorkflowType;
import com.arktech.superaccountant.workflow.repository.WorkflowRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end tests (real Postgres via Testcontainers) of TdsPaymentAnalyzer inside
 * WorkflowAnalysisService. NOT @Transactional: each test uses its own organization.
 * Needs Docker.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = "JWT_SECRET=test-jwt-secret-must-be-at-least-32-characters-long")
class TdsPaymentAnalysisIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @org.springframework.test.context.DynamicPropertySource
    static void configureProperties(org.springframework.test.context.DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired WorkflowRepository workflowRepository;
    @Autowired WorkflowAnalysisService analysisService;
    @Autowired OrganizationRepository organizationRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired UserRepository userRepository;
    @Autowired UploadJobRepository uploadJobRepository;
    @Autowired UploadLedgerSnapshotRepository snapshotRepository;
    @Autowired DayBookUploadJobRepository dayBookJobRepository;
    @Autowired ParsedVoucherRepository parsedVoucherRepository;
    @Autowired TdsFindingRepository findingRepository;
    @Autowired TdsFindingSource findingSource;
    @Autowired org.springframework.transaction.support.TransactionTemplate tx;
    @Autowired TdsPaymentDetailRepository paymentDetailRepository;

    UUID orgId;
    UserDetailsImpl principal;
    UUID mastersJobId;
    UUID dayBookJobId;

    @BeforeEach
    void seed() {
        Organization org = new Organization();
        org.setName("TDS Payment IT " + UUID.randomUUID());
        org.setBooksStartDate(LocalDate.of(2025, 4, 1));
        orgId = organizationRepository.save(org).getId();

        Role role = roleRepository.findByName(ERole.ROLE_ACCOUNTANT).orElseThrow();
        User user = new User();
        user.setUsername("acct-" + UUID.randomUUID());
        user.setPassword("x");
        user.setEmail("acct-" + UUID.randomUUID() + "@t.com");
        user.setRole(role);
        principal = UserDetailsImpl.build(userRepository.save(user), orgId);

        UploadJob mastersJob = new UploadJob();
        mastersJob.setOrganizationId(orgId);
        mastersJob.setFileName("masters.xml");
        mastersJob.setStatus(UploadJobStatus.COMPLETED);
        mastersJob.setUploadedBy("it");
        mastersJobId = uploadJobRepository.save(mastersJob).getId();

        snapshot(mastersJobId, "Subcontractor Charges", LedgerCategory.EXPENSE, true, "194C", null);
        snapshot(mastersJobId, "ABC Contractors", LedgerCategory.CREDITOR, null, null, null);
        snapshot(mastersJobId, "TDS Payable 194C", LedgerCategory.TDS, true, "194C", null);

        DayBookUploadJob dayBookJob = new DayBookUploadJob();
        dayBookJob.setOrganizationId(orgId);
        dayBookJob.setFileName("daybook.xml");
        dayBookJob.setStatus(UploadJobStatus.COMPLETED);
        dayBookJob.setUploadedBy("it");
        dayBookJobId = dayBookJobRepository.save(dayBookJob).getId();

        ParsedVoucher v = new ParsedVoucher();
        v.setUploadJobId(dayBookJobId);
        v.setOrganizationId(orgId);
        v.setVoucherGuid("dv-1");
        v.setVoucherNumber("V1");
        v.setVoucherTypeName("Journal");
        v.setVoucherDate(LocalDate.of(2025, 4, 10));
        v.setPartyLedgerName("ABC Contractors");
        v.setContentHash("a".repeat(64));
        v.setCancelled(false);
        List<ParsedVoucherEntry> entries = new ArrayList<>();
        entry(v, entries, "Subcontractor Charges", new BigDecimal("100000"), true, false);
        entry(v, entries, "ABC Contractors", new BigDecimal("-99000"), false, true);
        entry(v, entries, "TDS Payable 194C", new BigDecimal("-1000"), false, false);
        v.setEntries(entries);
        parsedVoucherRepository.save(v);
    }

    private void entry(ParsedVoucher v, List<ParsedVoucherEntry> entries, String ledger,
                       BigDecimal amount, boolean debit, boolean party) {
        ParsedVoucherEntry e = new ParsedVoucherEntry();
        e.setParsedVoucher(v);
        e.setOrganizationId(orgId);
        e.setLedgerName(ledger);
        e.setAmount(amount);
        e.setDebit(debit);
        e.setPartyLedger(party);
        entries.add(e);
    }

    private void snapshot(UUID jobId, String ledger, LedgerCategory cat, Boolean tdsApplicable,
                          String tdsSection, String pan) {
        UploadLedgerSnapshot s = new UploadLedgerSnapshot();
        s.setUploadJobId(jobId);
        s.setOrganizationId(orgId);
        s.setLedgerName(ledger);
        s.setCategory(cat);
        s.setTdsApplicable(tdsApplicable);
        s.setTdsSection(tdsSection);
        s.setPan(pan);
        snapshotRepository.save(s);
    }

    private Workflow seedWorkflow() {
        return seedWorkflow(dayBookJobId, null);
    }

    private Workflow seedWorkflow(UUID uploadJobId, UUID supersedesWorkflowId) {
        if (supersedesWorkflowId != null) {
            Workflow previous = workflowRepository.findById(supersedesWorkflowId).orElseThrow();
            previous.setActive(false);
            workflowRepository.save(previous);
        }
        Workflow w = new Workflow();
        w.setOrganizationId(orgId);
        w.setType(WorkflowType.TDS_PAYMENT);
        w.setPeriodStart(LocalDate.of(2025, 4, 1));
        w.setPeriodEnd(LocalDate.of(2025, 4, 30));
        w.setPeriodKey("2025-04");
        w.setFinancialYear(2025);
        w.setStage(WorkflowStage.DATA_UPLOADED);
        w.setUploadJobId(uploadJobId);
        w.setSupersedesWorkflowId(supersedesWorkflowId);
        w.setCreatedBy("it");
        return workflowRepository.save(w);
    }

    private UUID newDayBookJob(String fileName) {
        DayBookUploadJob job = new DayBookUploadJob();
        job.setOrganizationId(orgId);
        job.setFileName(fileName);
        job.setStatus(UploadJobStatus.COMPLETED);
        job.setUploadedBy("it");
        return dayBookJobRepository.save(job).getId();
    }

    private void addVoucher(UUID jobId, String guid, String number, String party,
                            BigDecimal base, BigDecimal tds, String contentHash) {
        ParsedVoucher v = new ParsedVoucher();
        v.setUploadJobId(jobId);
        v.setOrganizationId(orgId);
        v.setVoucherGuid(guid);
        v.setVoucherNumber(number);
        v.setVoucherTypeName("Journal");
        v.setVoucherDate(LocalDate.of(2025, 4, 10));
        v.setPartyLedgerName(party);
        v.setContentHash(contentHash);
        v.setCancelled(false);
        List<ParsedVoucherEntry> entries = new ArrayList<>();
        entry(v, entries, "Subcontractor Charges", base, true, false);
        entry(v, entries, party, base.add(tds), false, true);
        entry(v, entries, "TDS Payable 194C", tds, false, false);
        v.setEntries(entries);
        parsedVoucherRepository.save(v);
    }

    private List<TdsFinding> currentFindings(UUID workflowId) {
        return findingRepository
                .findByWorkflowIdAndSupersededAtIsNullOrderByVoucherDateAsc(workflowId);
    }

    private TdsFinding finding(List<TdsFinding> findings, String party, TdsFindingType type) {
        return findings.stream()
                .filter(f -> type == f.getFindingType() && party.equals(f.getPartyLedgerName()))
                .findFirst().orElseThrow();
    }

    @Test
    void run_movesToFindingsReviewWithTdsFindings() {
        Workflow w = seedWorkflow();

        analysisService.run(orgId, w.getId(), "it");

        Workflow reloaded = workflowRepository.findById(w.getId()).orElseThrow();
        assertEquals(WorkflowStage.FINDINGS_REVIEW, reloaded.getStage());
        List<TdsFinding> findings = findingRepository
                .findByWorkflowIdAndSupersededAtIsNullOrderByVoucherDateAsc(w.getId());
        assertTrue(findings.stream()
                .anyMatch(f -> f.getFindingType() == TdsFindingType.PAN_MISSING));
        TdsPaymentDetail detail = paymentDetailRepository.findByWorkflowId(w.getId())
                .orElseThrow();
        assertNotNull(detail.getTdsDeducted());
        assertEquals(0, detail.getTdsDeducted()
                .compareTo(new BigDecimal("1000.00")));
    }

    @Test
    void reanalysis_listsApprovedFindingOnce() {
        Workflow w = seedWorkflow();
        analysisService.run(orgId, w.getId(), "it");

        List<TdsFinding> first = findingRepository
                .findByWorkflowIdAndSupersededAtIsNullOrderByVoucherDateAsc(w.getId());
        first.stream()
                .filter(f -> f.getFindingType() == TdsFindingType.PAN_MISSING)
                .forEach(f -> {
                    f.setResolveStatus(ResolveStatus.APPROVED);
                    findingRepository.save(f);
                });
        Workflow reset = workflowRepository.findById(w.getId()).orElseThrow();
        reset.setStage(WorkflowStage.DATA_UPLOADED);
        workflowRepository.save(reset);

        analysisService.run(orgId, w.getId(), "it");

        List<TdsFinding> current = findingRepository
                .findByWorkflowIdAndSupersededAtIsNullOrderByVoucherDateAsc(w.getId());
        List<TdsFinding> panMissing = current.stream()
                .filter(f -> f.getFindingType() == TdsFindingType.PAN_MISSING)
                .toList();
        assertEquals(1, panMissing.size());
        assertEquals(ResolveStatus.APPROVED, panMissing.get(0).getResolveStatus());
    }

    @Test
    void predecessorFindings_areResolvedOrDiscardedOnReupload() {
        snapshot(mastersJobId, "XYZ Enterprises", LedgerCategory.CREDITOR, null, null, null);
        addVoucher(dayBookJobId, "dv-2", "V2", "XYZ Enterprises",
                new BigDecimal("50000"), new BigDecimal("-1000"), "c".repeat(64));
        Workflow a = seedWorkflow();
        analysisService.run(orgId, a.getId(), "it");

        List<TdsFinding> first = currentFindings(a.getId());
        TdsFinding abcPan = finding(first, "ABC Contractors", TdsFindingType.PAN_MISSING);
        TdsFinding xyzPan = finding(first, "XYZ Enterprises", TdsFindingType.PAN_MISSING);
        abcPan.setResolveStatus(ResolveStatus.APPROVED);
        findingRepository.save(abcPan);
        Workflow finalized = workflowRepository.findById(a.getId()).orElseThrow();
        finalized.setStage(WorkflowStage.FINALIZED);
        workflowRepository.save(finalized);

        UUID jobB = newDayBookJob("daybook-b.xml");
        addVoucher(jobB, "dv-1", "V1", "ABC Contractors",
                new BigDecimal("100000"), new BigDecimal("-1000"), "a".repeat(64));
        Workflow b = seedWorkflow(jobB, a.getId());
        analysisService.run(orgId, b.getId(), "it");

        TdsFinding abcReloaded = findingRepository.findById(abcPan.getId()).orElseThrow();
        assertEquals(ResolveStatus.APPROVED, abcReloaded.getResolveStatus());
        TdsFinding xyzReloaded = findingRepository.findById(xyzPan.getId()).orElseThrow();
        assertEquals(ResolveStatus.RESOLVED, xyzReloaded.getResolveStatus());
        assertEquals("system", xyzReloaded.getResolvedBy());
        assertTrue(xyzReloaded.getResolveNote().toLowerCase().contains("re-upload"));

        TdsFinding carried = finding(currentFindings(b.getId()), "ABC Contractors",
                TdsFindingType.PAN_MISSING);
        assertEquals(ResolveStatus.APPROVED, carried.getResolveStatus());
        assertEquals(abcPan.getId(), carried.getCarriedFromFindingId());
    }

    @Test
    void predecessorChangedEvidence_resetsToOpenWithoutReviewState() {
        Workflow a = seedWorkflow();
        analysisService.run(orgId, a.getId(), "it");
        TdsFinding abcPan = finding(currentFindings(a.getId()), "ABC Contractors",
                TdsFindingType.PAN_MISSING);
        abcPan.setResolveStatus(ResolveStatus.APPROVED);
        abcPan.setProposedValue("AAAPL1234C");
        findingRepository.save(abcPan);

        UUID jobC = newDayBookJob("daybook-c.xml");
        addVoucher(jobC, "dv-1", "V1", "ABC Contractors",
                new BigDecimal("120000"), new BigDecimal("-1000"), "d".repeat(64));
        Workflow c = seedWorkflow(jobC, a.getId());
        analysisService.run(orgId, c.getId(), "it");

        TdsFinding reopened = finding(currentFindings(c.getId()), "ABC Contractors",
                TdsFindingType.PAN_MISSING);
        assertEquals(ResolveStatus.OPEN, reopened.getResolveStatus());
        assertEquals(null, reopened.getProposedValue());
        assertEquals(abcPan.getId(), reopened.getCarriedFromFindingId());
        assertEquals(ResolveStatus.APPROVED,
                findingRepository.findById(abcPan.getId()).orElseThrow().getResolveStatus());
    }

    @Test
    void reupload_listsPredecessorAndSupersededAutoResolvedFindingsReadOnly() {
        snapshot(mastersJobId, "XYZ Enterprises", LedgerCategory.CREDITOR, null, null, null);
        snapshot(mastersJobId, "QRS Traders", LedgerCategory.CREDITOR, null, null, null);
        addVoucher(dayBookJobId, "dv-2", "V2", "XYZ Enterprises",
                new BigDecimal("50000"), new BigDecimal("-1000"), "c".repeat(64));
        Workflow a = seedWorkflow();
        analysisService.run(orgId, a.getId(), "it");
        TdsFinding xyzPan = finding(currentFindings(a.getId()), "XYZ Enterprises",
                TdsFindingType.PAN_MISSING);

        UUID jobB = newDayBookJob("daybook-b.xml");
        addVoucher(jobB, "dv-1", "V1", "ABC Contractors",
                new BigDecimal("100000"), new BigDecimal("-1000"), "a".repeat(64));
        addVoucher(jobB, "dv-3", "V3", "QRS Traders",
                new BigDecimal("70000"), new BigDecimal("-1000"), "e".repeat(64));
        Workflow b = seedWorkflow(jobB, a.getId());
        analysisService.run(orgId, b.getId(), "it");
        TdsFinding qrsPan = finding(currentFindings(b.getId()), "QRS Traders",
                TdsFindingType.PAN_MISSING);

        parsedVoucherRepository.findAll().stream()
                .filter(v -> jobB.equals(v.getUploadJobId()) && "dv-3".equals(v.getVoucherGuid()))
                .forEach(parsedVoucherRepository::delete);
        Workflow reset = workflowRepository.findById(b.getId()).orElseThrow();
        reset.setStage(WorkflowStage.DATA_UPLOADED);
        workflowRepository.save(reset);
        analysisService.run(orgId, b.getId(), "it");

        Workflow reloaded = workflowRepository.findById(b.getId()).orElseThrow();
        var listed = findingSource.list(reloaded);
        var autoResolvedIds = listed.stream()
                .filter(v -> "system".equals(v.resolvedBy()))
                .map(v -> v.id())
                .toList();
        assertTrue(autoResolvedIds.contains(xyzPan.getId()));
        assertTrue(autoResolvedIds.contains(qrsPan.getId()));
        assertTrue(listed.stream().filter(v -> "system".equals(v.resolvedBy()))
                .noneMatch(v -> v.blocking()));

        long blockingBefore = findingSource.blockingCount(reloaded);
        assertEquals(currentFindings(b.getId()).stream()
                .filter(f -> f.getResolveStatus() == ResolveStatus.OPEN).count(), blockingBefore);
        assertTrue(Boolean.TRUE.equals(tx.execute(st -> findingSource.lockForUpdate(reloaded, xyzPan.getId()).isEmpty())));
        assertTrue(Boolean.TRUE.equals(tx.execute(st -> findingSource.lockForUpdate(reloaded, qrsPan.getId()).isEmpty())));
    }
}
