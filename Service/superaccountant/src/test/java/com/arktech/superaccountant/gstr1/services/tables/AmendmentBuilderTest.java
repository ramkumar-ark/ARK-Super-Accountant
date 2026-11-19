package com.arktech.superaccountant.gstr1.services.tables;

import com.arktech.superaccountant.daybook.repository.ParsedVoucherRepository;
import com.arktech.superaccountant.gstr1.models.Gstr1FillIn;
import com.arktech.superaccountant.gstr1.models.Gstr1FillInAttribute;
import com.arktech.superaccountant.gstr1.models.Gstr1FillInScope;
import com.arktech.superaccountant.gstr1.models.PreparedReturn;
import com.arktech.superaccountant.gstr1.models.PreparedReturnRow;
import com.arktech.superaccountant.gstr1.repository.Gstr1FillInRepository;
import com.arktech.superaccountant.gstr1.repository.PreparedReturnRepository;
import com.arktech.superaccountant.gstr1.repository.PreparedReturnRowRepository;
import com.arktech.superaccountant.gstr1.services.Gstr1FillInService;
import com.arktech.superaccountant.gstr1.services.VoucherView;
import com.arktech.superaccountant.gstr1.services.VoucherViewBuilder;
import com.arktech.superaccountant.masters.repository.OrganizationRepository;
import com.arktech.superaccountant.masters.repository.UploadLedgerSnapshotRepository;
import com.arktech.superaccountant.workflow.models.Workflow;
import com.arktech.superaccountant.workflow.models.WorkflowStage;
import com.arktech.superaccountant.workflow.models.WorkflowType;
import com.arktech.superaccountant.workflow.repository.WorkflowRepository;
import com.arktech.superaccountant.workflow.snapshot.AmendmentChangeKind;
import com.arktech.superaccountant.workflow.snapshot.AmendmentFollowUp;
import com.arktech.superaccountant.workflow.snapshot.AmendmentFollowUpRepository;
import com.arktech.superaccountant.workflow.snapshot.AmendmentStatus;
import com.arktech.superaccountant.gst.mapping.TaxLedgerMappingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AmendmentBuilderTest extends TableTestSupport {

    private static final UUID ORG_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID APRIL_WORKFLOW_ID = UUID.fromString(
            "aaaaaaaa-1111-1111-1111-111111111111");

    private AmendmentFollowUpRepository followUpRepository;
    private PreparedReturnRepository preparedReturnRepository;
    private PreparedReturnRowRepository rowRepository;
    private Gstr1FillInRepository fillInRepository;
    private VoucherViewBuilder viewBuilder;
    private WorkflowRepository workflowRepository;
    private AmendmentBuilder builder;

    @BeforeEach
    void setUp() {
        followUpRepository = mock(AmendmentFollowUpRepository.class);
        preparedReturnRepository = mock(PreparedReturnRepository.class);
        rowRepository = mock(PreparedReturnRowRepository.class);
        fillInRepository = mock(Gstr1FillInRepository.class);
        viewBuilder = mock(VoucherViewBuilder.class);
        workflowRepository = mock(WorkflowRepository.class);
        Workflow aprilWorkflow = new Workflow();
        aprilWorkflow.setId(APRIL_WORKFLOW_ID);
        aprilWorkflow.setPeriodStart(LocalDate.of(2025, 4, 1));
        when(workflowRepository.findById(APRIL_WORKFLOW_ID)).thenReturn(Optional.of(aprilWorkflow));
        ParsedVoucherRepository parsedVoucherRepository = mock(ParsedVoucherRepository.class);
        UploadLedgerSnapshotRepository snapshotRepository = mock(UploadLedgerSnapshotRepository.class);
        TaxLedgerMappingRepository taxLedgerMappingRepository = mock(TaxLedgerMappingRepository.class);
        OrganizationRepository organizationRepository = mock(OrganizationRepository.class);
        Gstr1FillInService fillInService = mock(Gstr1FillInService.class);
        when(organizationRepository.findById(any())).thenReturn(Optional.empty());
        when(parsedVoucherRepository.findByUploadJobId(any())).thenReturn(List.of());
        when(fillInService.resolve(any(), any(), any(), any())).thenReturn(Optional.empty());

        builder = new AmendmentBuilder(followUpRepository, preparedReturnRepository, rowRepository,
                fillInRepository, workflowRepository, viewBuilder, fillInService,
                new B2bBuilder(), new B2clBuilder(), new B2csBuilder(), new ExportBuilder(),
                new CdnBuilder(), new AdvanceBuilder());
    }

    /** S2: a changed invoice → B2BA row whose oinum/oidt come from the April frozen return row, not the day book. */
    @Test
    void changedB2bFollowUpProducesB2baRowWithOriginalNumberAndDateFromFrozenReturn() {
        Workflow current = mayWorkflow();
        AmendmentFollowUp followUp = followUp(AmendmentChangeKind.CHANGED, "b2b:G:v1");
        when(followUpRepository.findByOrganizationIdAndStatus(ORG_ID, AmendmentStatus.OPEN))
                .thenReturn(List.of(followUp));
        seedOriginalRow(originalPayload("INV-001", "2025-04-15"));
        VoucherView revised = voucher()
                .voucherKey("G:v1")
                .voucherTypeName("Sales")
                .partyGstin("29AABCU9603R1ZM")
                .voucherNumber("INV-001-REV")
                .voucherDate(LocalDate.of(2025, 4, 20))
                .totalValue(v("11800"))
                .taxableValue(v("10000"))
                .rate(v("18"))
                .cgst(v("900"))
                .sgst(v("900"))
                .build();
        when(viewBuilder.buildAll(current)).thenReturn(List.of(revised));

        List<PreparedReturnRow> rows = builder.build(current, List.of());

        assertEquals(1, rows.size());
        PreparedReturnRow row = rows.get(0);
        assertEquals("B2BA", row.getTableCode());
        assertTrue(row.isAmendment());
        assertEquals("2025-04", row.getOriginalPeriod());
        assertEquals("b2b:G:v1", row.getOriginalDocKey());
        assertEquals(followUp.getId(), row.getFollowUpId());
        Map<String, Object> payload = row.payload();
        assertEquals("INV-001", payload.get("oinum"), "original number must come from the frozen April row");
        assertEquals("2025-04-15", payload.get("oidt"));
        assertEquals("INV-001-REV", payload.get("invoiceNo"), "revised number comes from the current books");
        assertEquals("29AABCU9603R1ZM", payload.get("gstin"));
        assertFalse(row.isLateReporting());
    }

    /** S1: a late-reported voucher → a fresh normal-table row flagged late. */
    @Test
    void lateFollowUpProducesFreshNormalRowFlaggedLate() {
        Workflow current = mayWorkflow();
        AmendmentFollowUp followUp = followUp(AmendmentChangeKind.LATE, null);
        followUp.setVoucherKey("G:v2");
        when(followUpRepository.findByOrganizationIdAndStatus(ORG_ID, AmendmentStatus.OPEN))
                .thenReturn(List.of(followUp));
        VoucherView late = voucher()
                .voucherKey("G:v2")
                .voucherTypeName("Sales")
                .partyGstin("29AABCU9603R1ZM")
                .voucherNumber("INV-LATE")
                .voucherDate(LocalDate.of(2025, 4, 30))
                .totalValue(v("11800"))
                .taxableValue(v("10000"))
                .rate(v("18"))
                .cgst(v("900"))
                .sgst(v("900"))
                .build();
        when(viewBuilder.buildAll(current)).thenReturn(List.of(late));

        List<PreparedReturnRow> rows = builder.build(current, List.of());

        assertEquals(1, rows.size());
        PreparedReturnRow row = rows.get(0);
        assertEquals("B2B", row.getTableCode(), "liability lands in the normal table");
        assertFalse(row.isAmendment());
        assertTrue(row.isLateReporting());
        assertEquals(followUp.getId(), row.getFollowUpId());
        assertEquals("G:v2", row.getSourceVoucherKey());
        assertEquals("INV-LATE", row.payload().get("invoiceNo"));
    }

    /** S3 with ack: a deleted doc → amendment row to zero. */
    @Test
    void deletedFollowUpWithAcknowledgementProducesZeroedAmendmentRow() {
        Workflow current = mayWorkflow();
        AmendmentFollowUp followUp = followUp(AmendmentChangeKind.DELETED, "b2b:G:v1");
        when(followUpRepository.findByOrganizationIdAndStatus(ORG_ID, AmendmentStatus.OPEN))
                .thenReturn(List.of(followUp));
        seedOriginalRow(originalPayload("INV-001", "2025-04-15"));
        seedAck(current.getId(), "G:v1");
        when(viewBuilder.buildAll(current)).thenReturn(List.of());

        List<PreparedReturnRow> rows = builder.build(current, List.of());

        assertEquals(1, rows.size());
        PreparedReturnRow row = rows.get(0);
        assertEquals("B2BA", row.getTableCode());
        assertTrue(row.isAmendment());
        assertFalse(row.isLateReporting());
        assertEquals(followUp.getId(), row.getFollowUpId());
        Map<String, Object> payload = row.payload();
        assertEquals("INV-001", payload.get("oinum"));
        assertEquals(0, new BigDecimal("0").compareTo(new BigDecimal(payload.get("taxableValue").toString())));
        assertEquals(0, new BigDecimal("0").compareTo(new BigDecimal(payload.get("invoiceValue").toString())));
        assertEquals(0, new BigDecimal("0").compareTo(new BigDecimal(payload.get("cgst").toString())));
    }

    /** S3 without ack: no row — the finalize gate must block instead. */
    @Test
    void deletedFollowUpWithoutAcknowledgementProducesNoRow() {
        Workflow current = mayWorkflow();
        AmendmentFollowUp followUp = followUp(AmendmentChangeKind.DELETED, "b2b:G:v1");
        when(followUpRepository.findByOrganizationIdAndStatus(ORG_ID, AmendmentStatus.OPEN))
                .thenReturn(List.of(followUp));
        seedOriginalRow(originalPayload("INV-001", "2025-04-15"));
        when(viewBuilder.buildAll(current)).thenReturn(List.of());

        assertTrue(builder.build(current, List.of()).isEmpty());
    }

    /** A follow-up attached to the current workflow is skipped: its return rebuilds normally. */
    @Test
    void followUpForCurrentWorkflowIsSkipped() {
        Workflow current = mayWorkflow();
        AmendmentFollowUp followUp = followUp(AmendmentChangeKind.CHANGED, "b2b:G:v1");
        followUp.setWorkflowId(current.getId());
        when(followUpRepository.findByOrganizationIdAndStatus(ORG_ID, AmendmentStatus.OPEN))
                .thenReturn(List.of(followUp));

        assertTrue(builder.build(current, List.of()).isEmpty());
    }

    private Workflow mayWorkflow() {        Workflow w = new Workflow();
        w.setId(UUID.fromString("33333333-4444-4444-4444-444444444444"));
        w.setOrganizationId(ORG_ID);
        w.setType(WorkflowType.GSTR1);
        w.setPeriodStart(LocalDate.of(2025, 5, 1));
        w.setPeriodEnd(LocalDate.of(2025, 5, 31));
        w.setPeriodKey("2025-05");
        w.setFinancialYear(2025);
        w.setStage(WorkflowStage.PREPARE);
        return w;
    }

    private AmendmentFollowUp followUp(AmendmentChangeKind change, String originalDocKey) {
        AmendmentFollowUp f = new AmendmentFollowUp();
        f.setId(UUID.randomUUID());
        f.setOrganizationId(ORG_ID);
        f.setWorkflowId(APRIL_WORKFLOW_ID);
        f.setVoucherKey("G:v1");
        f.setStatus(AmendmentStatus.OPEN);
        f.setChange(change);
        f.setTableCode(originalDocKey == null ? null : "B2B");
        f.setOriginalWorkflowId(originalDocKey == null ? null : APRIL_WORKFLOW_ID);
        f.setOriginalDocKey(originalDocKey);
        f.setCreatedAt(Instant.now());
        return f;
    }

    private Map<String, Object> originalPayload(String invoiceNo, String invoiceDate) {
        Map<String, Object> p = new HashMap<>();
        p.put("gstin", "29AABCU9603R1ZM");
        p.put("invoiceNo", invoiceNo);
        p.put("invoiceDate", invoiceDate);
        p.put("pos", "29");
        p.put("rate", new BigDecimal("18"));
        p.put("taxableValue", new BigDecimal("10000"));
        p.put("igst", BigDecimal.ZERO);
        p.put("cgst", new BigDecimal("900"));
        p.put("sgst", new BigDecimal("900"));
        p.put("cess", BigDecimal.ZERO);
        p.put("invoiceValue", new BigDecimal("11800"));
        return p;
    }

    private void seedOriginalRow(Map<String, Object> payload) {
        PreparedReturn prepared = new PreparedReturn();
        prepared.setId(UUID.randomUUID());
        prepared.setWorkflowId(APRIL_WORKFLOW_ID);
        prepared.setReturnKind("GSTR1");
        when(preparedReturnRepository.findByWorkflowIdAndReturnKind(
                APRIL_WORKFLOW_ID, "GSTR1")).thenReturn(Optional.of(prepared));
        PreparedReturnRow original = new PreparedReturnRow();
        original.setId(UUID.randomUUID());
        original.setPreparedReturnId(prepared.getId());
        original.setTableCode("B2B");
        original.setRowKey("b2b:G:v1");
        original.setSourceVoucherKey("G:v1");
        original.setPayload(toJson(payload));
        original.setAmendment(false);
        when(rowRepository.findByPreparedReturnId(prepared.getId())).thenReturn(List.of(original));
    }

    private void seedAck(UUID workflowId, String voucherKey) {
        Gstr1FillIn ack = new Gstr1FillIn();
        ack.setWorkflowId(workflowId);
        ack.setScope(Gstr1FillInScope.VOUCHER);
        ack.setKey(voucherKey);
        ack.setAttribute(Gstr1FillInAttribute.AMENDMENT_ZERO_ACK);
        when(fillInRepository.findByWorkflowIdAndScopeAndKeyAndAttribute(
                eq(workflowId), eq(Gstr1FillInScope.VOUCHER), eq(voucherKey),
                eq(Gstr1FillInAttribute.AMENDMENT_ZERO_ACK)))
                .thenReturn(Optional.of(ack));
    }

    private static String toJson(Map<String, Object> payload) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(payload);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
