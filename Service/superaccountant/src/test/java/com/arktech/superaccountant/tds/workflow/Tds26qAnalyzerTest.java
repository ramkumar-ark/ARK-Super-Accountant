package com.arktech.superaccountant.tds.workflow;

import com.arktech.superaccountant.daybook.models.ParsedVoucher;
import com.arktech.superaccountant.review.FindingLifecycleService;
import com.arktech.superaccountant.tds.engine.TdsAnalysisInput;
import com.arktech.superaccountant.tds.engine.TdsEngine;
import com.arktech.superaccountant.tds.engine.TdsAnalysisResult;
import com.arktech.superaccountant.tds.engine.TdsEngineFixtures;
import com.arktech.superaccountant.tds.models.TdsDeducteeEntry;
import com.arktech.superaccountant.tds.models.TdsSectionConfig;
import com.arktech.superaccountant.tds.repository.TdsDeducteeEntryRepository;
import com.arktech.superaccountant.tds.repository.TdsFindingRepository;
import com.arktech.superaccountant.tds.services.TdsWorkflowDetailService;
import com.arktech.superaccountant.workflow.analysis.AnalysisContext;
import com.arktech.superaccountant.workflow.models.Workflow;
import com.arktech.superaccountant.workflow.models.WorkflowType;
import com.arktech.superaccountant.workflow.repository.WorkflowRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class Tds26qAnalyzerTest {

    private static final LocalDate Q1_START = LocalDate.of(2026, 4, 1);
    private static final LocalDate Q1_END = LocalDate.of(2026, 6, 30);

    private final TdsEngine engine = new TdsEngine();
    private TdsAnalysisInputFactory factory;
    private TdsDeducteeEntryRepository entryRepository;
    private TdsWorkflowDetailService detailService;
    private WorkflowRepository workflowRepository;
    private TdsFindingRepository findingRepository;
    private Tds26qAnalyzer analyzer;

    private final AnalysisContext ctx = new AnalysisContext(TdsEngineFixtures.WF,
            TdsEngineFixtures.ORG, WorkflowType.TDS_26Q, Q1_START, Q1_END,
            TdsEngineFixtures.JOB, UUID.randomUUID(), UUID.randomUUID());

    private final Workflow workflow = new Workflow();

    @BeforeEach
    void setUp() {
        factory = mock(TdsAnalysisInputFactory.class);
        entryRepository = mock(TdsDeducteeEntryRepository.class);
        detailService = mock(TdsWorkflowDetailService.class);
        workflowRepository = mock(WorkflowRepository.class);
        findingRepository = mock(TdsFindingRepository.class);
        analyzer = new Tds26qAnalyzer(factory, engine, entryRepository, detailService,
                workflowRepository);
        workflow.setId(TdsEngineFixtures.WF);
        workflow.setOrganizationId(TdsEngineFixtures.ORG);
        workflow.setType(WorkflowType.TDS_26Q);
        workflow.setPeriodStart(Q1_START);
        workflow.setPeriodEnd(Q1_END);
        when(workflowRepository.findById(TdsEngineFixtures.WF))
                .thenReturn(Optional.of(workflow));
        when(detailService.ensure26qDetail(workflow)).thenReturn(null);
    }

    private ParsedVoucher paymentVoucher(String no, LocalDate date) {
        return TdsEngineFixtures.voucher(no, date, "ABC Contractors", new Object[][]{
                {"Subcontractor Charges", "100000"}, {"ABC Contractors", "-99000"},
                {"TDS Payable 194C", "-1000"}});
    }

    private TdsAnalysisInput input(List<ParsedVoucher> vouchers) {
        return TdsEngineFixtures.input(vouchers,
                TdsEngineFixtures.stdSnaps("ABCPE1234F"), TdsEngineFixtures.stdMappings(),
                Q1_START, Q1_END);
    }

    @Test
    void analyze_persistsInQuarterEntriesOnly() {
        when(factory.build(ctx)).thenReturn(input(List.of(
                paymentVoucher("V0", LocalDate.of(2026, 3, 20)),
                paymentVoucher("V1", LocalDate.of(2026, 5, 10)),
                paymentVoucher("V2", LocalDate.of(2026, 7, 10)))));
        when(entryRepository.findByWorkflowIdAndSupersededAtIsNull(TdsEngineFixtures.WF))
                .thenReturn(List.of());

        analyzer.analyze(ctx);

        ArgumentCaptor<List<TdsDeducteeEntry>> captor = ArgumentCaptor.forClass(List.class);
        verify(entryRepository).saveAll(captor.capture());
        List<TdsDeducteeEntry> saved = captor.getValue();
        assertEquals(1, saved.size());
        assertEquals(LocalDate.of(2026, 5, 10), saved.get(0).getDateOfDeduction());
        assertNull(saved.get(0).getSupersededAt());
        verify(findingRepository, never()).saveAll(any());
        verify(findingRepository, never()).save(any());
    }

    @Test
    void reanalysis_preservesChallanMapping() {
        TdsAnalysisInput in = input(List.of(paymentVoucher("V1", LocalDate.of(2026, 5, 10))));
        when(factory.build(ctx)).thenReturn(in);
        TdsAnalysisResult probe = engine.analyze(in);
        TdsDeducteeEntry template = probe.entries().get(0);
        TdsDeducteeEntry prev = new TdsDeducteeEntry();
        prev.setId(UUID.randomUUID());
        prev.setWorkflowId(template.getWorkflowId());
        prev.setDeducteeName(template.getDeducteeName());
        prev.setSection(template.getSection());
        prev.setAmountPaid(template.getAmountPaid());
        prev.setTdsDeducted(template.getTdsDeducted());
        prev.setTdsDeposited(template.getTdsDeposited());
        prev.setDateOfPayment(template.getDateOfPayment());
        prev.setDateOfDeduction(template.getDateOfDeduction());
        prev.setSourceVoucherIds(template.getSourceVoucherIds());
        prev.setTdsChallanId(UUID.fromString("00000000-0000-0000-0000-0000000000c1"));
        when(entryRepository.findByWorkflowIdAndSupersededAtIsNull(TdsEngineFixtures.WF))
                .thenReturn(List.of(prev));

        analyzer.analyze(ctx);

        ArgumentCaptor<List<TdsDeducteeEntry>> captor = ArgumentCaptor.forClass(List.class);
        verify(entryRepository, org.mockito.Mockito.times(2)).saveAll(captor.capture());
        assertEquals(prev, captor.getAllValues().get(0).get(0));
        assertNotNull(captor.getAllValues().get(0).get(0).getSupersededAt());
        TdsDeducteeEntry fresh = captor.getAllValues().get(1).get(0);
        assertEquals(prev.getTdsChallanId(), fresh.getTdsChallanId());
    }

    @Test
    void analyze_appliesPanOverride() {
        when(factory.build(ctx)).thenReturn(input(List.of(
                paymentVoucher("V1", LocalDate.of(2026, 5, 10)))));
        when(entryRepository.findByWorkflowIdAndSupersededAtIsNull(TdsEngineFixtures.WF))
                .thenReturn(List.of());

        analyzer.analyze(ctx);

        ArgumentCaptor<List<TdsDeducteeEntry>> captor = ArgumentCaptor.forClass(List.class);
        verify(entryRepository).saveAll(captor.capture());
        TdsDeducteeEntry e = captor.getValue().get(0);
        assertTrue(e.isPanValid());
        assertFalse(e.isHigherRateApplied());
        assertEquals("ABCPE1234F", e.getDeducteePan());
    }
}
