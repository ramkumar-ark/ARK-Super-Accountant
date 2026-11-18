package com.arktech.superaccountant.gstr1.services;

import com.arktech.superaccountant.daybook.models.ParsedVoucher;
import com.arktech.superaccountant.daybook.models.ParsedVoucherEntry;
import com.arktech.superaccountant.daybook.repository.ParsedVoucherRepository;
import com.arktech.superaccountant.gstr1.models.Gstr1FillIn;
import com.arktech.superaccountant.gstr1.models.Gstr1FillInAttribute;
import com.arktech.superaccountant.gstr1.repository.Gstr1FillInRepository;
import com.arktech.superaccountant.gstr1.models.Gstr1FillInScope;
import com.arktech.superaccountant.gstr1.models.PreparedReturn;
import com.arktech.superaccountant.gstr1.models.PreparedReturnRow;
import com.arktech.superaccountant.gstr1.repository.PreparedReturnRepository;
import com.arktech.superaccountant.gstr1.repository.PreparedReturnRowRepository;
import com.arktech.superaccountant.masters.models.Organization;
import com.arktech.superaccountant.masters.models.UploadLedgerSnapshot;
import com.arktech.superaccountant.masters.repository.OrganizationRepository;
import com.arktech.superaccountant.masters.repository.UploadLedgerSnapshotRepository;
import com.arktech.superaccountant.workflow.models.Workflow;
import com.arktech.superaccountant.workflow.models.WorkflowStage;
import com.arktech.superaccountant.workflow.models.WorkflowType;
import com.arktech.superaccountant.workflow.repository.WorkflowRepository;
import com.arktech.superaccountant.workflow.snapshot.VoucherKeys;
import com.arktech.superaccountant.gst.mapping.GstHead;
import com.arktech.superaccountant.gst.mapping.TaxLedgerMapping;
import com.arktech.superaccountant.gst.mapping.TaxLedgerMappingRepository;
import com.arktech.superaccountant.gst.mapping.TaxLedgerRole;
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
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Testcontainers
@TestPropertySource(properties = "JWT_SECRET=test-jwt-secret-must-be-at-least-32-characters-long")
class Gstr1PrepareServiceTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private Gstr1PrepareService prepareService;

    @Autowired
    private Gstr1PrepareHook prepareHook;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private WorkflowRepository workflowRepository;

    @Autowired
    private ParsedVoucherRepository parsedVoucherRepository;

    @Autowired
    private UploadLedgerSnapshotRepository snapshotRepository;

    @Autowired
    private TaxLedgerMappingRepository taxLedgerMappingRepository;

    @Autowired
    private Gstr1FillInRepository fillInRepository;

    @Autowired
    private PreparedReturnRepository preparedReturnRepository;

    @Autowired
    private PreparedReturnRowRepository rowRepository;

    private static int counter = 0;

    // ---------- seed helpers ----------

    private Organization organization() {
        Organization org = new Organization("Prepare Org " + (++counter));
        org.setGstin("29ABCDE1234F1Z5");
        org.setFinancialYearStart(4);
        org.setBooksStartDate(LocalDate.of(2025, 4, 1));
        return organizationRepository.save(org);
    }

    private Workflow gstr1Workflow(UUID orgId) {
        Workflow w = new Workflow();
        w.setOrganizationId(orgId);
        w.setType(WorkflowType.GSTR1);
        w.setPeriodStart(LocalDate.of(2025, 4, 1));
        w.setPeriodEnd(LocalDate.of(2025, 4, 30));
        w.setPeriodKey("2025-04");
        w.setFinancialYear(2025);
        w.setStage(WorkflowStage.PREPARE);
        w.setActive(true);
        w.setCreatedBy("tester");
        return workflowRepository.save(w);
    }

    private Workflow workflowOfType(UUID orgId, WorkflowType type) {
        Workflow w = new Workflow();
        w.setOrganizationId(orgId);
        w.setType(type);
        w.setPeriodStart(LocalDate.of(2025, 4, 1));
        w.setPeriodEnd(LocalDate.of(2025, 4, 30));
        w.setPeriodKey("2025-04");
        w.setFinancialYear(2025);
        w.setStage(WorkflowStage.PREPARE);
        w.setActive(true);
        w.setCreatedBy("tester");
        return workflowRepository.save(w);
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

    /** Intra-state B2B sales invoice: 10000 taxable @18% + CGST/SGST 900 each. */
    private ParsedVoucher salesInvoice(UUID orgId, UUID jobId, String number, LocalDate date,
                                       String placeOfSupply) {
        ParsedVoucher v = new ParsedVoucher();
        v.setUploadJobId(jobId);
        v.setOrganizationId(orgId);
        v.setVoucherTypeName("Sales");
        v.setVoucherNumber(number);
        v.setVoucherDate(date);
        v.setPartyLedgerName("Sundry Debtors");
        v.setPartyGstin("29AABCU9603R1ZM");
        v.setPartyGstRegistrationType("Regular");
        v.setPlaceOfSupply(placeOfSupply);
        v.setTotalDebit(new BigDecimal("11800.00"));
        v.setTotalCredit(new BigDecimal("11800.00"));
        List<ParsedVoucherEntry> entries = new ArrayList<>();
        entries.add(entry(v, "Sales Account", "10000.00", true, "18", false));
        entries.add(entry(v, "Output CGST", "900.00", true, null, false));
        entries.add(entry(v, "Output SGST", "900.00", true, null, false));
        entries.add(entry(v, "Sundry Debtors", "-11800.00", false, null, true));
        v.setEntries(entries);
        return parsedVoucherRepository.save(v);
    }

    private void seedMasters(UUID orgId, UUID jobId) {
        UploadLedgerSnapshot salesLedger = new UploadLedgerSnapshot();
        salesLedger.setUploadJobId(jobId);
        salesLedger.setOrganizationId(orgId);
        salesLedger.setLedgerName("Sales Account");
        salesLedger.setHsnSacCode("997331");
        snapshotRepository.save(salesLedger);

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

    private Gstr1FillIn ledgerFillIn(UUID workflowId, String ledger, Gstr1FillInAttribute attr,
                                     String value) {
        Gstr1FillIn f = new Gstr1FillIn();
        f.setWorkflowId(workflowId);
        f.setScope(Gstr1FillInScope.LEDGER);
        f.setKey(ledger);
        f.setAttribute(attr);
        f.setValue(value);
        f.setEnteredBy("tester");
        f.setEnteredAt(Instant.now());
        return fillInRepository.save(f);
    }

    private Optional<PreparedReturnRow> rowOf(UUID preparedReturnId, String rowKey) {
        return rowRepository.findByPreparedReturnId(preparedReturnId).stream()
                .filter(r -> r.getRowKey().equals(rowKey))
                .findFirst();
    }

    // ---------- tests ----------

    @Test
    void buildsRowsFromCapturedVoucherDataIncludingViewAmounts() {
        Organization org = organization();
        Workflow w = gstr1Workflow(org.getId());
        UUID jobId = seedJob(org.getId());
        w.setUploadJobId(jobId);
        workflowRepository.save(w);
        salesInvoice(org.getId(), jobId, "INV-1", LocalDate.of(2025, 4, 10), "Karnataka");
        seedMasters(org.getId(), jobId);
        ledgerFillIn(w.getId(), "Sales Account", Gstr1FillInAttribute.HSN, "99733199");

        PreparedReturn pr = prepareService.buildOrRebuild(w);

        assertEquals("GSTR1", pr.getReturnKind());
        assertNotNull(pr.getBuiltAt());
        assertNull(pr.getFrozenAt());

        Optional<PreparedReturnRow> b2b = rowOf(pr.getId(), "b2b:" + voucherKey(jobId));
        assertTrue(b2b.isPresent(), "B2B row missing; rows=" + rowKeys(pr.getId()));
        Map<String, Object> payload = b2b.get().payload();
        assertEquals("29AABCU9603R1ZM", payload.get("gstin"));
        assertEquals("INV-1", payload.get("invoiceNo"));
        assertEquals("2025-04-10", payload.get("invoiceDate"));
        assertEquals("29", payload.get("pos"), "POS must be normalized to a state code");
        assertEquals(0, new BigDecimal("18").compareTo(num(payload, "rate")));
        assertEquals(0, new BigDecimal("10000").compareTo(num(payload, "taxableValue")));
        assertEquals(0, new BigDecimal("900").compareTo(num(payload, "cgst")));
        assertEquals(0, new BigDecimal("900").compareTo(num(payload, "sgst")));
        assertEquals(0, new BigDecimal("11800").compareTo(num(payload, "invoiceValue")));

        Optional<PreparedReturnRow> hsn = rowOf(pr.getId(),
                "hsn:99733199:NA:18.00:b2b");
        assertTrue(hsn.isPresent(), "HSN row missing; rows=" + rowKeys(pr.getId()));
        assertEquals("99733199", hsn.get().payload().get("hsn"),
                "LEDGER-scope fill-in HSN must override the snapshot HSN");
        assertEquals("B2B", hsn.get().payload().get("supplyType"));

        Optional<PreparedReturnRow> docs = rowRepository.findByPreparedReturnId(pr.getId()).stream()
                .filter(r -> r.getTableCode().equals("DOCS"))
                .findFirst();
        assertTrue(docs.isPresent(), "DOCS row missing");
    }

    @Test
    void rebuildTwiceGivesIdenticalRowsAndKeepsFillIns() {
        Organization org = organization();
        Workflow w = gstr1Workflow(org.getId());
        UUID jobId = seedJob(org.getId());
        w.setUploadJobId(jobId);
        workflowRepository.save(w);
        salesInvoice(org.getId(), jobId, "INV-1", LocalDate.of(2025, 4, 10), "Karnataka");
        seedMasters(org.getId(), jobId);
        ledgerFillIn(w.getId(), "Sales Account", Gstr1FillInAttribute.HSN, "99733199");

        PreparedReturn first = prepareService.buildOrRebuild(w);
        List<PreparedReturnRow> firstRows = rowRepository.findByPreparedReturnId(first.getId());
        long fillInsBefore = fillInRepository.count();

        PreparedReturn second = prepareService.buildOrRebuild(w);
        List<PreparedReturnRow> secondRows = rowRepository.findByPreparedReturnId(second.getId());

        assertEquals(first.getId(), second.getId());
        assertEquals(firstRows.size(), secondRows.size());
        for (int i = 0; i < firstRows.size(); i++) {
            assertEquals(firstRows.get(i).getTableCode(), secondRows.get(i).getTableCode());
            assertEquals(firstRows.get(i).getRowKey(), secondRows.get(i).getRowKey());
            assertEquals(firstRows.get(i).getSourceVoucherKey(), secondRows.get(i).getSourceVoucherKey());
            assertEquals(firstRows.get(i).getPayload(), secondRows.get(i).getPayload());
        }
        assertEquals(fillInsBefore, fillInRepository.count(),
                "rebuild must not touch Gstr1FillIn rows");
    }

    @Test
    void freezeMakesRowsReadOnlyAgainstLaterDayBookChanges() {
        Organization org = organization();
        Workflow w = gstr1Workflow(org.getId());
        UUID jobId = seedJob(org.getId());
        w.setUploadJobId(jobId);
        workflowRepository.save(w);
        salesInvoice(org.getId(), jobId, "INV-1", LocalDate.of(2025, 4, 10), "Karnataka");
        seedMasters(org.getId(), jobId);

        PreparedReturn pr = prepareService.buildOrRebuild(w);
        prepareService.freeze(w);
        pr = preparedReturnRepository.findByWorkflowIdAndReturnKind(w.getId(), "GSTR1").orElseThrow();
        assertNotNull(pr.getFrozenAt());

        int rowsBefore = rowRepository.findByPreparedReturnId(pr.getId()).size();
        List<UUID> rowIdsBefore = rowRepository.findByPreparedReturnId(pr.getId()).stream()
                .map(PreparedReturnRow::getId).toList();

        // a later day-book re-upload captures another voucher under the same job
        salesInvoice(org.getId(), jobId, "INV-2", LocalDate.of(2025, 4, 20), "Karnataka");

        PreparedReturn after = prepareService.buildOrRebuild(w);
        assertEquals(pr.getId(), after.getId());
        assertEquals(rowsBefore, rowRepository.findByPreparedReturnId(after.getId()).size());
        assertEquals(rowIdsBefore,
                rowRepository.findByPreparedReturnId(after.getId()).stream()
                        .map(PreparedReturnRow::getId).toList(),
                "frozen rows must be untouched by later day-book re-uploads");

        // freeze is idempotent
        Instant firstFrozenAt = pr.getFrozenAt();
        prepareService.freeze(w);
        assertEquals(firstFrozenAt, preparedReturnRepository
                .findByWorkflowIdAndReturnKind(w.getId(), "GSTR1").orElseThrow().getFrozenAt());
    }

    @Test
    void releaseClearsFrozenFlagAndRebuildPicksUpNewData() {
        Organization org = organization();
        Workflow w = gstr1Workflow(org.getId());
        UUID jobId = seedJob(org.getId());
        w.setUploadJobId(jobId);
        workflowRepository.save(w);
        salesInvoice(org.getId(), jobId, "INV-1", LocalDate.of(2025, 4, 10), "Karnataka");
        seedMasters(org.getId(), jobId);

        PreparedReturn pr = prepareService.buildOrRebuild(w);
        prepareService.freeze(w);
        int frozenRowCount = rowRepository.findByPreparedReturnId(pr.getId()).size();
        assertTrue(frozenRowCount > 0);

        prepareService.release(w);
        pr = preparedReturnRepository.findByWorkflowIdAndReturnKind(w.getId(), "GSTR1").orElseThrow();
        assertNull(pr.getFrozenAt(), "release must clear frozenAt");

        salesInvoice(org.getId(), jobId, "INV-2", LocalDate.of(2025, 4, 20), "Karnataka");
        PreparedReturn rebuilt = prepareService.buildOrRebuild(w);
        assertTrue(rowRepository.findByPreparedReturnId(rebuilt.getId()).size() > frozenRowCount,
                "rebuild after release must pick up newly captured vouchers");
    }

    @Test
    void rejectsNonGstr1Workflows() {
        Organization org = organization();
        Workflow w = workflowOfType(org.getId(), WorkflowType.TDS_PAYMENT);
        assertThrows(Exception.class, () -> prepareService.buildOrRebuild(w));
        assertThrows(Exception.class, () -> prepareService.freeze(w));
        assertThrows(Exception.class, () -> prepareService.release(w));
    }

    @Test
    void prepareEntryCarriesForwardLedgerFillInsFromSupersededWorkflow() {
        Organization org = organization();
        Workflow previous = gstr1Workflow(org.getId());
        ledgerFillIn(previous.getId(), "Sales Account", Gstr1FillInAttribute.HSN, "99733199");

        previous.setActive(false);
        workflowRepository.save(previous);

        Workflow next = gstr1Workflow(org.getId());
        next.setSupersedesWorkflowId(previous.getId());
        workflowRepository.save(next);

        prepareHook.onEnterPrepare(next);

        Optional<Gstr1FillIn> copied = fillInRepository
                .findByWorkflowIdAndScopeAndKeyAndAttribute(next.getId(),
                        Gstr1FillInScope.LEDGER, "Sales Account", Gstr1FillInAttribute.HSN);
        assertTrue(copied.isPresent(), "LEDGER-scope fill-ins must carry forward at Prepare entry");
        assertEquals("99733199", copied.get().getValue());
        assertEquals("CARRY-FORWARD", copied.get().getEnteredBy());
    }

    // ---------- helpers ----------

    @Autowired
    private com.arktech.superaccountant.daybook.repository.DayBookUploadJobRepository dayBookUploadJobRepository;

    private UUID seedJob(UUID orgId) {
        com.arktech.superaccountant.daybook.models.DayBookUploadJob job =
                new com.arktech.superaccountant.daybook.models.DayBookUploadJob();
        job.setOrganizationId(orgId);
        job.setFileName("daybook-" + (++counter) + ".json");
        job.setStatus(com.arktech.superaccountant.masters.models.UploadJobStatus.COMPLETED);
        job.setCoverageFrom(LocalDate.of(2025, 4, 1));
        job.setCoverageTo(LocalDate.of(2025, 4, 30));
        job.setUploadedBy("tester");
        return dayBookUploadJobRepository.save(job).getId();
    }

    private String voucherKey(UUID jobId) {
        List<ParsedVoucher> vouchers = parsedVoucherRepository.findByUploadJobId(jobId);
        return VoucherKeys.keyAll(vouchers).keySet().iterator().next();
    }

    private String rowKeys(UUID preparedReturnId) {
        return rowRepository.findByPreparedReturnId(preparedReturnId).stream()
                .map(PreparedReturnRow::getRowKey).toList().toString();
    }

    private static BigDecimal num(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value instanceof Number n) {
            return new BigDecimal(n.toString());
        }
        return (BigDecimal) value;
    }
}
