package com.arktech.superaccountant.tds.services;

import com.arktech.superaccountant.masters.models.Organization;
import com.arktech.superaccountant.masters.repository.OrganizationRepository;
import com.arktech.superaccountant.tds.models.TdsChallan;
import com.arktech.superaccountant.tds.models.TdsChallanStatus;
import com.arktech.superaccountant.tds.repository.TdsChallanRepository;
import com.arktech.superaccountant.workflow.models.Workflow;
import com.arktech.superaccountant.workflow.models.WorkflowType;
import com.arktech.superaccountant.workflow.repository.WorkflowRepository;
import com.arktech.superaccountant.workflow.service.period.PeriodCalendar;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

@Service
public class TdsQuarterService {

    private final WorkflowRepository workflowRepository;
    private final TdsChallanRepository challanRepository;
    private final OrganizationRepository organizationRepository;

    public TdsQuarterService(WorkflowRepository workflowRepository,
                             TdsChallanRepository challanRepository,
                             OrganizationRepository organizationRepository) {
        this.workflowRepository = workflowRepository;
        this.challanRepository = challanRepository;
        this.organizationRepository = organizationRepository;
    }

    /** The active TDS_PAYMENT workflows for the quarter's months. */
    public List<Workflow> paymentWorkflows(Workflow quarter) {
        UUID orgId = quarter.getOrganizationId();
        int fyStartMonth = organizationRepository.findById(orgId)
                .map(Organization::getFinancialYearStart).orElse(4);
        List<Workflow> payment = new ArrayList<>();
        for (String month : PeriodCalendar.quarterMonths(quarter.getPeriodKey(), fyStartMonth)) {
            workflowRepository.findByOrganizationIdAndTypeAndPeriodKeyAndActiveTrue(
                    orgId, WorkflowType.TDS_PAYMENT, month).ifPresent(payment::add);
        }
        return payment;
    }

    /** APPROVED challans of the quarter's payment workflows, keyed by payment workflow. */
    public LinkedHashMap<Workflow, List<TdsChallan>> approvedChallansByMonth(Workflow quarter) {
        LinkedHashMap<Workflow, List<TdsChallan>> byMonth = new LinkedHashMap<>();
        for (Workflow payment : paymentWorkflows(quarter)) {
            byMonth.put(payment, challanRepository
                    .findByPaymentWorkflowIdIn(List.of(payment.getId())).stream()
                    .filter(c -> c.getStatus() == TdsChallanStatus.APPROVED)
                    .toList());
        }
        return byMonth;
    }

    /** APPROVED challans of the quarter's payment workflows. */
    public List<TdsChallan> approvedChallans(Workflow quarter) {
        return approvedChallansByMonth(quarter).values().stream()
                .flatMap(List::stream)
                .toList();
    }
}
