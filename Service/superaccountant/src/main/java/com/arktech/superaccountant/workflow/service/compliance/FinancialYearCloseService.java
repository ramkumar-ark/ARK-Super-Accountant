package com.arktech.superaccountant.workflow.service.compliance;

import com.arktech.superaccountant.masters.models.Organization;
import com.arktech.superaccountant.masters.repository.OrganizationRepository;
import com.arktech.superaccountant.workflow.models.CloseType;
import com.arktech.superaccountant.workflow.models.FinancialYearClose;
import com.arktech.superaccountant.workflow.models.Workflow;
import com.arktech.superaccountant.workflow.models.WorkflowStage;
import com.arktech.superaccountant.workflow.models.WorkflowType;
import com.arktech.superaccountant.workflow.payload.request.CloseFinancialYearRequest;
import com.arktech.superaccountant.workflow.payload.response.CloseFinancialYearResponse;
import com.arktech.superaccountant.workflow.payload.response.FinancialYearStatusResponse;
import com.arktech.superaccountant.workflow.repository.FinancialYearCloseRepository;
import com.arktech.superaccountant.workflow.repository.WorkflowRepository;
import com.arktech.superaccountant.workflow.seam.CompanyBlockerSource;
import com.arktech.superaccountant.workflow.service.CoverageService;
import com.arktech.superaccountant.workflow.service.WorkflowBadRequestException;
import com.arktech.superaccountant.workflow.service.WorkflowConflictException;
import com.arktech.superaccountant.workflow.service.period.PeriodCalendar;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class FinancialYearCloseService {

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private FinancialYearCloseRepository financialYearCloseRepository;

    @Autowired
    private WorkflowRepository workflowRepository;

    @Autowired
    private CoverageService coverageService;

    @Autowired
    private com.arktech.superaccountant.workflow.service.WorkflowService workflowService;

    @Autowired
    private com.arktech.superaccountant.workflow.repository.ResponsibilityStartRepository responsibilityStartRepository;

    @Autowired(required = false)
    private List<CompanyBlockerSource> blockerSources;

    public List<FinancialYearStatusResponse> listYears(UUID organizationId) {
        Organization org = organizationRepository.findById(organizationId).orElseThrow();
        int fyStartMonth = org.getFinancialYearStart();
        LocalDate books = org.getBooksStartDate();
        int firstFy = books != null
                ? PeriodCalendar.financialYearOf(books, fyStartMonth)
                : PeriodCalendar.financialYearOf(LocalDate.now(), fyStartMonth);
        int currentFy = PeriodCalendar.financialYearOf(LocalDate.now(), fyStartMonth);
        List<FinancialYearStatusResponse> years = new ArrayList<>();
        for (int fy = firstFy; fy <= currentFy; fy++) {
            years.add(status(organizationId, fy));
        }
        return years;
    }

    public FinancialYearStatusResponse status(UUID organizationId, int financialYear) {
        Organization org = organizationRepository.findById(organizationId).orElseThrow();
        int fyStartMonth = org.getFinancialYearStart();
        LocalDate start = PeriodCalendar.fyStart(financialYear, fyStartMonth);
        LocalDate end = PeriodCalendar.fyEnd(financialYear, fyStartMonth);
        Optional<FinancialYearClose> close = financialYearCloseRepository
                .findByOrganizationIdAndFinancialYear(organizationId, financialYear);
        List<String> openItems = openItems(organizationId, financialYear);
        List<String> blockers = closeBlockers(organizationId, financialYear);
        boolean closable = close.isEmpty() && openItems.isEmpty() && blockers.isEmpty()
                && end.isBefore(LocalDate.now())
                && financialYear == earliestOpenFy(organizationId, org);
        return new FinancialYearStatusResponse(
                financialYear, start, end,
                close.isPresent(),
                close.map(c -> c.getCloseType().name()).orElse(null),
                close.map(FinancialYearClose::getItrAckNo).orElse(null),
                close.map(FinancialYearClose::getItrAckDate).orElse(null),
                close.map(FinancialYearClose::getNote).orElse(null),
                closable, openItems, blockers);
    }

    private int earliestOpenFy(UUID organizationId, Organization org) {
        int fyStartMonth = org.getFinancialYearStart();
        LocalDate books = org.getBooksStartDate();
        int earliestOpen = books != null
                ? PeriodCalendar.financialYearOf(books, fyStartMonth)
                : PeriodCalendar.financialYearOf(LocalDate.now(), fyStartMonth);
        while (financialYearCloseRepository
                .findByOrganizationIdAndFinancialYear(organizationId, earliestOpen).isPresent()) {
            earliestOpen++;
        }
        return earliestOpen;
    }

    @Transactional
    public CloseFinancialYearResponse close(UUID organizationId, String actor, int financialYear,
            CloseFinancialYearRequest request) {
        Organization org = organizationRepository.findById(organizationId).orElseThrow();
        int fyStartMonth = org.getFinancialYearStart();
        if (financialYearCloseRepository
                .findByOrganizationIdAndFinancialYear(organizationId, financialYear).isPresent()) {
            throw new WorkflowConflictException("FY " + financialYear + " is already closed");
        }
        LocalDate books = org.getBooksStartDate();
        if (!PeriodCalendar.fyEnd(financialYear, fyStartMonth).isBefore(LocalDate.now())) {
            throw new WorkflowConflictException("FY has not ended");
        }
        int earliestOpen = earliestOpenFy(organizationId, org);
        if (financialYear != earliestOpen) {
            throw new WorkflowConflictException("Close FY " + earliestOpen + " first");
        }
        CloseType closeType;
        try {
            closeType = CloseType.valueOf(request.getCloseType());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new WorkflowBadRequestException("closeType must be ITR_FILED or HANDLED_ELSEWHERE");
        }
        if (closeType == CloseType.ITR_FILED) {
            if (request.getItrAckNo() == null || request.getItrAckNo().isBlank()
                    || request.getItrAckDate() == null) {
                throw new WorkflowBadRequestException(
                        "ITR_FILED requires itrAckNo and itrAckDate");
            }
        } else {
            if (request.getNote() == null || request.getNote().isBlank()) {
                throw new WorkflowBadRequestException(
                        "HANDLED_ELSEWHERE requires a note");
            }
        }

        List<String> openItems = openItems(organizationId, financialYear);
        List<String> blockers = closeBlockers(organizationId, financialYear);
        if (closeType == CloseType.ITR_FILED && (!openItems.isEmpty() || !blockers.isEmpty())) {
            List<String> all = new ArrayList<>();
            all.addAll(openItems);
            all.addAll(blockers);
            throw new WorkflowConflictException(
                    "FY cannot be closed as ITR_FILED: " + String.join("; ", all));
        }

        FinancialYearClose close = new FinancialYearClose();
        close.setOrganizationId(organizationId);
        close.setFinancialYear(financialYear);
        close.setCloseType(closeType);
        close.setItrAckNo(request.getItrAckNo());
        close.setItrAckDate(request.getItrAckDate());
        close.setNote(request.getNote());
        close.setClosedBy(actor);
        financialYearCloseRepository.save(close);

        return new CloseFinancialYearResponse(status(organizationId, financialYear),
                closeType == CloseType.HANDLED_ELSEWHERE
                        ? List.copyOf(concat(openItems, blockers))
                        : List.of());
    }

    private List<String> concat(List<String> a, List<String> b) {
        List<String> all = new ArrayList<>(a);
        all.addAll(b);
        return all;
    }

    private List<String> openItems(UUID organizationId, int financialYear) {
        Organization org = organizationRepository.findById(organizationId).orElseThrow();
        int fyStartMonth = org.getFinancialYearStart();
        LocalDate fyStart = PeriodCalendar.fyStart(financialYear, fyStartMonth);
        LocalDate fyEndPlus = PeriodCalendar.fyEnd(financialYear, fyStartMonth).plusDays(1);
        LocalDate coverage = coverageService.coverageStart(organizationId);
        List<String> items = new ArrayList<>();
        for (var rs : responsibilityStartRepository.findAllByOrganizationId(organizationId)) {
            WorkflowType type = rs.getType();
            List<PeriodCalendar.PeriodRange> periods = CompliancePeriods.periods(
                    type, fyStart.isAfter(coverage) ? fyStart : coverage, fyEndPlus, fyStartMonth,
                    d -> workflowService.profileInForce(organizationId, d));
            for (PeriodCalendar.PeriodRange range : periods) {
                Workflow w = workflowRepository
                        .findByOrganizationIdAndTypeAndPeriodKeyAndActiveTrue(
                                organizationId, type, range.periodKey())
                        .orElse(null);
                String stage = w == null ? "not started" : w.getStage().name();
                if (w != null && (w.getStage() == WorkflowStage.FILED
                        || w.getStage() == WorkflowStage.BASELINE)) {
                    continue;
                }
                items.add(type.name() + " " + range.periodKey() + ": " + stage);
            }
        }
        return items;
    }

    private List<String> closeBlockers(UUID organizationId, int financialYear) {
        if (blockerSources == null) {
            return List.of();
        }
        List<String> blockers = new ArrayList<>();
        for (CompanyBlockerSource source : blockerSources) {
            blockers.addAll(source.financialYearCloseBlockers(organizationId, financialYear));
        }
        return blockers;
    }
}
