package com.arktech.superaccountant.tds.workflow;

import com.arktech.superaccountant.daybook.models.ParsedVoucher;
import com.arktech.superaccountant.daybook.repository.ParsedVoucherRepository;
import com.arktech.superaccountant.masters.models.Organization;
import com.arktech.superaccountant.masters.models.UploadLedgerSnapshot;
import com.arktech.superaccountant.masters.repository.OrganizationRepository;
import com.arktech.superaccountant.masters.repository.UploadLedgerSnapshotRepository;
import com.arktech.superaccountant.tds.engine.TdsAnalysisInput;
import com.arktech.superaccountant.tds.engine.TdsEngine;
import com.arktech.superaccountant.tds.models.TdsSectionConfig;
import com.arktech.superaccountant.tds.repository.TdsSectionConfigRepository;
import com.arktech.superaccountant.workflow.analysis.AnalysisContext;
import com.arktech.superaccountant.workflow.service.period.PeriodCalendar;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class TdsAnalysisInputFactory {

    @org.springframework.beans.factory.annotation.Autowired
    private ParsedVoucherRepository parsedVoucherRepository;

    @org.springframework.beans.factory.annotation.Autowired
    private UploadLedgerSnapshotRepository snapshotRepository;

    @org.springframework.beans.factory.annotation.Autowired
    private TdsSectionConfigRepository configRepository;

    @org.springframework.beans.factory.annotation.Autowired
    private OrganizationRepository organizationRepository;

    public TdsAnalysisInput build(AnalysisContext ctx) {
        if (ctx.mastersJobId() == null) {
            throw new IllegalStateException("No completed masters upload to analyse against");
        }
        Organization org = organizationRepository.findById(ctx.organizationId())
                .orElseThrow(() -> new IllegalStateException("Organization not found"));
        LocalDate fyStart = PeriodCalendar.fyStart(
                PeriodCalendar.financialYearOf(ctx.periodStart(), org.getFinancialYearStart()),
                org.getFinancialYearStart());
        List<ParsedVoucher> vouchers = parsedVoucherRepository.findByUploadJobId(ctx.uploadJobId());
        Map<String, UploadLedgerSnapshot> snapshotsByName = new LinkedHashMap<>();
        for (UploadLedgerSnapshot s : snapshotRepository.findByUploadJobId(ctx.mastersJobId())) {
            snapshotsByName.putIfAbsent(TdsEngine.key(s.getLedgerName()), s);
        }
        Map<String, TdsSectionConfig> configsBySection = new LinkedHashMap<>();
        for (TdsSectionConfig c : configRepository.findAll()) {
            configsBySection.putIfAbsent(c.getSectionCode(), c);
        }
        return new TdsAnalysisInput(ctx.organizationId(), ctx.workflowId(), ctx.uploadJobId(),
                vouchers, snapshotsByName, configsBySection, fyStart, ctx.periodStart(), ctx.periodEnd());
    }
}
