package com.arktech.superaccountant.tds.workflow;

import com.arktech.superaccountant.tds.engine.TdsAnalysisInput;
import com.arktech.superaccountant.tds.engine.TdsAnalysisResult;
import com.arktech.superaccountant.tds.engine.TdsEngine;
import com.arktech.superaccountant.tds.models.TdsDeducteeEntry;
import com.arktech.superaccountant.tds.repository.TdsDeducteeEntryRepository;
import com.arktech.superaccountant.tds.services.TdsWorkflowDetailService;
import com.arktech.superaccountant.workflow.analysis.AnalysisContext;
import com.arktech.superaccountant.workflow.analysis.WorkflowAnalyzer;
import com.arktech.superaccountant.workflow.models.Workflow;
import com.arktech.superaccountant.workflow.models.WorkflowType;
import com.arktech.superaccountant.workflow.repository.WorkflowRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Builds the quarter's deductee entries. It persists no TdsFinding: the payment months own all
 * TDS findings (grill Q1(a)). A surviving entry keeps its challan mapping when the identity
 * (sourceVoucherIds + "|" + section) is unchanged.
 */
@Component
public class Tds26qAnalyzer implements WorkflowAnalyzer {

    private final TdsAnalysisInputFactory factory;
    private final TdsEngine tdsEngine;
    private final TdsDeducteeEntryRepository entryRepository;
    private final TdsWorkflowDetailService detailService;
    private final WorkflowRepository workflowRepository;

    public Tds26qAnalyzer(TdsAnalysisInputFactory factory, TdsEngine tdsEngine,
                          TdsDeducteeEntryRepository entryRepository,
                          TdsWorkflowDetailService detailService,
                          WorkflowRepository workflowRepository) {
        this.factory = factory;
        this.tdsEngine = tdsEngine;
        this.entryRepository = entryRepository;
        this.detailService = detailService;
        this.workflowRepository = workflowRepository;
    }

    @Override
    public String code() {
        return "TDS_26Q";
    }

    @Override
    public Set<WorkflowType> types() {
        return Set.of(WorkflowType.TDS_26Q);
    }

    @Override
    @Transactional
    public void analyze(AnalysisContext ctx) {
        Workflow workflow = workflowRepository.findById(ctx.workflowId())
                .orElseThrow(() -> new IllegalStateException("Workflow not found: " + ctx.workflowId()));
        detailService.ensure26qDetail(workflow);

        TdsAnalysisInput input = factory.build(ctx);
        TdsAnalysisResult result = tdsEngine.analyze(input);
        List<TdsDeducteeEntry> fresh = result.entries().stream()
                .filter(e -> inPeriod(e.getDateOfDeduction(), ctx.periodStart(), ctx.periodEnd()))
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);

        List<TdsDeducteeEntry> previous = entryRepository
                .findByWorkflowIdAndSupersededAtIsNull(workflow.getId());
        Map<String, UUID> mappingsByIdentity = new HashMap<>();
        for (TdsDeducteeEntry p : previous) {
            if (p.getTdsChallanId() != null) {
                mappingsByIdentity.put(identity(p), p.getTdsChallanId());
            }
        }
        for (TdsDeducteeEntry e : fresh) {
            UUID challanId = mappingsByIdentity.get(identity(e));
            if (challanId != null) {
                e.setTdsChallanId(challanId);
            }
        }

        Instant now = Instant.now();
        if (!previous.isEmpty()) {
            previous.forEach(p -> p.setSupersededAt(now));
            entryRepository.saveAll(previous);
        }
        entryRepository.saveAll(fresh);
    }

    private static String identity(TdsDeducteeEntry e) {
        return e.getSourceVoucherIds() + "|" + e.getSection();
    }

    private static boolean inPeriod(LocalDate date, LocalDate start, LocalDate end) {
        return date != null && !date.isBefore(start) && !date.isAfter(end);
    }
}
