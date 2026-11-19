package com.arktech.superaccountant.gstr1.services;

import com.arktech.superaccountant.daybook.models.DayBookUploadJob;
import com.arktech.superaccountant.daybook.models.ParsedVoucher;
import com.arktech.superaccountant.daybook.models.ParsedVoucherEntry;
import com.arktech.superaccountant.daybook.repository.DayBookUploadJobRepository;
import com.arktech.superaccountant.daybook.repository.ParsedVoucherRepository;
import com.arktech.superaccountant.gstr1.models.PreparedReturn;
import com.arktech.superaccountant.gstr1.models.PreparedReturnRow;
import com.arktech.superaccountant.gstr1.repository.PreparedReturnRepository;
import com.arktech.superaccountant.gstr1.repository.PreparedReturnRowRepository;
import com.arktech.superaccountant.gst.mapping.GstHead;
import com.arktech.superaccountant.gst.mapping.TaxLedgerMapping;
import com.arktech.superaccountant.gst.mapping.TaxLedgerMappingRepository;
import com.arktech.superaccountant.gst.mapping.TaxLedgerRole;
import com.arktech.superaccountant.masters.models.Organization;
import com.arktech.superaccountant.masters.models.UploadJobStatus;
import com.arktech.superaccountant.masters.models.UploadLedgerSnapshot;
import com.arktech.superaccountant.masters.repository.OrganizationRepository;
import com.arktech.superaccountant.masters.repository.UploadLedgerSnapshotRepository;
import com.arktech.superaccountant.workflow.models.Workflow;
import com.arktech.superaccountant.workflow.models.WorkflowStage;
import com.arktech.superaccountant.workflow.repository.WorkflowRepository;
import com.arktech.superaccountant.workflow.service.WorkflowConflictException;
import com.arktech.superaccountant.workflow.service.WorkflowService;
import com.arktech.superaccountant.workflow.snapshot.VoucherKeys;
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
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Full Prepare lifecycle through WorkflowService: advance builds the prepared
 * return, finalize freezes it, un-finalize releases it, and the FILED guard
 * is untouched.
 */
@SpringBootTest
@Testcontainers
@TestPropertySource(properties = "JWT_SECRET=test-jwt-secret-must-be-at-least-32-characters-long")
class Gstr1PrepareFlowIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private WorkflowService workflowService;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private WorkflowRepository workflowRepository;

    @Autowired
    private DayBookUploadJobRepository dayBookUploadJobRepository;

    @Autowired
    private ParsedVoucherRepository parsedVoucherRepository;

    @Autowired
    private UploadLedgerSnapshotRepository snapshotRepository;

    @Autowired
    private TaxLedgerMappingRepository taxLedgerMappingRepository;

    @Autowired
    private PreparedReturnRepository preparedReturnRepository;

    @Autowired
    private PreparedReturnRowRepository rowRepository;

    @Autowired
    private Gstr1PrepareService gstr1PrepareService;

    @Test
    void advanceBuildsFinalizeFreezesUnfinalizeReleases() {
        Organization org = new Organization("Flow Org " + System.nanoTime());
        // no GSTIN on the org: the tax-mapping blocker counts a GSTIN company with no
        // masters upload, which is outside this IT's scope
        org.setFinancialYearStart(4);
        org.setBooksStartDate(LocalDate.of(2025, 4, 1));
        org = organizationRepository.save(org);
        UUID orgId = org.getId();

        Workflow w = workflowService.start(orgId, "tester", com.arktech.superaccountant.workflow.models.WorkflowType.GSTR1, "2025-04");

        UUID jobId = seedDayBook(orgId);
        seedMasters(orgId, jobId);

        w.setUploadJobId(jobId);
        w.setStage(WorkflowStage.FINDINGS_REVIEW);
        workflowRepository.save(w);

        // un-finalize before anything is finalized must still be rejected (stage guard)
        assertThrows(WorkflowConflictException.class,
                () -> workflowService.unfinalize(orgId, "tester", w.getId(), "oops"));

        Workflow advanced = workflowService.advanceToPrepare(orgId, "tester", w.getId());
        assertEquals(WorkflowStage.PREPARE, advanced.getStage());

        PreparedReturn pr = preparedReturnRepository
                .findByWorkflowIdAndReturnKind(w.getId(), "GSTR1")
                .orElseThrow(() -> new AssertionError("advanceToPrepare must build the prepared return"));
        assertTrue(rowRepository.findByPreparedReturnId(pr.getId()).size() > 0);
        assertNull(pr.getFrozenAt());

        Workflow finalized = workflowService.finalize(orgId, "tester", w.getId());
        assertEquals(WorkflowStage.FINALIZED, finalized.getStage());
        pr = preparedReturnRepository.findByWorkflowIdAndReturnKind(w.getId(), "GSTR1").orElseThrow();
        assertNotNull(pr.getFrozenAt(), "finalize must freeze the prepared return");
        List<UUID> frozenRowIds = rowRepository.findByPreparedReturnId(pr.getId()).stream()
                .map(PreparedReturnRow::getId).toList();

        // a later day-book re-upload under the same job must not change the frozen return
        captureExtraVoucher(orgId, jobId, "INV-EXTRA");

        // finalize is guarded: cannot finalize twice
        assertThrows(WorkflowConflictException.class, () -> workflowService.finalize(orgId, "tester", w.getId()));

        Workflow unfinalized = workflowService.unfinalize(orgId, "tester", w.getId(), "correction needed");
        assertEquals(WorkflowStage.PREPARE, unfinalized.getStage());
        pr = preparedReturnRepository.findByWorkflowIdAndReturnKind(w.getId(), "GSTR1").orElseThrow();
        assertNull(pr.getFrozenAt(), "un-finalize must release the freeze");

        // rebuild after release picks up the extra voucher
        gstr1PrepareService.buildOrRebuild(unfinalized);
        assertTrue(rowRepository.findByPreparedReturnId(pr.getId()).size() > frozenRowIds.size(),
                "rebuild after release must include the re-uploaded voucher");
        assertTrue(rowRepository.findByPreparedReturnId(pr.getId()).stream()
                        .map(PreparedReturnRow::getId).noneMatch(frozenRowIds::contains),
                "rebuild must replace rows, not append to them");

        // the FILED guard is untouched: finalized -> filed -> unfinalize rejected
        Workflow refinalized = workflowService.finalize(orgId, "tester", w.getId());
        Workflow filed = workflowService.recordFiling(orgId, "tester", w.getId(), "ARN-1", LocalDate.of(2025, 5, 5));
        assertEquals(WorkflowStage.FILED, filed.getStage());
        assertThrows(WorkflowConflictException.class,
                () -> workflowService.unfinalize(orgId, "tester", refinalized.getId(), "late"));
    }

    // ---------- seed helpers ----------

    private UUID seedDayBook(UUID orgId) {
        DayBookUploadJob job = new DayBookUploadJob();
        job.setOrganizationId(orgId);
        job.setFileName("flow-daybook.json");
        job.setStatus(UploadJobStatus.COMPLETED);
        job.setCoverageFrom(LocalDate.of(2025, 4, 1));
        job.setCoverageTo(LocalDate.of(2025, 4, 30));
        job.setUploadedBy("tester");
        job = dayBookUploadJobRepository.save(job);

        ParsedVoucher v = new ParsedVoucher();
        v.setUploadJobId(job.getId());
        v.setOrganizationId(orgId);
        v.setVoucherTypeName("Sales");
        v.setVoucherNumber("INV-1");
        v.setVoucherDate(LocalDate.of(2025, 4, 10));
        v.setPartyLedgerName("Sundry Debtors");
        v.setPartyGstin("29AABCU9603R1ZJ");
        v.setPartyGstRegistrationType("Regular");
        v.setPlaceOfSupply("Karnataka");
        v.setCaptureVersion(1);
        v.setTotalDebit(new BigDecimal("11800.00"));
        v.setTotalCredit(new BigDecimal("11800.00"));
        List<ParsedVoucherEntry> entries = new ArrayList<>();
        entries.add(entry(v, "Sales Account", "10000.00", true, "18", false));
        entries.add(entry(v, "Output CGST", "900.00", true, null, false));
        entries.add(entry(v, "Output SGST", "900.00", true, null, false));
        entries.add(entry(v, "Sundry Debtors", "-11800.00", false, null, true));
        v.setEntries(entries);
        parsedVoucherRepository.save(v);

        UploadLedgerSnapshot salesLedger = new UploadLedgerSnapshot();
        salesLedger.setUploadJobId(job.getId());
        salesLedger.setOrganizationId(orgId);
        salesLedger.setLedgerName("Sales Account");
        salesLedger.setHsnSacCode("997331");
        snapshotRepository.save(salesLedger);
        return job.getId();
    }

    private void captureExtraVoucher(UUID orgId, UUID jobId, String number) {
        ParsedVoucher v = new ParsedVoucher();
        v.setUploadJobId(jobId);
        v.setOrganizationId(orgId);
        v.setVoucherTypeName("Sales");
        v.setVoucherNumber(number);
        v.setVoucherDate(LocalDate.of(2025, 4, 20));
        v.setPartyLedgerName("Sundry Debtors");
        v.setPartyGstin("29AABCU9603R1ZJ");
        v.setPlaceOfSupply("Karnataka");
        v.setCaptureVersion(1);
        v.setTotalDebit(new BigDecimal("5900.00"));
        v.setTotalCredit(new BigDecimal("5900.00"));
        List<ParsedVoucherEntry> entries = new ArrayList<>();
        entries.add(entry(v, "Sales Account", "5000.00", true, "18", false));
        entries.add(entry(v, "Output CGST", "450.00", true, null, false));
        entries.add(entry(v, "Output SGST", "450.00", true, null, false));
        entries.add(entry(v, "Sundry Debtors", "-5900.00", false, null, true));
        v.setEntries(entries);
        parsedVoucherRepository.save(v);
    }

    private ParsedVoucherEntry entry(ParsedVoucher voucher, String ledger, String amount,
                                     boolean isDebit, String gstRate, boolean isParty) {
        ParsedVoucherEntry e = new ParsedVoucherEntry();
        e.setParsedVoucher(voucher);
        e.setOrganizationId(voucher.getOrganizationId());
        e.setLedgerName(ledger);
        e.setAmount(new BigDecimal(amount));
        e.setDebit(isDebit);
        if (gstRate != null) {
            e.setGstRate(new BigDecimal(gstRate));
            e.setGstTaxability("Taxable");
        }
        e.setPartyLedger(isParty);
        return e;
    }

    private void seedMasters(UUID orgId, UUID jobId) {
        taxMapping(orgId, "Output CGST", GstHead.CGST);
        taxMapping(orgId, "Output SGST", GstHead.SGST);
        taxMapping(orgId, "Output IGST", GstHead.IGST);
        taxMapping(orgId, "Output Cess", GstHead.CESS);
    }

    private void taxMapping(UUID orgId, String ledgerName, GstHead head) {
        TaxLedgerMapping m = new TaxLedgerMapping();
        m.setOrganizationId(orgId);
        m.setLedgerName(ledgerName);
        m.setLedgerKey(ledgerName.trim().toLowerCase());
        m.setRole(TaxLedgerRole.OUTPUT);
        m.setHead(head);
        m.setSavedBy("tester");
        m.setSavedAt(Instant.now());
        taxLedgerMappingRepository.save(m);
    }
}
