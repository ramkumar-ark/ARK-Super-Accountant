package com.arktech.superaccountant.gstr1.services;

import com.arktech.superaccountant.daybook.models.ParsedVoucher;
import com.arktech.superaccountant.daybook.models.ParsedVoucherEntry;
import com.arktech.superaccountant.daybook.models.ParsedVoucherItem;
import com.arktech.superaccountant.daybook.repository.ParsedVoucherRepository;
import com.arktech.superaccountant.gst.mapping.GstHead;
import com.arktech.superaccountant.gst.mapping.TaxLedgerMapping;
import com.arktech.superaccountant.gst.mapping.TaxLedgerMappingRepository;
import com.arktech.superaccountant.gst.mapping.TaxLedgerRole;
import com.arktech.superaccountant.masters.models.Organization;
import com.arktech.superaccountant.masters.models.UploadLedgerSnapshot;
import com.arktech.superaccountant.masters.repository.OrganizationRepository;
import com.arktech.superaccountant.masters.repository.UploadLedgerSnapshotRepository;
import com.arktech.superaccountant.workflow.models.Workflow;
import com.arktech.superaccountant.workflow.models.WorkflowStage;
import com.arktech.superaccountant.workflow.models.WorkflowType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Real captured entities (ParsedVoucher + entries, UploadLedgerSnapshot,
 * TaxLedgerMapping) drive the view construction — the T8 review critical item.
 */
class VoucherViewBuilderTest {

    private ParsedVoucherRepository parsedVoucherRepository;
    private UploadLedgerSnapshotRepository snapshotRepository;
    private TaxLedgerMappingRepository taxLedgerMappingRepository;
    private OrganizationRepository organizationRepository;
    private Gstr1FillInService fillInService;
    private VoucherViewBuilder builder;

    private final UUID orgId = UUID.randomUUID();
    private final UUID jobId = UUID.randomUUID();

    private Organization organization;
    private Map<String, UploadLedgerSnapshot> snapshots;
    private Map<String, TaxLedgerMapping> taxLedgers;

    @BeforeEach
    void setUp() {
        parsedVoucherRepository = mock(ParsedVoucherRepository.class);
        snapshotRepository = mock(UploadLedgerSnapshotRepository.class);
        taxLedgerMappingRepository = mock(TaxLedgerMappingRepository.class);
        organizationRepository = mock(OrganizationRepository.class);
        fillInService = mock(Gstr1FillInService.class);
        builder = new VoucherViewBuilder(parsedVoucherRepository, snapshotRepository,
                taxLedgerMappingRepository, organizationRepository, fillInService);

        organization = new Organization("Test Org");
        organization.setGstin("29ABCDE1234F1Z5");
        lenient().when(organizationRepository.findById(orgId)).thenReturn(Optional.of(organization));

        snapshots = Map.of(
                "sales account", snapshot("Sales Account", "997331"));
        taxLedgers = Map.of(
                "output cgst", mapping("Output CGST", GstHead.CGST),
                "output sgst", mapping("Output SGST", GstHead.SGST),
                "output igst", mapping("Output IGST", GstHead.IGST),
                "output cess", mapping("Output Cess", GstHead.CESS));
        lenient().when(snapshotRepository.findByUploadJobId(any()))
                .thenReturn(List.copyOf(snapshots.values()));
        lenient().when(taxLedgerMappingRepository.findByOrganizationId(any()))
                .thenReturn(List.copyOf(taxLedgers.values()));
        lenient().when(fillInService.resolve(any(), anyString(), any(), any()))
                .thenReturn(java.util.Optional.empty());
    }

    private UploadLedgerSnapshot snapshot(String ledgerName, String hsn) {
        UploadLedgerSnapshot s = new UploadLedgerSnapshot();
        s.setUploadJobId(jobId);
        s.setOrganizationId(orgId);
        s.setLedgerName(ledgerName);
        s.setHsnSacCode(hsn);
        return s;
    }

    private TaxLedgerMapping mapping(String ledgerName, GstHead head) {
        TaxLedgerMapping m = new TaxLedgerMapping();
        m.setOrganizationId(orgId);
        m.setLedgerName(ledgerName);
        m.setLedgerKey(ledgerName.trim().toLowerCase());
        m.setRole(TaxLedgerRole.OUTPUT);
        m.setHead(head);
        m.setSavedBy("test");
        m.setSavedAt(Instant.now());
        return m;
    }

    private Workflow workflow() {
        Workflow w = new Workflow();
        w.setId(UUID.randomUUID());
        w.setOrganizationId(orgId);
        w.setType(WorkflowType.GSTR1);
        w.setPeriodStart(LocalDate.of(2025, 4, 1));
        w.setPeriodEnd(LocalDate.of(2025, 4, 30));
        w.setPeriodKey("2025-04");
        w.setFinancialYear(2025);
        w.setStage(WorkflowStage.PREPARE);
        w.setUploadJobId(jobId);
        return w;
    }

    private ParsedVoucher voucher(LocalDate date, String pos, ParsedVoucherEntry... entries) {
        ParsedVoucher v = new ParsedVoucher();
        v.setUploadJobId(jobId);
        v.setOrganizationId(orgId);
        v.setVoucherTypeName("Sales");
        v.setVoucherNumber("INV-1");
        v.setVoucherDate(date);
        v.setPartyGstin("29AABCU9603R1ZM");
        v.setPartyGstRegistrationType("Regular");
        v.setPlaceOfSupply(pos);
        v.setEntries(new ArrayList<>(List.of(entries)));
        BigDecimal debit = v.getEntries().stream()
                .map(ParsedVoucherEntry::getAmount)
                .filter(a -> a != null && a.compareTo(BigDecimal.ZERO) > 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        v.setTotalDebit(debit);
        return v;
    }

    private ParsedVoucherEntry taxable(String ledger, String amount, String rate) {
        ParsedVoucherEntry e = new ParsedVoucherEntry();
        e.setLedgerName(ledger);
        e.setAmount(new BigDecimal(amount));
        e.setDebit(true);
        e.setGstRate(new BigDecimal(rate));
        e.setGstTaxability("Taxable");
        return e;
    }

    private ParsedVoucherEntry tax(String ledger, String amount) {
        ParsedVoucherEntry e = new ParsedVoucherEntry();
        e.setLedgerName(ledger);
        e.setAmount(new BigDecimal(amount));
        e.setDebit(true);
        return e;
    }

    @Test
    void populatesAmountsFromCapturedEntriesAndMappedTaxLedgers() {
        ParsedVoucher v = voucher(LocalDate.of(2025, 4, 10), "Karnataka",
                taxable("Sales Account", "10000.00", "18"),
                tax("Output CGST", "900.00"),
                tax("Output SGST", "900.00"),
                tax("Sundry Debtors", "-11800.00"));
        when(parsedVoucherRepository.findByUploadJobId(jobId)).thenReturn(List.of(v));

        List<VoucherView> views = builder.build(workflow());
        assertEquals(1, views.size());
        VoucherView view = views.get(0);
        assertEquals(0, new BigDecimal("10000").compareTo(view.getTaxableValue()));
        assertEquals(0, new BigDecimal("18").compareTo(view.getRate()));
        assertEquals(0, new BigDecimal("900").compareTo(view.getCgst()));
        assertEquals(0, new BigDecimal("900").compareTo(view.getSgst()));
        assertEquals(0, BigDecimal.ZERO.compareTo(view.getIgst()));
        assertEquals(0, BigDecimal.ZERO.compareTo(view.getCess()));
        assertEquals(0, new BigDecimal("11800").compareTo(view.getTotalValue()));
        assertEquals("INV-1", view.getVoucherNumber());
        assertEquals(LocalDate.of(2025, 4, 10), view.getVoucherDate());
        assertEquals("29", view.getSupplierStateCode());
        assertEquals("29", view.placeOfSupplyCode());
        assertEquals("997331", view.getHsn());
    }

    @Test
    void interStateTaxLandsOnIgst() {
        ParsedVoucher v = voucher(LocalDate.of(2025, 4, 10), "Delhi",
                taxable("Sales Account", "10000.00", "18"),
                tax("Output IGST", "1800.00"));
        when(parsedVoucherRepository.findByUploadJobId(jobId)).thenReturn(List.of(v));

        VoucherView view = builder.build(workflow()).get(0);
        assertEquals(0, new BigDecimal("1800").compareTo(view.getIgst()));
        assertEquals(0, BigDecimal.ZERO.compareTo(view.getCgst()));
        assertEquals(0, BigDecimal.ZERO.compareTo(view.getSgst()));
    }

    @Test
    void ledgerFillInOverridesSnapshotHsn() {
        ParsedVoucher v = voucher(LocalDate.of(2025, 4, 10), "Karnataka",
                taxable("Sales Account", "10000.00", "18"));
        when(parsedVoucherRepository.findByUploadJobId(jobId)).thenReturn(List.of(v));
        when(fillInService.resolve(any(), anyString(), eq("Sales Account"),
                eq(com.arktech.superaccountant.gstr1.models.Gstr1FillInAttribute.HSN)))
                .thenReturn(Optional.of("99733199"));

        VoucherView view = builder.build(workflow()).get(0);
        assertEquals("99733199", view.getHsn());
    }

    @Test
    void uqcAndQuantityComeFromFillIns() {
        ParsedVoucher v = voucher(LocalDate.of(2025, 4, 10), "Karnataka",
                taxable("Sales Account", "10000.00", "18"));
        when(parsedVoucherRepository.findByUploadJobId(jobId)).thenReturn(List.of(v));
        when(fillInService.resolve(any(), anyString(), eq("Sales Account"),
                eq(com.arktech.superaccountant.gstr1.models.Gstr1FillInAttribute.UQC)))
                .thenReturn(Optional.of("NOS"));
        when(fillInService.resolve(any(), anyString(), eq("Sales Account"),
                eq(com.arktech.superaccountant.gstr1.models.Gstr1FillInAttribute.QTY)))
                .thenReturn(Optional.of("25"));

        VoucherView view = builder.build(workflow()).get(0);
        assertEquals("NOS", view.getUqc());
        assertEquals(0, new BigDecimal("25").compareTo(view.getQuantity()));
    }

    @Test
    void posFillInOverridesCapturedPlaceOfSupply() {
        ParsedVoucher v = voucher(LocalDate.of(2025, 4, 10), "Karnataka",
                taxable("Sales Account", "10000.00", "18"));
        when(parsedVoucherRepository.findByUploadJobId(jobId)).thenReturn(List.of(v));
        when(fillInService.resolve(any(), anyString(), any(),
                eq(com.arktech.superaccountant.gstr1.models.Gstr1FillInAttribute.POS)))
                .thenReturn(Optional.of("Delhi"));

        VoucherView view = builder.build(workflow()).get(0);
        assertEquals("Delhi", view.getPlaceOfSupply());
        assertEquals("07", view.placeOfSupplyCode());
    }

    @Test
    void vouchersOutsideThePeriodAreExcluded() {
        ParsedVoucher inPeriod = voucher(LocalDate.of(2025, 4, 10), "Karnataka",
                taxable("Sales Account", "100.00", "18"));
        ParsedVoucher before = voucher(LocalDate.of(2025, 3, 31), "Karnataka",
                taxable("Sales Account", "100.00", "18"));
        ParsedVoucher after = voucher(LocalDate.of(2025, 5, 1), "Karnataka",
                taxable("Sales Account", "100.00", "18"));
        ParsedVoucher undated = voucher(null, "Karnataka", taxable("Sales Account", "100.00", "18"));
        when(parsedVoucherRepository.findByUploadJobId(jobId))
                .thenReturn(List.of(inPeriod, before, after, undated));

        List<VoucherView> views = builder.build(workflow());
        assertEquals(List.of("INV-1"), views.stream().map(VoucherView::getVoucherNumber)
                .collect(Collectors.toList()));
    }

    @Test
    void noRatedEntriesFallsBackToInvoiceValueMinusTaxHeads() {
        ParsedVoucher v = voucher(LocalDate.of(2025, 4, 10), "Karnataka",
                tax("Output CGST", "450.00"),
                tax("Output SGST", "450.00"),
                tax("Sundry Debtors", "-10900.00"));
        v.setTotalDebit(new BigDecimal("10900.00"));
        v.setTotalCredit(new BigDecimal("10900.00"));
        when(parsedVoucherRepository.findByUploadJobId(jobId)).thenReturn(List.of(v));

        VoucherView view = builder.build(workflow()).get(0);
        assertEquals(0, new BigDecimal("10000").compareTo(view.getTaxableValue()));
        assertNull(view.getRate());
    }

    @Test
    void workflowWithoutUploadJobYieldsNoViews() {
        Workflow w = workflow();
        w.setUploadJobId(null);
        assertTrue(builder.build(w).isEmpty());
    }

    @Test
    void uqcQuantityAndHsnFallBackToCapturedItemsWhenFillInsAbsent() {
        ParsedVoucher v = voucher(LocalDate.of(2025, 4, 10), "Karnataka",
                taxable("Goods Sold", "2000.00", "18"));
        ParsedVoucherItem first = item("Plywood 8mm", "4407", "NOS", "10");
        ParsedVoucherItem second = item("Teak Board", "4407", "NOS", "5");
        v.setItems(new ArrayList<>(List.of(first, second)));
        when(parsedVoucherRepository.findByUploadJobId(jobId)).thenReturn(List.of(v));

        VoucherView view = builder.build(workflow()).get(0);
        assertEquals("NOS", view.getUqc());
        assertEquals(0, new BigDecimal("15").compareTo(view.getQuantity()));
        assertEquals("4407", view.getHsn());
    }

    @Test
    void fillInStillBeatsCapturedItemsForUqcQuantityAndHsn() {
        ParsedVoucher v = voucher(LocalDate.of(2025, 4, 10), "Karnataka",
                taxable("Sales Account", "2000.00", "18"));
        v.setItems(new ArrayList<>(List.of(item("Plywood 8mm", "4407", "NOS", "10"))));
        when(parsedVoucherRepository.findByUploadJobId(jobId)).thenReturn(List.of(v));
        when(fillInService.resolve(any(), anyString(), eq("Sales Account"),
                eq(com.arktech.superaccountant.gstr1.models.Gstr1FillInAttribute.UQC)))
                .thenReturn(Optional.of("PCS"));
        when(fillInService.resolve(any(), anyString(), eq("Sales Account"),
                eq(com.arktech.superaccountant.gstr1.models.Gstr1FillInAttribute.QTY)))
                .thenReturn(Optional.of("3"));
        when(fillInService.resolve(any(), anyString(), eq("Sales Account"),
                eq(com.arktech.superaccountant.gstr1.models.Gstr1FillInAttribute.HSN)))
                .thenReturn(Optional.of("9999"));

        VoucherView view = builder.build(workflow()).get(0);
        assertEquals("PCS", view.getUqc());
        assertEquals(0, new BigDecimal("3").compareTo(view.getQuantity()));
        assertEquals("9999", view.getHsn());
    }

    private ParsedVoucherItem item(String stockItemName, String hsn, String uqc, String quantity) {
        ParsedVoucherItem item = new ParsedVoucherItem();
        item.setOrganizationId(orgId);
        item.setStockItemName(stockItemName);
        item.setHsn(hsn);
        item.setUqc(uqc);
        item.setQuantity(new BigDecimal(quantity));
        return item;
    }
}
