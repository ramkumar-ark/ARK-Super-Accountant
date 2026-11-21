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

    @org.springframework.beans.factory.annotation.Autowired
    private com.arktech.superaccountant.tds.repository.TdsPanOverrideRepository panOverrideRepository;

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
        applyPanOverlay(ctx, snapshotsByName);
        Map<String, TdsSectionConfig> configsBySection = new LinkedHashMap<>();
        for (TdsSectionConfig c : configRepository.findAll()) {
            configsBySection.putIfAbsent(c.getSectionCode(), c.effectiveFor(ctx.periodStart()));
        }
        return new TdsAnalysisInput(ctx.organizationId(), ctx.workflowId(), ctx.uploadJobId(),
                vouchers, snapshotsByName, configsBySection, fyStart, ctx.periodStart(), ctx.periodEnd());
    }

    /**
     * Company PAN overrides (C4-PAN): applied on detached copies so the stored masters snapshot is
     * never mutated; a valid masters PAN always wins.
     */
    private void applyPanOverlay(AnalysisContext ctx,
                                 Map<String, UploadLedgerSnapshot> snapshotsByName) {
        for (com.arktech.superaccountant.tds.models.TdsPanOverride o
                : panOverrideRepository.findByOrganizationIdOrderByPartyLedgerNameAsc(
                        ctx.organizationId())) {
            UploadLedgerSnapshot s = snapshotsByName.get(o.getPartyKey());
            if (s == null) {
                UploadLedgerSnapshot synthetic = new UploadLedgerSnapshot();
                synthetic.setLedgerName(o.getPartyLedgerName());
                synthetic.setPan(o.getPan());
                snapshotsByName.put(o.getPartyKey(), synthetic);
            } else if (s.getPan() == null || s.getPan().isBlank()
                    || !s.getPan().matches(TdsEngine.PAN_PATTERN)) {
                UploadLedgerSnapshot copy = new UploadLedgerSnapshot();
                org.springframework.beans.BeanUtils.copyProperties(s, copy);
                copy.setPan(o.getPan());
                snapshotsByName.put(o.getPartyKey(), copy);
            }
        }
    }
}
