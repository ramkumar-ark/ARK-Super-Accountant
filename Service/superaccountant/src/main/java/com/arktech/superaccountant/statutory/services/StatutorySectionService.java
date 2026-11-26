package com.arktech.superaccountant.statutory.services;

import com.arktech.superaccountant.statutory.models.Statute;
import com.arktech.superaccountant.statutory.models.StatutoryRuleContext;
import com.arktech.superaccountant.statutory.models.StatutorySection;
import com.arktech.superaccountant.statutory.models.StatutorySectionRule;
import com.arktech.superaccountant.statutory.models.StatutorySectionRules;
import com.arktech.superaccountant.statutory.payload.response.StatutorySectionOptionResponse;
import com.arktech.superaccountant.statutory.repository.StatutorySectionRepository;
import com.arktech.superaccountant.statutory.repository.StatutorySectionRuleRepository;
import com.arktech.superaccountant.tds.models.TdsSectionConfig;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Read access to the statutory section tables — the single source of section truth.
 * Global app configuration: the same options serve every company, so there is no
 * per-organization filtering and no write path here.
 */
@Service
public class StatutorySectionService {

    private final StatutorySectionRepository statutorySectionRepository;

    private final StatutorySectionRuleRepository statutorySectionRuleRepository;

    public StatutorySectionService(StatutorySectionRepository statutorySectionRepository,
                                   StatutorySectionRuleRepository statutorySectionRuleRepository) {
        this.statutorySectionRepository = statutorySectionRepository;
        this.statutorySectionRuleRepository = statutorySectionRuleRepository;
    }

    /**
     * Active Income-tax sections keyed by their internal code, in display order.
     * The legacy stored 194I (pre-split rent) resolves to the land/building half,
     * mirroring the engine's config alias.
     */
    public Map<String, StatutorySection> activeIncomeTaxSections() {
        Map<String, StatutorySection> sections = new LinkedHashMap<>();
        for (StatutorySection section
                : statutorySectionRepository.findByStatuteOrderBySortOrderAscCodeAsc(Statute.INCOME_TAX)) {
            if (Boolean.TRUE.equals(section.getActive())) {
                sections.put(section.getCode(), section);
            }
        }
        if (!sections.containsKey("194I") && sections.containsKey("194I_B")) {
            sections.put("194I", sections.get("194I_B"));
        }
        return sections;
    }

    /**
     * The section options a TDS picker offers: identity and RPU codes from
     * statutory_sections, the legal reference from the section's dated rule rows —
     * the DEFAULT row covering the new-Act effective date (1 April 2026), which is
     * the s.393 row when the section has one.
     */
    @Transactional
    public List<StatutorySectionOptionResponse> tdsSectionOptions() {
        Map<UUID, List<StatutorySectionRule>> rulesBySectionId = new LinkedHashMap<>();
        for (StatutorySectionRule rule : statutorySectionRuleRepository.findAll()) {
            rulesBySectionId.computeIfAbsent(rule.getSectionId(), k -> new ArrayList<>()).add(rule);
        }
        List<StatutorySectionOptionResponse> options = new ArrayList<>();
        for (StatutorySection section
                : statutorySectionRepository.findByStatuteOrderBySortOrderAscCodeAsc(Statute.INCOME_TAX)) {
            if (!Boolean.TRUE.equals(section.getActive())) {
                continue;
            }
            options.add(new StatutorySectionOptionResponse(
                    section.getCode(),
                    section.getDescription(),
                    legalReference(rulesBySectionId.get(section.getId()), section),
                    section.getRpu26qCode(),
                    section.getRpu140Code(),
                    section.getSortOrder()));
        }
        return options;
    }

    /**
     * The reference to show with a section code, taken from the section's dated rule
     * rows: the DEFAULT row covering the new-Act effective date (1 April 2026). When a
     * section has no new-Act row (e.g. 194M), that is the open-ended pre-2026 row whose
     * reference is the old-Act code itself; consumers suppress a reference equal to the
     * code when rendering labels. Null when the section has no rule rows.
     */
    private String legalReference(List<StatutorySectionRule> rules, StatutorySection section) {
        if (rules == null || rules.isEmpty()) {
            return null;
        }
        StatutorySectionRule rule = StatutorySectionRules.effectiveRule(
                rules, TdsSectionConfig.NEW_ACT_EFFECTIVE_DATE, StatutoryRuleContext.DEFAULT);
        return rule == null ? null : rule.getLegalReference();
    }
}
