package com.arktech.superaccountant.workflow.service.compliance;

import com.arktech.superaccountant.masters.models.Organization;
import com.arktech.superaccountant.masters.repository.OrganizationRepository;
import com.arktech.superaccountant.workflow.models.ResponsibilityStart;
import com.arktech.superaccountant.workflow.models.Workflow;
import com.arktech.superaccountant.workflow.models.WorkflowStage;
import com.arktech.superaccountant.workflow.models.WorkflowType;
import com.arktech.superaccountant.workflow.payload.response.ResponsibilityStartResponse;
import com.arktech.superaccountant.workflow.repository.ResponsibilityStartRepository;
import com.arktech.superaccountant.workflow.repository.WorkflowRepository;
import com.arktech.superaccountant.workflow.seam.SnapshotService;
import com.arktech.superaccountant.workflow.service.CoverageService;
import com.arktech.superaccountant.workflow.service.WorkflowBadRequestException;
import com.arktech.superaccountant.workflow.service.WorkflowConflictException;
import com.arktech.superaccountant.workflow.service.WorkflowService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ResponsibilityStartSettingsService {

    @Autowired
    private ResponsibilityStartRepository responsibilityStartRepository;

    @Autowired
    private WorkflowRepository workflowRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private CoverageService coverageService;

    @Autowired
    private WorkflowService workflowService;

    @Autowired
    private SnapshotService snapshotService;

    public List<ResponsibilityStartResponse> list(UUID organizationId) {
        List<ResponsibilityStartResponse> responses = new ArrayList<>();
        for (WorkflowType type : WorkflowType.values()) {
            responses.add(toResponse(organizationId, type));
        }
        return responses;
    }

    @Transactional
    public ResponsibilityStartResponse set(UUID organizationId, String actor, WorkflowType type,
            LocalDate firstPeriodStart) {
        Organization org = organizationRepository.findById(organizationId).orElseThrow();
        List<com.arktech.superaccountant.workflow.service.period.PeriodCalendar.PeriodRange> covering =
                CompliancePeriods.periods(type, firstPeriodStart, firstPeriodStart.plusDays(1),
                        org.getFinancialYearStart(), d -> workflowService.profileInForce(organizationId, d));
        boolean isPeriodStart = !covering.isEmpty()
                && covering.get(0).start().equals(firstPeriodStart);
        if (!isPeriodStart) {
            throw new WorkflowBadRequestException(
                    "firstPeriodStart must be a period start for " + type);
        }
        if (workflowRepository.existsByOrganizationIdAndTypeAndStageNot(organizationId, type, WorkflowStage.BASELINE)) {
            throw new WorkflowConflictException(
                    "Responsibility start for " + type + " is locked: a workflow already exists");
        }
        ResponsibilityStart row = responsibilityStartRepository
                .findByOrganizationIdAndType(organizationId, type)
                .orElseGet(() -> {
                    ResponsibilityStart r = new ResponsibilityStart();
                    r.setOrganizationId(organizationId);
                    r.setType(type);
                    return r;
                });
        row.setFirstPeriodStart(firstPeriodStart);
        row.setSetBy(actor);
        responsibilityStartRepository.save(row);
        rebuildBaselineRows(organizationId, actor, type);
        return toResponse(organizationId, type);
    }

    /** Deactivates this type's active BASELINE rows and re-creates them unconfirmed. */
    public int rebuildBaselineRows(UUID organizationId, String actor, WorkflowType type) {
        List<Workflow> active = workflowRepository
                .findAllByOrganizationIdAndTypeAndStageAndActiveTrue(organizationId, type, WorkflowStage.BASELINE);
        boolean anyConfirmed = active.stream()
                .anyMatch(w -> w.getBaselineConfirmedAt() != null);
        active.forEach(w -> w.setActive(false));
        workflowRepository.saveAllAndFlush(active);
        if (anyConfirmed) {
            UUID releasedId = active.stream()
                    .filter(w -> w.getBaselineConfirmedAt() != null)
                    .findFirst()
                    .orElseThrow()
                    .getId();
            snapshotService.release(releasedId, organizationId, actor);
        }

        ResponsibilityStart row = responsibilityStartRepository
                .findByOrganizationIdAndType(organizationId, type)
                .orElse(null);
        int created = 0;
        if (row != null) {
            Organization org = organizationRepository.findById(organizationId).orElseThrow();
            List<com.arktech.superaccountant.workflow.service.period.PeriodCalendar.PeriodRange> periods =
                    CompliancePeriods.periods(type, coverageService.coverageStart(organizationId),
                            row.getFirstPeriodStart(), org.getFinancialYearStart(),
                            d -> workflowService.profileInForce(organizationId, d));
            for (com.arktech.superaccountant.workflow.service.period.PeriodCalendar.PeriodRange range : periods) {
                Workflow w = new Workflow();
                w.setOrganizationId(organizationId);
                w.setType(type);
                w.setPeriodStart(range.start());
                w.setPeriodEnd(range.end());
                w.setPeriodKey(range.periodKey());
                w.setFinancialYear(range.financialYear());
                w.setStage(WorkflowStage.BASELINE);
                w.setActive(true);
                w.setRevision(0);
                w.setCreatedBy(actor);
                workflowRepository.save(w);
                created++;
            }
        }
        return created;
    }

    private ResponsibilityStartResponse toResponse(UUID organizationId, WorkflowType type) {
        ResponsibilityStart row = responsibilityStartRepository
                .findByOrganizationIdAndType(organizationId, type).orElse(null);
        boolean locked = workflowRepository.existsByOrganizationIdAndTypeAndStageNot(
                organizationId, type, WorkflowStage.BASELINE);
        int baselineRowCount = workflowRepository
                .findAllByOrganizationIdAndTypeAndStageAndActiveTrue(organizationId, type, WorkflowStage.BASELINE)
                .size();
        return new ResponsibilityStartResponse(
                type.name(),
                row == null ? null : row.getFirstPeriodStart(),
                row == null ? null : row.getSetBy(),
                row == null ? null : row.getSetAt(),
                locked,
                baselineRowCount);
    }
}
