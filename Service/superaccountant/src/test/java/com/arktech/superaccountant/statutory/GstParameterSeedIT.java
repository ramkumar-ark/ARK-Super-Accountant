package com.arktech.superaccountant.statutory;

import com.arktech.superaccountant.login.config.DataMigrationService;
import com.arktech.superaccountant.statutory.models.Statute;
import com.arktech.superaccountant.statutory.models.StatutorySection;
import com.arktech.superaccountant.statutory.models.StatutorySectionRule;
import com.arktech.superaccountant.statutory.repository.StatutorySectionRepository;
import com.arktech.superaccountant.statutory.repository.StatutorySectionRuleRepository;
import com.arktech.superaccountant.statutory.services.GstParameterService;
import com.arktech.superaccountant.statutory.services.GstParameters;
import com.arktech.superaccountant.statutory.services.GstStatutorySeed;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Boots the app (DataInitializer seeds the GST rows) and reads them back through the shared lookup. */
@SpringBootTest
@Testcontainers
@TestPropertySource(properties = "JWT_SECRET=test-jwt-secret-must-be-at-least-32-characters-long")
class GstParameterSeedIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private GstParameterService service;
    @Autowired
    private DataMigrationService migrationService;
    @Autowired
    private StatutorySectionRepository sectionRepository;
    @Autowired
    private StatutorySectionRuleRepository ruleRepository;

    @Test
    void seededRowsResolveThroughTheSharedLookupWithCitations() {
        GstParameters parameters = service.current();
        assertEquals(0, new BigDecimal("250000").compareTo(parameters.b2clThreshold(LocalDate.of(2024, 7, 31))));
        assertEquals(0, new BigDecimal("100000").compareTo(parameters.b2clThreshold(LocalDate.of(2024, 8, 1))));
        assertEquals(0, new BigDecimal("5000000").compareTo(parameters.iffCap(LocalDate.of(2026, 3, 1))));
        assertTrue(parameters.isAllowedRate(new BigDecimal("18"), LocalDate.of(2026, 3, 1)));
        assertFalse(parameters.isAllowedRate(new BigDecimal("6"), LocalDate.of(2026, 3, 1)));

        StatutorySection b2cl = sectionRepository.findByStatuteAndCode(Statute.CGST, "B2CL_THRESHOLD").orElseThrow();
        for (StatutorySectionRule rule : ruleRepository.findBySectionId(b2cl.getId())) {
            assertNotNull(rule.getSourceCitation());
            assertFalse(rule.getSourceCitation().isBlank());
        }
    }

    @Test
    void seedingIsIdempotent() {
        long sections = sectionRepository.count();
        long rules = ruleRepository.count();
        assertEquals(0, migrationService.seedGstParameters());
        assertEquals(sections, sectionRepository.count());
        assertEquals(rules, ruleRepository.count());
        assertTrue(GstStatutorySeed.ROWS.size() > 0);
    }

    @Test
    void gstPseudoSectionsDoNotLeakIntoTdsSections() {
        assertTrue(sectionRepository.findByStatuteOrderBySortOrderAscCodeAsc(Statute.INCOME_TAX).stream()
                .noneMatch(s -> s.getCode().startsWith("B2CL") || s.getCode().startsWith("GST_")));
    }
}
