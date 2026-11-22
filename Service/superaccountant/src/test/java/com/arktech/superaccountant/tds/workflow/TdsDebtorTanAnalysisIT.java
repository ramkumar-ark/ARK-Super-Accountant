package com.arktech.superaccountant.tds.workflow;

import com.arktech.superaccountant.daybook.models.DayBookUploadJob;
import com.arktech.superaccountant.daybook.models.ParsedVoucher;
import com.arktech.superaccountant.daybook.models.ParsedVoucherEntry;
import com.arktech.superaccountant.daybook.repository.DayBookUploadJobRepository;
import com.arktech.superaccountant.daybook.repository.ParsedVoucherRepository;
import com.arktech.superaccountant.masters.models.LedgerCategory;
import com.arktech.superaccountant.masters.models.Organization;
import com.arktech.superaccountant.masters.models.UploadJob;
import com.arktech.superaccountant.masters.models.UploadJobStatus;
import com.arktech.superaccountant.masters.models.UploadLedgerSnapshot;
import com.arktech.superaccountant.masters.repository.OrganizationRepository;
import com.arktech.superaccountant.masters.repository.UploadJobRepository;
import com.arktech.superaccountant.masters.repository.UploadLedgerSnapshotRepository;
import com.arktech.superaccountant.tds.models.TdsFinding;
import com.arktech.superaccountant.tds.models.TdsFindingType;
import com.arktech.superaccountant.tds.repository.DebtorTanRepository;
import com.arktech.superaccountant.tds.repository.TdsFindingRepository;
import com.arktech.superaccountant.tds.services.DebtorTanStore;
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
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = "JWT_SECRET=test-jwt-secret-must-be-at-least-32-characters-long")
class TdsDebtorTanAnalysisIT {

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
    @Autowired UploadJobRepository uploadJobRepository;
    @Autowired UploadLedgerSnapshotRepository snapshotRepository;
    @Autowired DayBookUploadJobRepository dayBookJobRepository;
    @Autowired ParsedVoucherRepository parsedVoucherRepository;
    @Autowired TdsFindingRepository findingRepository;
    @Autowired DebtorTanRepository debtorTanRepository;
    @Autowired DebtorTanStore debtorTanStore;

    UUID orgId;
    UUID dayBookJobId;
    UUID workflowId;

    @BeforeEach
    void seed() {
        Organization org = new Organization();
        org.setName("Debtor TAN Analysis IT " + UUID.randomUUID());
        org.setBooksStartDate(LocalDate.of(2025, 4, 1));
        orgId = organizationRepository.save(org).getId();

        UploadJob mastersJob = new UploadJob();
        mastersJob.setOrganizationId(orgId);
        mastersJob.setFileName("masters.xml");
        mastersJob.setStatus(UploadJobStatus.COMPLETED);
        mastersJob.setUploadedBy("it");
        UUID mastersJobId = uploadJobRepository.save(mastersJob).getId();
        snapshot(mastersJobId, "TDS Receivable 194J", LedgerCategory.OTHER, null);
        snapshot(mastersJobId, "Acme Corp", LedgerCategory.OTHER, "Sundry Debtors|Current Assets");

        DayBookUploadJob dayBookJob = new DayBookUploadJob();
        dayBookJob.setOrganizationId(orgId);
        dayBookJob.setFileName("daybook.xml");
        dayBookJob.setStatus(UploadJobStatus.COMPLETED);
        dayBookJob.setUploadedBy("it");
        dayBookJobId = dayBookJobRepository.save(dayBookJob).getId();

        ParsedVoucher voucher = new ParsedVoucher();
        voucher.setUploadJobId(dayBookJobId);
        voucher.setOrganizationId(orgId);
        voucher.setVoucherGuid("dv-debtor-tan");
        voucher.setVoucherNumber("V1");
        voucher.setVoucherTypeName("Journal");
        voucher.setVoucherDate(LocalDate.of(2025, 4, 10));
        voucher.setPartyLedgerName("Acme Corp");
        voucher.setContentHash("b".repeat(64));
        voucher.setCancelled(false);
        List<ParsedVoucherEntry> entries = new ArrayList<>();
        entry(voucher, entries, "TDS Receivable 194J", new BigDecimal("1000.00"), true);
        entry(voucher, entries, "Acme Corp", new BigDecimal("-1000.00"), false);
        voucher.setEntries(entries);
        parsedVoucherRepository.save(voucher);

        Workflow workflow = new Workflow();
        workflow.setOrganizationId(orgId);
        workflow.setType(WorkflowType.TDS_PAYMENT);
        workflow.setPeriodStart(LocalDate.of(2025, 4, 1));
        workflow.setPeriodEnd(LocalDate.of(2025, 4, 30));
        workflow.setPeriodKey("2025-04");
        workflow.setFinancialYear(2025);
        workflow.setStage(WorkflowStage.DATA_UPLOADED);
        workflow.setUploadJobId(dayBookJobId);
        workflow.setCreatedBy("it");
        workflowId = workflowRepository.save(workflow).getId();
    }

    @Test
    void analysisPersistsMissingTanFindingForDebtorWithTdsReceivable() {
        analysisService.run(orgId, workflowId, "it");

        List<TdsFinding> findings = findings();
        assertThat(findings).hasSize(1);
        TdsFinding finding = findings.get(0);
        assertThat(finding.getFindingType())
                .isEqualTo(TdsFindingType.DEBTOR_TDS_RECEIVABLE_MISSING_TAN);
        assertThat(finding.getPartyLedgerName()).isEqualTo("Acme Corp");
        assertThat(finding.getSeverity().name()).isEqualTo("LOW");
    }

    @Test
    void storedTanSuppressesFindingOnTheNextAnalysis() {
        debtorTanStore.saveTan(orgId, "Acme Corp", "BLRA12345C", null, null, "it", Instant.now());

        analysisService.run(orgId, workflowId, "it");

        assertThat(findings()).isEmpty();
    }

    private List<TdsFinding> findings() {
        return findingRepository.findByWorkflowIdAndSupersededAtIsNullOrderByVoucherDateAsc(workflowId)
                .stream()
                .filter(f -> f.getFindingType()
                        == TdsFindingType.DEBTOR_TDS_RECEIVABLE_MISSING_TAN)
                .toList();
    }

    private void entry(ParsedVoucher v, List<ParsedVoucherEntry> entries, String ledger,
                       BigDecimal amount, boolean debit) {
        ParsedVoucherEntry e = new ParsedVoucherEntry();
        e.setParsedVoucher(v);
        e.setOrganizationId(orgId);
        e.setLedgerName(ledger);
        e.setAmount(amount);
        e.setDebit(debit);
        entries.add(e);
    }

    private void snapshot(UUID jobId, String ledger, LedgerCategory category, String groupChain) {
        UploadLedgerSnapshot snapshot = new UploadLedgerSnapshot();
        snapshot.setUploadJobId(jobId);
        snapshot.setOrganizationId(orgId);
        snapshot.setLedgerName(ledger);
        snapshot.setCategory(category);
        snapshot.setGroupChain(groupChain);
        snapshotRepository.save(snapshot);
    }
}
