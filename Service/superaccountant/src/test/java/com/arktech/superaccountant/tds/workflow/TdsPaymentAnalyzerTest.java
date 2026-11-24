package com.arktech.superaccountant.tds.workflow;

import com.arktech.superaccountant.daybook.models.ParsedVoucher;
import com.arktech.superaccountant.masters.models.ResolveStatus;
import com.arktech.superaccountant.review.FindingLifecycleService;
import com.arktech.superaccountant.tds.engine.TdsAnalysisInput;
import com.arktech.superaccountant.tds.engine.TdsAnalysisResult;
import com.arktech.superaccountant.tds.engine.TdsEngine;
import com.arktech.superaccountant.tds.engine.TdsEngineFixtures;
import com.arktech.superaccountant.tds.models.TdsFinding;
import com.arktech.superaccountant.tds.models.TdsFindingType;
import com.arktech.superaccountant.tds.models.TdsPaymentDetail;
import com.arktech.superaccountant.tds.repository.TdsFindingRepository;
import com.arktech.superaccountant.tds.repository.TdsPaymentDetailRepository;
import com.arktech.superaccountant.tds.services.TdsWorkflowDetailService;
import com.arktech.superaccountant.workflow.analysis.AnalysisContext;
import com.arktech.superaccountant.workflow.models.Workflow;
import com.arktech.superaccountant.workflow.models.WorkflowType;
import com.arktech.superaccountant.workflow.repository.WorkflowRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TdsPaymentAnalyzerTest {

    private static final UUID MASTERS_JOB = UUID.fromString(
            "00000000-0000-0000-0000-00000000000a");
    private static final LocalDate APRIL_START = LocalDate.of(2026, 4, 1);
    private static final LocalDate APRIL_END = LocalDate.of(2026, 4, 30);

    private final TdsEngine engine = new TdsEngine();
    private final FindingLifecycleService lifecycle = new FindingLifecycleService();

    private TdsAnalysisInputFactory factory;
    private TdsFindingRepository findingRepository;
    private TdsPaymentDetailRepository paymentDetailRepository;
    private TdsWorkflowDetailService detailService;
    private WorkflowRepository workflowRepository;
    private TdsPaymentAnalyzer analyzer;

    private final AnalysisContext ctx = new AnalysisContext(TdsEngineFixtures.WF,
            TdsEngineFixtures.ORG, WorkflowType.TDS_PAYMENT, APRIL_START, APRIL_END,
            TdsEngineFixtures.JOB, MASTERS_JOB, UUID.randomUUID());

    private final Workflow workflow = new Workflow();

    @BeforeEach
    void setUp() {
        factory = mock(TdsAnalysisInputFactory.class);
        findingRepository = mock(TdsFindingRepository.class);
        paymentDetailRepository = mock(TdsPaymentDetailRepository.class);
        detailService = mock(TdsWorkflowDetailService.class);
        workflowRepository = mock(WorkflowRepository.class);
        analyzer = new TdsPaymentAnalyzer(factory, engine, lifecycle, findingRepository,
                paymentDetailRepository, detailService, workflowRepository);
        workflow.setId(TdsEngineFixtures.WF);
        workflow.setOrganizationId(TdsEngineFixtures.ORG);
        workflow.setType(WorkflowType.TDS_PAYMENT);
        workflow.setPeriodStart(APRIL_START);
        workflow.setPeriodEnd(APRIL_END);
        when(workflowRepository.findById(TdsEngineFixtures.WF))
                .thenReturn(Optional.of(workflow));
        when(detailService.ensurePaymentDetail(workflow)).thenReturn(new TdsPaymentDetail());
    }

    private TdsAnalysisInput shortDeductionInput() {
        ParsedVoucher v = TdsEngineFixtures.voucher("V1", LocalDate.of(2026, 4, 10),
                "ABC Contractors", new Object[][]{
                        {"Subcontractor Charges", "100000"}, {"ABC Contractors", "-99500"},
                        {"TDS Payable 194C", "-500"}});
        return TdsEngineFixtures.input(List.of(v),
                TdsEngineFixtures.stdSnaps("ABCPE1234F"), TdsEngineFixtures.stdMappings(),
                APRIL_START, APRIL_END);
    }

    @Test
    void analyze_persistsStampedFindingsAndMonthDeducted() {
        TdsAnalysisInput input = shortDeductionInput();
        when(factory.build(ctx)).thenReturn(input);
        when(findingRepository.findByWorkflowIdAndSupersededAtIsNullOrderByVoucherDateAsc(
                TdsEngineFixtures.WF)).thenReturn(List.of());

        analyzer.analyze(ctx);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<TdsFinding>> captor = ArgumentCaptor.forClass(List.class);
        verify(findingRepository, times(2)).saveAll(captor.capture());
        assertTrue(captor.getAllValues().get(0).isEmpty());
        List<TdsFinding> saved = captor.getAllValues().get(1);
        assertEquals(1, saved.size());
        TdsFinding f = saved.get(0);
        assertNotNull(f.getFindingKey());
        assertNotNull(f.getEvidenceHash());
        assertNull(f.getSupersededAt());

        TdsAnalysisResult result = engine.analyze(input);
        Map<UUID, ParsedVoucher> byId = input.vouchers().stream()
                .collect(Collectors.toMap(ParsedVoucher::getId, Function.identity()));
        result.findings().forEach(x -> TdsFindingKeys.stamp(x, byId));
        TdsFinding savedFinding = saved.get(0);
        TdsFinding expected = result.findings().get(0);
        assertEquals(expected.getFindingKey(), savedFinding.getFindingKey());
        assertEquals(expected.getEvidenceHash(), savedFinding.getEvidenceHash());

        ArgumentCaptor<TdsPaymentDetail> detailCaptor =
                ArgumentCaptor.forClass(TdsPaymentDetail.class);
        verify(paymentDetailRepository).save(detailCaptor.capture());
        BigDecimal monthDeducted = result.entries().stream()
                .filter(e -> !e.getDateOfDeduction().isBefore(APRIL_START)
                        && !e.getDateOfDeduction().isAfter(APRIL_END))
                .map(e -> e.getTdsDeducted())
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, java.math.RoundingMode.UNNECESSARY);
        assertEquals(monthDeducted, detailCaptor.getValue().getTdsDeducted());
        assertEquals(0, detailCaptor.getValue().getTdsDeducted()
                .compareTo(new BigDecimal("500.00")));
    }

    @Test
    void analyze_withoutMastersJob_throws() {
        when(factory.build(ctx)).thenThrow(
                new IllegalStateException("No completed masters upload to analyse against"));

        assertThrows(IllegalStateException.class, () -> analyzer.analyze(ctx));

        verify(findingRepository, never()).saveAll(any());
        verify(findingRepository, never()).save(any());
        verify(paymentDetailRepository, never()).save(any());
        verify(workflowRepository, never()).findById(any());
    }

    @Test
    void reanalysis_carriesApprovedAndSupersedesPrevious() {
        TdsAnalysisInput input = shortDeductionInput();
        when(factory.build(ctx)).thenReturn(input);
        TdsAnalysisResult probe = engine.analyze(input);
        Map<UUID, ParsedVoucher> byId = input.vouchers().stream()
                .collect(Collectors.toMap(ParsedVoucher::getId, Function.identity()));
        probe.findings().forEach(f -> TdsFindingKeys.stamp(f, byId));
        TdsFinding template = probe.findings().get(0);
        TdsFinding prev = new TdsFinding();
        prev.setId(UUID.randomUUID());
        prev.setOrganizationId(template.getOrganizationId());
        prev.setWorkflowId(template.getWorkflowId());
        prev.setUploadJobId(template.getUploadJobId());
        prev.setFindingType(template.getFindingType());
        prev.setSeverity(template.getSeverity());
        prev.setPartyLedgerName(template.getPartyLedgerName());
        prev.setVoucherId(template.getVoucherId());
        prev.setVoucherDate(template.getVoucherDate());
        prev.setVoucherNumber(template.getVoucherNumber());
        prev.setSection(template.getSection());
        prev.setExpectedAmount(template.getExpectedAmount());
        prev.setActualAmount(template.getActualAmount());
        prev.setFindingKey(template.getFindingKey());
        prev.setEvidenceHash(template.getEvidenceHash());
        prev.setResolveStatus(ResolveStatus.APPROVED);
        when(findingRepository.findByWorkflowIdAndSupersededAtIsNullOrderByVoucherDateAsc(
                TdsEngineFixtures.WF)).thenReturn(List.of(prev));

        analyzer.analyze(ctx);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<TdsFinding>> captor = ArgumentCaptor.forClass(List.class);
        verify(findingRepository, org.mockito.Mockito.times(2)).saveAll(captor.capture());
        List<List<TdsFinding>> calls = captor.getAllValues();
        assertEquals(prev, calls.get(0).get(0));
        Instant supersededAt = calls.get(0).get(0).getSupersededAt();
        assertNotNull(supersededAt);
        TdsFinding carried = calls.get(1).stream()
                .filter(f -> f.getFindingType() == TdsFindingType.SHORT_DEDUCTED)
                .findFirst().orElseThrow();
        assertEquals(ResolveStatus.APPROVED, carried.getResolveStatus());
        assertEquals(prev.getId(), carried.getCarriedFromFindingId());
        assertNotEquals(prev.getId(), carried.getId());
        assertEquals(template.getEvidenceHash(), carried.getEvidenceHash());
    }
}
