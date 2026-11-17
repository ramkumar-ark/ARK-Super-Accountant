package com.arktech.superaccountant.tds.workflow;

import com.arktech.superaccountant.daybook.models.ParsedVoucher;
import com.arktech.superaccountant.masters.models.ResolveStatus;
import com.arktech.superaccountant.review.FindingLifecycleService;
import com.arktech.superaccountant.tds.engine.TdsAnalysisInput;
import com.arktech.superaccountant.tds.engine.TdsAnalysisResult;
import com.arktech.superaccountant.tds.engine.TdsEngine;
import com.arktech.superaccountant.tds.models.TdsDeducteeEntry;
import com.arktech.superaccountant.tds.models.TdsFinding;
import com.arktech.superaccountant.tds.models.TdsPaymentDetail;
import com.arktech.superaccountant.tds.repository.TdsFindingRepository;
import com.arktech.superaccountant.tds.repository.TdsPaymentDetailRepository;
import com.arktech.superaccountant.tds.services.TdsWorkflowDetailService;
import com.arktech.superaccountant.workflow.analysis.AnalysisContext;
import com.arktech.superaccountant.workflow.analysis.WorkflowAnalyzer;
import com.arktech.superaccountant.workflow.models.Workflow;
import com.arktech.superaccountant.workflow.models.WorkflowType;
import com.arktech.superaccountant.workflow.repository.WorkflowRepository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@org.springframework.stereotype.Component
public class TdsPaymentAnalyzer implements WorkflowAnalyzer {

    private final TdsAnalysisInputFactory factory;
    private final TdsEngine tdsEngine;
    private final FindingLifecycleService lifecycleService;
    private final TdsFindingRepository findingRepository;
    private final TdsPaymentDetailRepository paymentDetailRepository;
    private final TdsWorkflowDetailService detailService;
    private final WorkflowRepository workflowRepository;

    public TdsPaymentAnalyzer(TdsAnalysisInputFactory factory, TdsEngine tdsEngine,
                              FindingLifecycleService lifecycleService,
                              TdsFindingRepository findingRepository,
                              TdsPaymentDetailRepository paymentDetailRepository,
                              TdsWorkflowDetailService detailService,
                              WorkflowRepository workflowRepository) {
        this.factory = factory;
        this.tdsEngine = tdsEngine;
        this.lifecycleService = lifecycleService;
        this.findingRepository = findingRepository;
        this.paymentDetailRepository = paymentDetailRepository;
        this.detailService = detailService;
        this.workflowRepository = workflowRepository;
    }

    @Override
    public String code() {
        return "TDS_PAYMENT";
    }

    @Override
    public Set<WorkflowType> types() {
        return Set.of(WorkflowType.TDS_PAYMENT);
    }

    @Override
    @Transactional
    public void analyze(AnalysisContext ctx) {
        TdsAnalysisInput input = factory.build(ctx);
        TdsAnalysisResult result = tdsEngine.analyze(input);
        Map<UUID, ParsedVoucher> byId = input.vouchers().stream()
                .collect(Collectors.toMap(ParsedVoucher::getId, Function.identity()));
        Instant now = Instant.now();
        List<TdsFinding> fresh = new ArrayList<>(result.findings());
        fresh.forEach(f -> TdsFindingKeys.stamp(f, byId));

        Workflow workflow = workflowRepository.findById(ctx.workflowId())
                .orElseThrow(() -> new IllegalStateException("Workflow not found: " + ctx.workflowId()));
        List<TdsFinding> ownPrevious = findingRepository
                .findByWorkflowIdAndSupersededAtIsNullOrderByVoucherDateAsc(workflow.getId());
        List<TdsFinding> previous = ownPrevious;
        if (previous.isEmpty() && workflow.getSupersedesWorkflowId() != null) {
            previous = findingRepository
                    .findByWorkflowIdAndSupersededAtIsNullOrderByVoucherDateAsc(
                            workflow.getSupersedesWorkflowId());
        }
        lifecycleService.reconcile(previous, fresh, now);
        previous.forEach(p -> p.setSupersededAt(now));
        findingRepository.saveAll(previous);
        findingRepository.saveAll(fresh);

        TdsPaymentDetail detail = detailService.ensurePaymentDetail(workflow);
        BigDecimal deducted = result.entries().stream()
                .filter(e -> inPeriod(e.getDateOfDeduction(), ctx.periodStart(), ctx.periodEnd()))
                .map(TdsDeducteeEntry::getTdsDeducted)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, java.math.RoundingMode.UNNECESSARY);
        detail.setTdsDeducted(deducted);
        paymentDetailRepository.save(detail);
    }

    private static boolean inPeriod(LocalDate date, LocalDate start, LocalDate end) {
        return date != null && !date.isBefore(start) && !date.isAfter(end);
    }
}
