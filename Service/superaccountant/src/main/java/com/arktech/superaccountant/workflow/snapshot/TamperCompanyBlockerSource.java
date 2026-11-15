package com.arktech.superaccountant.workflow.snapshot;

import com.arktech.superaccountant.masters.models.Organization;
import com.arktech.superaccountant.masters.models.ResolveStatus;
import com.arktech.superaccountant.masters.repository.OrganizationRepository;
import com.arktech.superaccountant.workflow.models.Workflow;
import com.arktech.superaccountant.workflow.repository.WorkflowRepository;
import com.arktech.superaccountant.workflow.service.period.PeriodCalendar;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * CompanyBlockerSource over the tamper tables: open tamper findings and open
 * amendment follow-ups block financial-year close; any open tamper finding
 * blocks baseline confirmation.
 */
@Component
public class TamperCompanyBlockerSource
        implements com.arktech.superaccountant.workflow.seam.CompanyBlockerSource {

    private static final Set<ResolveStatus> TERMINAL =
            EnumSet.of(ResolveStatus.APPROVED, ResolveStatus.RESOLVED, ResolveStatus.DISCARDED);

    @Autowired
    private TamperFindingRepository tamperFindingRepository;
    @Autowired
    private AmendmentFollowUpRepository amendmentFollowUpRepository;
    @Autowired
    private OrganizationRepository organizationRepository;
    @Autowired
    private WorkflowRepository workflowRepository;

    @Override
    public List<String> financialYearCloseBlockers(UUID organizationId, int financialYear) {
        List<String> blockers = new ArrayList<>();
        int fyStartMonth = organizationRepository.findById(organizationId)
                .map(Organization::getFinancialYearStart)
                .orElse(4);
        LocalDate fyStart = PeriodCalendar.fyStart(financialYear, fyStartMonth);
        LocalDate fyEnd = PeriodCalendar.fyEnd(financialYear, fyStartMonth);
        for (TamperFinding f : tamperFindingRepository
                .findByOrganizationIdAndResolveStatusNotIn(organizationId, TERMINAL)) {
            if (f.getChangeDate() != null && !f.getChangeDate().isBefore(fyStart)
                    && !f.getChangeDate().isAfter(fyEnd)) {
                blockers.add("Open tamper finding: " + f.getMessage());
            }
        }
        for (AmendmentFollowUp f : amendmentFollowUpRepository
                .findByOrganizationIdAndStatus(organizationId, AmendmentStatus.OPEN)) {
            Workflow w = workflowRepository.findById(f.getWorkflowId()).orElse(null);
            if (w != null && w.getFinancialYear() == financialYear) {
                blockers.add("Open amendment follow-up: " + f.getSummary());
            }
        }
        return blockers;
    }

    @Override
    public List<String> baselineConfirmBlockers(UUID organizationId) {
        List<String> blockers = new ArrayList<>();
        for (TamperFinding f : tamperFindingRepository
                .findByOrganizationIdAndResolveStatusNotIn(organizationId, TERMINAL)) {
            blockers.add("Open tamper finding: " + f.getMessage());
        }
        return blockers;
    }
}
