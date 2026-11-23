package com.arktech.superaccountant.tdsapplicability;

import com.arktech.superaccountant.daybook.models.DayBookUploadJob;
import com.arktech.superaccountant.daybook.models.ParsedVoucher;
import com.arktech.superaccountant.daybook.models.ParsedVoucherEntry;
import com.arktech.superaccountant.daybook.repository.DayBookUploadJobRepository;
import com.arktech.superaccountant.daybook.repository.ParsedVoucherRepository;
import com.arktech.superaccountant.filedreturns.FiledFormType;
import com.arktech.superaccountant.filedreturns.FiledReturnLine;
import com.arktech.superaccountant.filedreturns.FiledReturnLineRepository;
import com.arktech.superaccountant.filedreturns.FiledReturnSnapshot;
import com.arktech.superaccountant.filedreturns.FiledReturnSnapshotRepository;
import com.arktech.superaccountant.filedreturns.FiledSourceKind;
import com.arktech.superaccountant.masters.models.LedgerCategory;
import com.arktech.superaccountant.masters.models.Organization;
import com.arktech.superaccountant.masters.models.UploadJob;
import com.arktech.superaccountant.masters.models.UploadJobStatus;
import com.arktech.superaccountant.masters.models.UploadLedgerSnapshot;
import com.arktech.superaccountant.masters.repository.OrganizationRepository;
import com.arktech.superaccountant.masters.repository.UploadJobRepository;
import com.arktech.superaccountant.masters.repository.UploadLedgerSnapshotRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Testcontainers
@TestPropertySource(properties = "JWT_SECRET=test-jwt-secret-must-be-at-least-32-characters-long")
class PriorYearTurnoverServiceIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private PriorYearTurnoverService service;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private UploadJobRepository uploadJobRepository;

    @Autowired
    private UploadLedgerSnapshotRepository snapshotRepository;

    @Autowired
    private DayBookUploadJobRepository dayBookJobRepository;

    @Autowired
    private ParsedVoucherRepository voucherRepository;

    @Autowired
    private FiledReturnSnapshotRepository filedSnapshotRepository;

    @Autowired
    private FiledReturnLineRepository filedLineRepository;

    private static final LocalDate FY_START = LocalDate.of(2025, 4, 1);
    private static final LocalDate FY_END = LocalDate.of(2026, 3, 31);

    private Organization createOrg(String name) {
        Organization org = new Organization(name);
        org.setPan("AAACA1234A");
        org.setFinancialYearStart(4);
        return organizationRepository.save(org);
    }

    private UUID latestMastersJob(UUID orgId) {
        UploadJob job = new UploadJob();
        job.setOrganizationId(orgId);
        job.setStatus(UploadJobStatus.COMPLETED);
        job.setUploadedBy("test");
        return uploadJobRepository.save(job).getId();
    }

    private void ledger(UUID orgId, UUID jobId, String name, LedgerCategory category, String groupChain) {
        UploadLedgerSnapshot s = new UploadLedgerSnapshot();
        s.setOrganizationId(orgId);
        s.setUploadJobId(jobId);
        s.setLedgerName(name);
        s.setCategory(category);
        s.setGroupChain(groupChain);
        snapshotRepository.save(s);
    }

    private UUID dayBookJob(UUID orgId, LocalDate coverageFrom, LocalDate coverageTo) {
        DayBookUploadJob job = new DayBookUploadJob();
        job.setOrganizationId(orgId);
        job.setStatus(UploadJobStatus.COMPLETED);
        job.setCoverageFrom(coverageFrom);
        job.setCoverageTo(coverageTo);
        return dayBookJobRepository.save(job).getId();
    }

    private void voucher(UUID orgId, UUID jobId, LocalDate date, boolean cancelled,
                         List<Object[]> entries) {
        // entries: [ledgerName, amount] — negative amount = credit (Tally convention)
        ParsedVoucher v = new ParsedVoucher();
        v.setOrganizationId(orgId);
        v.setUploadJobId(jobId);
        v.setVoucherTypeName("Sales");
        v.setVoucherDate(date);
        v.setCancelled(cancelled);
        List<ParsedVoucherEntry> parsed = new ArrayList<>();
        for (Object[] e : entries) {
            ParsedVoucherEntry entry = new ParsedVoucherEntry();
            entry.setParsedVoucher(v);
            entry.setOrganizationId(orgId);
            entry.setLedgerName((String) e[0]);
            BigDecimal amount = (BigDecimal) e[1];
            entry.setAmount(amount);
            entry.setDebit(amount.compareTo(BigDecimal.ZERO) > 0);
            parsed.add(entry);
        }
        v.setEntries(parsed);
        voucherRepository.save(v);
    }

    @Test
    void booksFigureForFullyCoveredYear() {
        Organization org = createOrg("Books Full Org " + UUID.randomUUID());
        UUID mastersJob = latestMastersJob(org.getId());
        ledger(org.getId(), mastersJob, "Sales Account", LedgerCategory.INCOME,
                "income statement|sales accounts");
        ledger(org.getId(), mastersJob, "Local Customer", LedgerCategory.CREDITOR,
                "sundry debtors");
        UUID job = dayBookJob(org.getId(), FY_START, FY_END);

        voucher(org.getId(), job, LocalDate.of(2025, 5, 10), false, List.of(
                new Object[]{"Sales Account", new BigDecimal("-6000000")},
                new Object[]{"Local Customer", new BigDecimal("6000000")}));
        voucher(org.getId(), job, LocalDate.of(2026, 1, 15), false, List.of(
                new Object[]{"Sales Account", new BigDecimal("100000")},
                new Object[]{"Local Customer", new BigDecimal("-100000")}));

        TurnoverEstimate estimate = service.estimateFor(org.getId(), 2026);

        assertEquals(2025, estimate.measuredFinancialYear());
        assertEquals(TurnoverEstimate.TurnoverSource.BOOKS, estimate.source());
        assertEquals(0, new BigDecimal("5900000").compareTo(estimate.amount()));
        assertTrue(estimate.complete());
    }

    @Test
    void partialCoverageIsMarkedIncomplete() {
        Organization org = createOrg("Books Partial Org " + UUID.randomUUID());
        UUID mastersJob = latestMastersJob(org.getId());
        ledger(org.getId(), mastersJob, "Sales Account", LedgerCategory.INCOME,
                "sales accounts");
        UUID job = dayBookJob(org.getId(), FY_START, LocalDate.of(2025, 9, 30));

        voucher(org.getId(), job, LocalDate.of(2025, 5, 10), false, List.of(
                new Object[]{"Sales Account", new BigDecimal("-2000000")},
                new Object[]{"Local Customer", new BigDecimal("2000000")}));

        TurnoverEstimate estimate = service.estimateFor(org.getId(), 2026);

        assertEquals(TurnoverEstimate.TurnoverSource.BOOKS, estimate.source());
        assertEquals(0, new BigDecimal("2000000").compareTo(estimate.amount()));
        assertFalse(estimate.complete());
    }

    @Test
    void gstr3bFallbackWhenNoBooks() {
        Organization org = createOrg("Gstr Fallback Org " + UUID.randomUUID());

        UUID snapshotId = snapshot(org.getId(), "2025-04", LocalDate.of(2025, 4, 1),
                LocalDate.of(2025, 4, 30));
        line(snapshotId, "3.1a", "4000000");
        line(snapshotId, "3.1c", "500000");

        TurnoverEstimate estimate = service.estimateFor(org.getId(), 2026);

        assertEquals(2025, estimate.measuredFinancialYear());
        assertEquals(TurnoverEstimate.TurnoverSource.GSTR3B, estimate.source());
        assertEquals(0, new BigDecimal("4500000").compareTo(estimate.amount()));
        assertFalse(estimate.complete());
    }

    @Test
    void noneWhenNoData() {
        Organization org = createOrg("No Data Org " + UUID.randomUUID());

        TurnoverEstimate estimate = service.estimateFor(org.getId(), 2026);

        assertEquals(2025, estimate.measuredFinancialYear());
        assertEquals(TurnoverEstimate.TurnoverSource.NONE, estimate.source());
        assertNull(estimate.amount());
        assertFalse(estimate.complete());
    }

    @Test
    void cancelledVouchersIgnored() {
        Organization org = createOrg("Cancelled Org " + UUID.randomUUID());
        UUID mastersJob = latestMastersJob(org.getId());
        ledger(org.getId(), mastersJob, "Sales Account", LedgerCategory.INCOME,
                "sales accounts");
        UUID job = dayBookJob(org.getId(), FY_START, FY_END);

        voucher(org.getId(), job, LocalDate.of(2025, 5, 10), false, List.of(
                new Object[]{"Sales Account", new BigDecimal("-3000000")},
                new Object[]{"Local Customer", new BigDecimal("3000000")}));
        voucher(org.getId(), job, LocalDate.of(2025, 6, 10), true, List.of(
                new Object[]{"Sales Account", new BigDecimal("-5000000")},
                new Object[]{"Local Customer", new BigDecimal("5000000")}));

        TurnoverEstimate estimate = service.estimateFor(org.getId(), 2026);

        assertEquals(TurnoverEstimate.TurnoverSource.BOOKS, estimate.source());
        assertEquals(0, new BigDecimal("3000000").compareTo(estimate.amount()));
        assertTrue(estimate.complete());
    }

    private UUID snapshot(UUID orgId, String filingPeriod, LocalDate coverStart, LocalDate coverEnd) {
        FiledReturnSnapshot s = new FiledReturnSnapshot();
        s.setOrganizationId(orgId);
        s.setFormType(FiledFormType.GSTR3B);
        s.setFilingPeriod(filingPeriod);
        s.setCoverStart(coverStart);
        s.setCoverEnd(coverEnd);
        s.setBaselineWorkflowId(UUID.randomUUID());
        s.setGstin("29AAACA1234A1Z5");
        s.setSourceKind(FiledSourceKind.MANUAL_ENTRY);
        s.setContentHash("hash-" + UUID.randomUUID());
        s.setImportedBy("test");
        return filedSnapshotRepository.save(s).getId();
    }

    private void line(UUID snapshotId, String tableCode, String taxable) {
        FiledReturnLine l = new FiledReturnLine();
        l.setSnapshotId(snapshotId);
        l.setLineNo(1);
        l.setTableCode(tableCode);
        l.setTaxable(new BigDecimal(taxable));
        filedLineRepository.save(l);
    }
}
