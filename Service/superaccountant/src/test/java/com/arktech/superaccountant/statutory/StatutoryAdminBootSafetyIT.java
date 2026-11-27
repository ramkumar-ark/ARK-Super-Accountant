package com.arktech.superaccountant.statutory;

import com.arktech.superaccountant.login.config.DataMigrationService;
import com.arktech.superaccountant.statutory.models.Statute;
import com.arktech.superaccountant.statutory.models.StatutorySection;
import com.arktech.superaccountant.statutory.models.StatutorySectionRule;
import com.arktech.superaccountant.statutory.repository.StatutorySectionRepository;
import com.arktech.superaccountant.statutory.repository.StatutorySectionRuleRepository;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The startup seed corrections must never rewrite rows an app administrator added:
 * they touch only seed rows (created_by null), so admin rule values survive a restart.
 */
@SpringBootTest
@Testcontainers
@TestPropertySource(properties = "JWT_SECRET=test-jwt-secret-must-be-at-least-32-characters-long")
class StatutoryAdminBootSafetyIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private DataMigrationService dataMigrationService;

    @Autowired
    private StatutorySectionRepository sectionRepository;

    @Autowired
    private StatutorySectionRuleRepository ruleRepository;

    private StatutorySectionRule adminRow(String code, LocalDate from) {
        StatutorySection section = sectionRepository
                .findByStatuteAndCode(Statute.INCOME_TAX, code).orElseThrow();
        StatutorySectionRule row = new StatutorySectionRule();
        row.setSectionId(section.getId());
        row.setRuleContext("DEFAULT");
        row.setRateCompany(new BigDecimal("2"));
        row.setRateNonCompany(new BigDecimal("1"));
        row.setHigherRateIfNoPan(new BigDecimal("20"));
        row.setThresholdExcessOnly(false);
        row.setEffectiveFrom(from);
        row.setSourceCitation("Admin correction 2027");
        row.setCreatedBy("admin-user");
        row.setUpdatedAt(java.time.Instant.now());
        return ruleRepository.save(row);
    }

    @Test
    void bootCorrectionsLeaveAdminRowsUnchanged() {
        StatutorySectionRule q194 = adminRow("194Q", LocalDate.of(2027, 4, 1));
        StatutorySectionRule i194 = adminRow("194I_A", LocalDate.of(2027, 4, 1));

        dataMigrationService.applyFinanceAct2025RuleCorrections();

        StatutorySectionRule q = ruleRepository.findById(q194.getId()).orElseThrow();
        assertThat(q.getThresholdExcessOnly()).isFalse();
        assertThat(q.getHigherRateIfNoPan()).isEqualByComparingTo("20");

        StatutorySectionRule i = ruleRepository.findById(i194.getId()).orElseThrow();
        assertThat(i.getThresholdMonthly()).isNull();
        assertThat(i.getThresholdAnnual()).isNull();
    }
}
