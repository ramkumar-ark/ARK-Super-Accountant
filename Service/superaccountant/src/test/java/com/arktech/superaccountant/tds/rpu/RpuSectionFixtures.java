package com.arktech.superaccountant.tds.rpu;

import com.arktech.superaccountant.statutory.models.Statute;
import com.arktech.superaccountant.statutory.models.StatutorySection;
import com.arktech.superaccountant.statutory.repository.StatutorySectionRepository;
import com.arktech.superaccountant.statutory.repository.StatutorySectionRuleRepository;
import com.arktech.superaccountant.statutory.services.StatutorySectionService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Statutory section fixtures mirroring the PR1 backfill: one active INCOME_TAX row per
 * seeded internal code, with the RPU codes the seed maps hold. Tests use these to drive
 * the table-backed RPU section-code lookups.
 */
public final class RpuSectionFixtures {

    private static final List<String> CODES = List.of(
            "194A", "194C", "194H", "194I", "194I_A", "194I_B",
            "194J_A", "194J_B", "194J_C", "194M", "194Q");

    private RpuSectionFixtures() {
    }

    public static Map<String, StatutorySection> incomeTaxSections() {
        Map<String, StatutorySection> sections = new LinkedHashMap<>();
        for (String internal : CODES) {
            StatutorySection section = new StatutorySection();
            section.setStatute(Statute.INCOME_TAX);
            section.setCode(internal);
            section.setRpu26qCode(RpuFormatSpec.rpu26qCode(internal));
            section.setRpu140Code(Rpu140FormatSpec.rpu140Code(internal));
            section.setRpu140PayeeSplit("194C".equals(internal));
            section.setActive(true);
            sections.put(internal, section);
        }
        return sections;
    }

    /** A real service backed by repositories stubbed to the fixture rows. */
    public static StatutorySectionService sectionService() {
        StatutorySectionRepository sectionRepository =
                org.mockito.Mockito.mock(StatutorySectionRepository.class);
        org.mockito.Mockito.when(sectionRepository.findByStatuteOrderBySortOrderAscCodeAsc(Statute.INCOME_TAX))
                .thenReturn(new ArrayList<>(incomeTaxSections().values()));
        StatutorySectionRuleRepository ruleRepository =
                org.mockito.Mockito.mock(StatutorySectionRuleRepository.class);
        org.mockito.Mockito.when(ruleRepository.findAll()).thenReturn(List.of());
        return new StatutorySectionService(sectionRepository, ruleRepository);
    }
}
