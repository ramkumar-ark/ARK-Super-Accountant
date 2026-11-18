package com.arktech.superaccountant.tds.workflow;

import com.arktech.superaccountant.daybook.models.DayBookUploadJob;
import com.arktech.superaccountant.daybook.repository.DayBookUploadJobRepository;
import com.arktech.superaccountant.masters.models.Organization;
import com.arktech.superaccountant.masters.repository.OrganizationRepository;
import com.arktech.superaccountant.tds.engine.RetroFlag;
import com.arktech.superaccountant.tds.engine.TdsAnalysisInput;
import com.arktech.superaccountant.tds.engine.TdsEngine;
import com.arktech.superaccountant.workflow.analysis.AnalysisContext;
import com.arktech.superaccountant.workflow.models.WorkflowType;
import com.arktech.superaccountant.workflow.seam.MastersJobGate;
import com.arktech.superaccountant.workflow.service.period.PeriodCalendar;
import com.arktech.superaccountant.workflow.snapshot.RetroFlagSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Re-runs the TDS engine over a completed day-book upload and reports earlier
 * vouchers that became deductible because a later voucher crossed the annual
 * threshold. The comparison layer decides whether the earlier month is frozen.
 */
@Component
public class TdsRetroFlagSource implements RetroFlagSource {

    private static final UUID NO_WORKFLOW = new UUID(0L, 0L);

    @Autowired
    private DayBookUploadJobRepository dayBookUploadJobRepository;
    @Autowired
    private OrganizationRepository organizationRepository;
    @Autowired
    private MastersJobGate mastersJobGate;
    @Autowired
    private TdsAnalysisInputFactory inputFactory;
    @Autowired
    private TdsEngine tdsEngine;

    @Override
    public List<Flag> retroFlags(UUID organizationId, UUID uploadJobId) {
        DayBookUploadJob job = dayBookUploadJobRepository.findById(uploadJobId).orElse(null);
        if (job == null || job.getCoverageFrom() == null || job.getCoverageTo() == null) {
            return List.of();
        }
        Organization org = organizationRepository.findById(organizationId).orElse(null);
        UUID mastersJobId = mastersJobGate.latestCompletedMastersJob(organizationId).orElse(null);
        if (org == null || mastersJobId == null) {
            return List.of();
        }
        LocalDate fyStart = PeriodCalendar.fyStart(
                PeriodCalendar.financialYearOf(job.getCoverageFrom(), org.getFinancialYearStart()),
                org.getFinancialYearStart());
        LocalDate fyEnd = PeriodCalendar.fyEnd(
                PeriodCalendar.financialYearOf(job.getCoverageFrom(), org.getFinancialYearStart()),
                org.getFinancialYearStart());
        LocalDate periodEnd = job.getCoverageTo().isAfter(fyEnd) ? fyEnd : job.getCoverageTo();
        if (periodEnd.isBefore(fyStart)) {
            return List.of();
        }
        AnalysisContext ctx = new AnalysisContext(NO_WORKFLOW, organizationId, WorkflowType.TDS_PAYMENT,
                fyStart, periodEnd, uploadJobId, mastersJobId, null);
        TdsAnalysisInput input = inputFactory.build(ctx);
        return tdsEngine.analyze(input).retroFlags().stream()
                .map(this::toFlag)
                .toList();
    }

    private Flag toFlag(RetroFlag flag) {
        return new Flag(flag.voucherKey(), flag.voucherDate(), flag.section(),
                flag.deducteeLedger(), flag.shortfall());
    }
}
