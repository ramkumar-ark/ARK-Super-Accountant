package com.arktech.superaccountant.tds.workflow;

import com.arktech.superaccountant.masters.models.Organization;
import com.arktech.superaccountant.masters.repository.OrganizationRepository;
import com.arktech.superaccountant.tds.engine.TdsEngine;
import com.arktech.superaccountant.tds.models.Tds26qDetail;
import com.arktech.superaccountant.tds.models.TdsChallan;
import com.arktech.superaccountant.tds.models.TdsDeducteeEntry;
import com.arktech.superaccountant.tds.repository.Tds26qDetailRepository;
import com.arktech.superaccountant.tds.repository.TdsDeducteeEntryRepository;
import com.arktech.superaccountant.tds.services.TdsQuarterService;
import com.arktech.superaccountant.workflow.models.Workflow;
import com.arktech.superaccountant.workflow.models.WorkflowType;
import com.arktech.superaccountant.workflow.repository.WorkflowRepository;
import com.arktech.superaccountant.workflow.seam.FinalizePrecondition;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 26Q Finalize gate: the old plan's conditions 2-6, in the original order. Old condition 1
 * (NO_OPEN_FINDINGS) is already enforced by WorkflowService.requireZeroBlocking.
 */
@Component
public class Tds26qFinalizePrecondition implements FinalizePrecondition {

    private final TdsDeducteeEntryRepository entryRepository;
    private final TdsQuarterService quarterService;
    private final Tds26qDetailRepository detailRepository;
    private final OrganizationRepository organizationRepository;
    private final WorkflowRepository workflowRepository;

    public Tds26qFinalizePrecondition(TdsDeducteeEntryRepository entryRepository,
                                      TdsQuarterService quarterService,
                                      Tds26qDetailRepository detailRepository,
                                      OrganizationRepository organizationRepository,
                                      WorkflowRepository workflowRepository) {
        this.entryRepository = entryRepository;
        this.quarterService = quarterService;
        this.detailRepository = detailRepository;
        this.organizationRepository = organizationRepository;
        this.workflowRepository = workflowRepository;
    }

    @Override
    public boolean appliesTo(Workflow w) {
        return w.getType() == WorkflowType.TDS_26Q;
    }

    @Override
    public List<String> unmet(Workflow w) {
        return evaluate(w).stream()
                .filter(c -> !c.passed())
                .map(c -> c.label() + ": " + c.detail())
                .toList();
    }

    public List<TdsGateCondition> evaluate(Workflow q) {
        List<TdsDeducteeEntry> entries = entryRepository
                .findByWorkflowIdAndSupersededAtIsNull(q.getId());
        List<TdsChallan> approved = quarterService.approvedChallans(q);

        List<TdsGateCondition> conditions = new ArrayList<>();
        conditions.add(allEntriesMapped(entries));
        conditions.add(challansReconcile(entries, approved));
        conditions.add(pansValid(entries));
        conditions.add(deductorComplete(q));
        conditions.add(hasChallan(approved));
        return conditions;
    }

    private TdsGateCondition allEntriesMapped(List<TdsDeducteeEntry> entries) {
        long unmapped = entries.stream().filter(e -> e.getTdsChallanId() == null).count();
        return new TdsGateCondition("ALL_ENTRIES_MAPPED", "All entries mapped", unmapped == 0,
                unmapped + " entries unmapped");
    }

    private TdsGateCondition challansReconcile(List<TdsDeducteeEntry> entries,
                                               List<TdsChallan> approved) {
        Map<UUID, List<TdsDeducteeEntry>> mapped = new java.util.LinkedHashMap<>();
        for (TdsDeducteeEntry e : entries) {
            if (e.getTdsChallanId() != null) {
                mapped.computeIfAbsent(e.getTdsChallanId(), k -> new ArrayList<>()).add(e);
            }
        }
        List<String> mismatches = new ArrayList<>();
        for (TdsChallan challan : approved) {
            BigDecimal mappedTotal = mapped
                    .getOrDefault(challan.getId(), List.of()).stream()
                    .map(TdsDeducteeEntry::getTdsDeposited)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (mappedTotal.compareTo(challan.getTdsAmount()) != 0) {
                mismatches.add("Challan " + challan.getChallanSerialNumber() + ": mapped "
                        + mappedTotal.toPlainString() + " vs challan "
                        + challan.getTdsAmount().toPlainString());
            }
        }
        return new TdsGateCondition("CHALLANS_RECONCILE", "Challans reconcile",
                mismatches.isEmpty(), String.join("; ", mismatches));
    }

    private TdsGateCondition pansValid(List<TdsDeducteeEntry> entries) {
        long invalid = entries.stream()
                .filter(e -> !e.isPanValid() && !e.isHigherRateApplied()).count();
        return new TdsGateCondition("PANS_VALID", "PANs valid", invalid == 0,
                invalid + " entries without a valid PAN");
    }

    private TdsGateCondition deductorComplete(Workflow q) {
        Tds26qDetail detail = detailRepository.findByWorkflowId(q.getId()).orElse(null);
        if (detail == null) {
            return new TdsGateCondition("DEDUCTOR_COMPLETE", "Deductor complete", false,
                    "No 26Q detail");
        }
        int fyStartMonth = organizationRepository.findById(q.getOrganizationId())
                .map(Organization::getFinancialYearStart).orElse(4);
        List<String> problems = new ArrayList<>();
        if (detail.getTan() == null || !detail.getTan().matches(Organization.TAN_PATTERN)) {
            problems.add("TAN is invalid");
        }
        if (detail.getDeductorPan() == null
                || !detail.getDeductorPan().matches(TdsEngine.PAN_PATTERN)) {
            problems.add("Deductor PAN is invalid");
        }
        if (isBlank(detail.getDeductorName()) || isBlank(detail.getDeductorAddress())) {
            problems.add("Deductor name and address are required");
        }
        if (isBlank(detail.getResponsiblePersonName())) {
            problems.add("Responsible person name is required");
        }
        if (fyOf(q, fyStartMonth) > 0 && q.getRevision() > 0
                && isBlank(detail.getOriginalReceiptNumber())) {
            problems.add("Original receipt number is required for a revised statement");
        }
        return new TdsGateCondition("DEDUCTOR_COMPLETE", "Deductor complete", problems.isEmpty(),
                String.join("; ", problems));
    }

    private TdsGateCondition hasChallan(List<TdsChallan> approved) {
        return new TdsGateCondition("HAS_CHALLAN", "Has approved challan", !approved.isEmpty(),
                "The quarter has no approved challan");
    }

    private int fyOf(Workflow q, int fyStartMonth) {
        return com.arktech.superaccountant.workflow.service.period.PeriodCalendar
                .financialYearOf(q.getPeriodStart(), fyStartMonth);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
