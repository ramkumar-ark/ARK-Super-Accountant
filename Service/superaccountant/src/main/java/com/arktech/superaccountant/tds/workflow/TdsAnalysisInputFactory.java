package com.arktech.superaccountant.tds.workflow;

import com.arktech.superaccountant.daybook.models.ParsedVoucher;
import com.arktech.superaccountant.daybook.repository.ParsedVoucherRepository;
import com.arktech.superaccountant.masters.models.InterestPayerType;
import com.arktech.superaccountant.masters.models.Organization;
import com.arktech.superaccountant.masters.models.UploadLedgerSnapshot;
import com.arktech.superaccountant.masters.repository.OrganizationRepository;
import com.arktech.superaccountant.masters.repository.UploadLedgerSnapshotRepository;
import com.arktech.superaccountant.statutory.models.Statute;
import com.arktech.superaccountant.statutory.models.StatutorySection;
import com.arktech.superaccountant.statutory.models.StatutorySectionRule;
import com.arktech.superaccountant.statutory.repository.StatutorySectionRepository;
import com.arktech.superaccountant.statutory.repository.StatutorySectionRuleRepository;
import com.arktech.superaccountant.tds.engine.TdsAnalysisInput;
import com.arktech.superaccountant.tds.engine.TdsEngine;
import com.arktech.superaccountant.tds.mapping.TdsLedgerMapping;
import com.arktech.superaccountant.tds.mapping.TdsLedgerMappingService;
import com.arktech.superaccountant.statutory.models.StatutoryRuleContext;
import com.arktech.superaccountant.tds.models.Tds194aRules;
import com.arktech.superaccountant.tds.models.TdsSectionConfig;
import com.arktech.superaccountant.workflow.analysis.AnalysisContext;
import com.arktech.superaccountant.workflow.service.period.PeriodCalendar;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class TdsAnalysisInputFactory {

    @org.springframework.beans.factory.annotation.Autowired
    private ParsedVoucherRepository parsedVoucherRepository;

    @org.springframework.beans.factory.annotation.Autowired
    private UploadLedgerSnapshotRepository snapshotRepository;

    @org.springframework.beans.factory.annotation.Autowired
    private OrganizationRepository organizationRepository;

    @org.springframework.beans.factory.annotation.Autowired
    private com.arktech.superaccountant.tds.repository.TdsPanOverrideRepository panOverrideRepository;

    @org.springframework.beans.factory.annotation.Autowired
    private com.arktech.superaccountant.tds.repository.DebtorTanRepository debtorTanRepository;

    @org.springframework.beans.factory.annotation.Autowired
    private com.arktech.superaccountant.tds.mapping.TdsLedgerMappingGate mappingGate;

    @org.springframework.beans.factory.annotation.Autowired
    private TdsLedgerMappingService mappingService;

    @org.springframework.beans.factory.annotation.Autowired
    private StatutorySectionRepository statutorySectionRepository;

    @org.springframework.beans.factory.annotation.Autowired
    private StatutorySectionRuleRepository statutorySectionRuleRepository;

    public TdsAnalysisInput build(AnalysisContext ctx) {
        mappingGate.requireComplete(ctx.organizationId());
        Map<String, TdsLedgerMapping> mappingsByKey = mappingService.mappingsByKey(ctx.organizationId());
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
        Map<String, String> debtorTansByName = new LinkedHashMap<>();
        for (com.arktech.superaccountant.tds.models.DebtorTan record
                : debtorTanRepository.findByOrganizationIdOrderByLedgerNameAsc(ctx.organizationId())) {
            debtorTansByName.put(record.getLedgerKey(), record.getTan());
        }
        Map<String, TdsSectionConfig> configsBySection = buildSectionConfigs(ctx.periodStart());
        // Legacy stored 194I (pre-split rent) is the land/building half; keep rows that still
        // carry it resolvable even before/without the stored-value migration.
        if (!configsBySection.containsKey("194I") && configsBySection.containsKey("194I_B")) {
            configsBySection.put("194I", configsBySection.get("194I_B"));
        }
        applyInterestPayerThreshold(org, configsBySection, ctx.periodStart());
        return new TdsAnalysisInput(ctx.organizationId(), ctx.workflowId(), ctx.uploadJobId(),
                vouchers, snapshotsByName, mappingsByKey, debtorTansByName, configsBySection,
                fyStart, ctx.periodStart(), ctx.periodEnd());
    }

    /**
     * Single source of section truth: identity comes from statutory_sections and the
     * values in force for the period from the covering DEFAULT rule rows in
     * statutory_section_rules. The legacy TdsSectionConfig table is not read here.
     */
    private Map<String, TdsSectionConfig> buildSectionConfigs(LocalDate periodStart) {
        Map<UUID, List<StatutorySectionRule>> rulesBySectionId = new HashMap<>();
        for (StatutorySectionRule rule : statutorySectionRuleRepository.findAll()) {
            rulesBySectionId.computeIfAbsent(rule.getSectionId(), k -> new ArrayList<>()).add(rule);
        }
        Map<String, TdsSectionConfig> configsBySection = new LinkedHashMap<>();
        for (StatutorySection section
                : statutorySectionRepository.findByStatuteOrderBySortOrderAscCodeAsc(Statute.INCOME_TAX)) {
            if (!Boolean.TRUE.equals(section.getActive())) {
                continue;
            }
            configsBySection.putIfAbsent(section.getCode(),
                    effectiveConfig(section, rulesBySectionId.get(section.getId()), periodStart));
        }
        return configsBySection;
    }

    /**
     * The engine's view of one section for a period: a detached {@link TdsSectionConfig}
     * carrying the covering DEFAULT rule row's values. When no row covers the period
     * (before the earliest effectiveFrom), the earliest DEFAULT row is the regime in
     * force — the same values the legacy stored columns held.
     */
    private TdsSectionConfig effectiveConfig(StatutorySection section,
                                             List<StatutorySectionRule> rules,
                                             LocalDate periodStart) {
        TdsSectionConfig config = new TdsSectionConfig();
        config.setSectionCode(section.getCode());
        config.setDescription(section.getDescription());
        if (rules == null || rules.isEmpty()) {
            return config;
        }
        config.setDatedRules(rules);
        TdsSectionConfig effective = config.effectiveFor(periodStart);
        if (effective == config) {
            StatutorySectionRule earliest = rules.stream()
                    .filter(r -> StatutoryRuleContext.DEFAULT.equals(r.getRuleContext()))
                    .min(java.util.Comparator.comparing(StatutorySectionRule::getEffectiveFrom))
                    .orElse(null);
            if (earliest != null) {
                config.setRateCompany(earliest.getRateCompany());
                config.setRateNonCompany(earliest.getRateNonCompany());
                config.setThresholdPerTransaction(earliest.getThresholdPerTransaction());
                config.setThresholdAnnual(earliest.getThresholdAnnual());
                config.setHigherRateIfNoPan(earliest.getHigherRateIfNoPan());
            }
        }
        return effective;
    }

    /**
     * Section 194A has one stored threshold but company-level thresholds: a bank, co-operative
     * bank or post office gets Rs 40,000 before 1 April 2025 and Rs 50,000 from then (Finance Act
     * 2025); every other payer gets Rs 5,000 before 1 April 2025 and Rs 10,000 from then. Apply
     * the company's choice to a detached copy so the stored config (and the managed entity) is
     * never mutated.
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
            String overridePan = o.getPan() == null || o.getPan().isBlank() ? null : o.getPan();
            if (s == null) {
                UploadLedgerSnapshot synthetic = new UploadLedgerSnapshot();
                synthetic.setLedgerName(o.getPartyLedgerName());
                synthetic.setPan(overridePan);
                snapshotsByName.put(o.getPartyKey(), synthetic);
            } else if (s.getPan() == null || s.getPan().isBlank()
                    || !s.getPan().matches(TdsEngine.PAN_PATTERN)) {
                UploadLedgerSnapshot copy = new UploadLedgerSnapshot();
                org.springframework.beans.BeanUtils.copyProperties(s, copy);
                copy.setPan(overridePan);
                snapshotsByName.put(o.getPartyKey(), copy);
            }
        }
    }
}
