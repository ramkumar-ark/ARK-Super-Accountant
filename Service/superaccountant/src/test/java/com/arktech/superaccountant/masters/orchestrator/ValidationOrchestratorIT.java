package com.arktech.superaccountant.masters.orchestrator;

import com.arktech.superaccountant.masters.classifier.ParsedLedger;
import com.arktech.superaccountant.masters.models.*;
import com.arktech.superaccountant.masters.rules.GstinPresenceRule;
import com.arktech.superaccountant.masters.rules.TdsSectionMappingRule;
import com.arktech.superaccountant.masters.rules.ValidationContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@TestPropertySource(properties = "JWT_SECRET=test-jwt-secret-must-be-at-least-32-characters-long")
class ValidationOrchestratorIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private TdsSectionMappingRule tdsSectionMappingRule;

    @Autowired
    private GstinPresenceRule gstinPresenceRule;

    private PreconfiguredMaster master(String name, LedgerCategory category,
                                       String tdsSection, String gstin) {
        PreconfiguredMaster m = new PreconfiguredMaster();
        m.setLedgerName(name);
        m.setCategory(category);
        m.setTdsSection(tdsSection);
        m.setGstin(gstin);
        m.setActive(true);
        return m;
    }

    @Test
    void validate_fixtureWith3TdsGaps_produces3TdsSectionMappingFindings() {
        List<PreconfiguredMaster> masters = List.of(
                master("Test Vendor A",  LedgerCategory.PURCHASE, null, null),
                master("Test Expense B", LedgerCategory.EXPENSE,  null, null),
                master("Test Income C",  LedgerCategory.INCOME,   null, null),
                master("Test Capital D", LedgerCategory.OTHER,    "NOT_SUBJECT", null)
        );

        ValidationContext ctx = new ValidationContext(
                UUID.randomUUID(), "testuser", masters, Map.of()
        );

        List<ValidationFinding> findings = tdsSectionMappingRule.execute(ctx, List.of());

        assertThat(findings).hasSize(3);
        assertThat(findings).extracting(ValidationFinding::getRuleCode)
                .containsOnly("TDS_SECTION_MAPPING");
        assertThat(findings).allMatch(f ->
                f.getSeverity() == FindingSeverity.HIGH ||
                f.getSeverity() == FindingSeverity.MEDIUM ||
                f.getSeverity() == FindingSeverity.LOW
        );
    }

    @Test
    void validate_fixtureWith2GstinGaps_produces2GstinPresenceFindings() {
        List<PreconfiguredMaster> masters = List.of(
                master("Test Vendor A", LedgerCategory.PURCHASE, "194Q", null),
                master("Test Vendor B", LedgerCategory.PURCHASE, "194Q", null),
                master("Test Income C", LedgerCategory.INCOME,   "NOT_SUBJECT", "29ABCDE1234F1Z5")
        );

        ValidationContext ctx = new ValidationContext(
                UUID.randomUUID(), "testuser", masters, Map.of()
        );

        List<ValidationFinding> findings = gstinPresenceRule.execute(ctx, List.of());

        assertThat(findings).hasSize(2);
        assertThat(findings).extracting(ValidationFinding::getRuleCode)
                .containsOnly("GSTIN_PRESENCE");
        assertThat(findings).allMatch(f -> f.getSeverity() == FindingSeverity.HIGH);
    }
}
