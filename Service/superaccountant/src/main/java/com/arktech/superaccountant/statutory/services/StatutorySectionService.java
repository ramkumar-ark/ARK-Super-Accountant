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

import java.time.LocalDate;
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
        Map<UUID, List<StatutorySectionRule>> rulesBySectionId = rulesBySectionId();
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
     * True when an active INCOME_TAX section row with this exact code is stored in
     * statutory_sections (no 194I alias — the legacy pre-split code is not a stored
     * section and masters must not carry it).
     */
    public boolean isActiveIncomeTaxSection(String code) {
        return code != null && statutorySectionRepository.existsByStatuteAndCodeAndActiveTrue(
                Statute.INCOME_TAX, code);
    }

    /**
     * The s.393-era legal reference per active section code: the DEFAULT rule row
     * covering the new-Act effective date (1 April 2026). Sections whose covering
     * rule's reference is the code itself (e.g. 194M, which has no s.393 row) are
     * omitted, and the legacy stored 194I alias resolves to its land/building half —
     * mirroring the deleted TdsSectionRefs map.
     */
    public Map<String, String> newActRefsByCode() {
        Map<UUID, List<StatutorySectionRule>> rulesBySectionId = rulesBySectionId();
        Map<String, String> refs = new LinkedHashMap<>();
        for (StatutorySection section
                : statutorySectionRepository.findByStatuteOrderBySortOrderAscCodeAsc(Statute.INCOME_TAX)) {
            if (!Boolean.TRUE.equals(section.getActive())) {
                continue;
            }
            String ref = legalReference(rulesBySectionId.get(section.getId()), section);
            if (ref != null && !ref.equals(section.getCode())) {
                refs.put(section.getCode(), ref);
            }
        }
        if (!refs.containsKey("194I") && refs.containsKey("194I_B")) {
            refs.put("194I", refs.get("194I_B"));
        }
        return refs;
    }

    /**
     * The reference to show for a known TDS date: the s.393 reference on/after 1 April
     * 2026, otherwise the old section code. Null dates fall back to the code.
     */
    public static String displayRef(Map<String, String> newActRefsByCode, String code, LocalDate tdsDate) {
        if (code == null) {
            return null;
        }
        String ref = newActRefsByCode.get(code);
        return ref != null && tdsDate != null && TdsSectionConfig.newActApplies(tdsDate) ? ref : code;
    }

    /** Old section first, then the s.393 reference, for places where no period is known. */
    public static String displayWithRef(Map<String, String> newActRefsByCode, String code) {
        if (code == null) {
            return null;
        }
        String ref = newActRefsByCode.get(code);
        return ref == null ? code : code + " / " + ref;
    }

    private Map<UUID, List<StatutorySectionRule>> rulesBySectionId() {
        Map<UUID, List<StatutorySectionRule>> rulesBySectionId = new LinkedHashMap<>();
        for (StatutorySectionRule rule : statutorySectionRuleRepository.findAll()) {
            rulesBySectionId.computeIfAbsent(rule.getSectionId(), k -> new ArrayList<>()).add(rule);
        }
        return rulesBySectionId;
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
