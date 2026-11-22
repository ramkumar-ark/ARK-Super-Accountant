package com.arktech.superaccountant.tds.workflow;

import com.arktech.superaccountant.daybook.models.ParsedVoucher;
import com.arktech.superaccountant.daybook.repository.ParsedVoucherRepository;
import com.arktech.superaccountant.masters.models.InterestPayerType;
import com.arktech.superaccountant.masters.models.Organization;
import com.arktech.superaccountant.masters.models.UploadLedgerSnapshot;
import com.arktech.superaccountant.masters.repository.OrganizationRepository;
import com.arktech.superaccountant.masters.repository.UploadLedgerSnapshotRepository;
import com.arktech.superaccountant.tds.engine.TdsAnalysisInput;
import com.arktech.superaccountant.tds.engine.TdsEngine;
import com.arktech.superaccountant.tds.models.Tds194aRules;
import com.arktech.superaccountant.tds.models.TdsSectionConfig;
import com.arktech.superaccountant.tds.repository.TdsSectionConfigRepository;
import com.arktech.superaccountant.workflow.analysis.AnalysisContext;
import com.arktech.superaccountant.workflow.service.period.PeriodCalendar;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
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
        // Legacy stored 194I (pre-split rent) is the land/building half; keep rows that still
        // carry it resolvable even before/without the stored-value migration.
        if (!configsBySection.containsKey("194I") && configsBySection.containsKey("194I_B")) {
            configsBySection.put("194I", configsBySection.get("194I_B"));
        }
        applyInterestPayerThreshold(org, configsBySection, ctx.periodStart());
        return new TdsAnalysisInput(ctx.organizationId(), ctx.workflowId(), ctx.uploadJobId(),
                vouchers, snapshotsByName, configsBySection, fyStart, ctx.periodStart(), ctx.periodEnd());
    }

    /**
     * Section 194A has one stored threshold but two company-level thresholds: a bank, co-operative
     * bank or post office gets Rs 50,000 (Rs 40,000 before 1 April 2025), every other payer Rs 10,000. Apply the company's choice
     * to a detached copy so the stored config (and the managed entity) is never mutated.
     */
    private void applyInterestPayerThreshold(Organization org,
                                             Map<String, TdsSectionConfig> configsBySection,
                                             LocalDate periodStart) {
        TdsSectionConfig config = configsBySection.get("194A");
        if (config == null) {
            return;
        }
        BigDecimal threshold = Tds194aRules.thresholdAnnualForPeriod(
                InterestPayerType.from(org.getInterestPayerType()), periodStart);
        if (config.getThresholdAnnual() != null
                && config.getThresholdAnnual().compareTo(threshold) == 0) {
            return;
        }
        TdsSectionConfig copy = new TdsSectionConfig();
        BeanUtils.copyProperties(config, copy);
        copy.setThresholdAnnual(threshold);
        configsBySection.put("194A", copy);
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
