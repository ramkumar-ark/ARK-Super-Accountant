package com.arktech.superaccountant.statutory.services;

import com.arktech.superaccountant.statutory.models.Statute;
import com.arktech.superaccountant.statutory.models.StatutoryRuleContext;
import com.arktech.superaccountant.statutory.models.StatutorySection;
import com.arktech.superaccountant.statutory.models.StatutorySectionRule;
import com.arktech.superaccountant.statutory.payload.response.StatutorySectionOptionResponse;
import com.arktech.superaccountant.statutory.repository.StatutorySectionRepository;
import com.arktech.superaccountant.statutory.repository.StatutorySectionRuleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatutorySectionServiceTest {

    @Mock
    private StatutorySectionRepository statutorySectionRepository;

    @Mock
    private StatutorySectionRuleRepository statutorySectionRuleRepository;

    private StatutorySectionService service;

    @BeforeEach
    void setUp() {
        service = new StatutorySectionService(statutorySectionRepository, statutorySectionRuleRepository);
    }

    private StatutorySection section(String code, Integer sortOrder, boolean active) {
        StatutorySection s = new StatutorySection();
        s.setId(UUID.randomUUID());
        s.setStatute(Statute.INCOME_TAX);
        s.setCode(code);
        s.setDescription(code + " description");
        s.setRpu26qCode("26Q-" + code);
        s.setRpu140Code("140-" + code);
        s.setSortOrder(sortOrder);
        s.setActive(active);
        return s;
    }

    private StatutorySectionRule rule(UUID sectionId, String legalReference,
                                      LocalDate from, LocalDate to) {
        StatutorySectionRule r = new StatutorySectionRule();
        r.setSectionId(sectionId);
        r.setRuleContext(StatutoryRuleContext.DEFAULT);
        r.setRateCompany(new BigDecimal("2.00"));
        r.setRateNonCompany(new BigDecimal("1.00"));
        r.setThresholdAnnual(new BigDecimal("100000.00"));
        r.setLegalReference(legalReference);
        r.setEffectiveFrom(from);
        r.setEffectiveTo(to);
        return r;
    }

    @Test
    void tdsSectionOptions_returnsActiveSectionsInSortOrderWithRefsAndRpuCodes() {
        StatutorySection c = section("194C", 2, true);
        StatutorySection a = section("194A", 1, true);
        StatutorySection hidden = section("194Z", 3, false);
        when(statutorySectionRepository.findByStatuteOrderBySortOrderAscCodeAsc(Statute.INCOME_TAX))
                .thenReturn(List.of(a, c, hidden));
        when(statutorySectionRuleRepository.findAll()).thenReturn(List.of(
                rule(a.getId(), "194A", LocalDate.of(2000, 4, 1), LocalDate.of(2026, 3, 31)),
                rule(a.getId(), "393(1) [Table: Sl. No. 5(ii) and 5(iii)]",
                        LocalDate.of(2026, 4, 1), null),
                rule(c.getId(), "194C", LocalDate.of(2000, 4, 1), LocalDate.of(2026, 3, 31)),
                rule(c.getId(), "393(1) [Table: Sl. No. 6(i)]",
                        LocalDate.of(2026, 4, 1), null)));

        List<StatutorySectionOptionResponse> options = service.tdsSectionOptions();

        assertEquals(2, options.size());
        assertEquals("194A", options.get(0).getCode());
        assertEquals("393(1) [Table: Sl. No. 5(ii) and 5(iii)]", options.get(0).getLegalReference());
        assertEquals("26Q-194A", options.get(0).getRpu26qCode());
        assertEquals("140-194A", options.get(0).getRpu140Code());
        assertEquals("194C", options.get(1).getCode());
        assertEquals("393(1) [Table: Sl. No. 6(i)]", options.get(1).getLegalReference());
        assertEquals(2, options.get(1).getSortOrder());
    }

    @Test
    void tdsSectionOptions_sectionWithoutNewActRowKeepsOldRowReference() {
        StatutorySection m = section("194M", 1, true);
        when(statutorySectionRepository.findByStatuteOrderBySortOrderAscCodeAsc(Statute.INCOME_TAX))
                .thenReturn(List.of(m));
        when(statutorySectionRuleRepository.findAll()).thenReturn(List.of(
                rule(m.getId(), "194M", LocalDate.of(2000, 4, 1), null)));

        List<StatutorySectionOptionResponse> options = service.tdsSectionOptions();

        assertEquals("194M", options.get(0).getLegalReference());
    }

    @Test
    void tdsSectionOptions_sectionWithoutRuleRowsHasNullReference() {
        StatutorySection x = section("194X", 1, true);
        when(statutorySectionRepository.findByStatuteOrderBySortOrderAscCodeAsc(Statute.INCOME_TAX))
                .thenReturn(List.of(x));
        when(statutorySectionRuleRepository.findAll()).thenReturn(List.of());

        assertNull(service.tdsSectionOptions().get(0).getLegalReference());
    }

    @Test
    void activeIncomeTaxSections_aliasesLegacy194IToBuildingHalf() {
        StatutorySection ib = section("194I_B", 1, true);
        when(statutorySectionRepository.findByStatuteOrderBySortOrderAscCodeAsc(Statute.INCOME_TAX))
                .thenReturn(List.of(ib));

        Map<String, StatutorySection> sections = service.activeIncomeTaxSections();

        assertSame(sections.get("194I_B"), sections.get("194I"));
        assertEquals(2, sections.size());
    }
}
