package com.arktech.superaccountant.statutory;

import com.arktech.superaccountant.login.config.DataMigrationService;
import com.arktech.superaccountant.statutory.models.Statute;
import com.arktech.superaccountant.statutory.models.StatutorySection;
import com.arktech.superaccountant.statutory.models.StatutorySectionRule;
import com.arktech.superaccountant.statutory.models.StatutorySectionRules;
import com.arktech.superaccountant.statutory.repository.StatutorySectionRepository;
import com.arktech.superaccountant.statutory.repository.StatutorySectionRuleRepository;
import com.arktech.superaccountant.tds.models.Tds194aRules;
import com.arktech.superaccountant.tds.models.TdsSectionConfig;
import com.arktech.superaccountant.tds.repository.TdsSectionConfigRepository;
import com.arktech.superaccountant.tds.rpu.Rpu140FormatSpec;
import com.arktech.superaccountant.tds.rpu.RpuFormatSpec;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Boots the full application (DataInitializer runs the statutory backfill) and proves
 * the seeded dated rule rows reproduce the legacy two-regime resolution exactly, for
 * every section and both Acts.
 */
@SpringBootTest
@Testcontainers
@TestPropertySource(properties = "JWT_SECRET=test-jwt-secret-must-be-at-least-32-characters-long")
class StatutorySectionBackfillIT {

    private static final LocalDate OLD_TO = LocalDate.of(2026, 3, 31);
    private static final LocalDate NEW_FROM = LocalDate.of(2026, 4, 1);

    /** Expected s.393 legal references, hard-coded so a seed/data change cannot self-confirm. */
    private static final Map<String, String> EXPECTED_S393_REFS = Map.of(
            "194A", "393(1) [Table: Sl. No. 5(ii) and 5(iii)]",
            "194C", "393(1) [Table: Sl. No. 6(i)]",
            "194H", "393(1) [Table: Sl. No. 1(ii)]",
            "194I_A", "393(1) [Table: Sl. No. 2(ii).D(a)]",
            "194I_B", "393(1) [Table: Sl. No. 2(ii).D(b)]",
            "194J_A", "393(1) [Table: Sl. No. 6(iii).D(a)]",
            "194J_B", "393(1) [Table: Sl. No. 6(iii).D(b)]",
            "194J_C", "393(1) [Table: Sl. No. 6(iii).D(b)]",
            "194Q", "393(1) [Table: Sl. No. 8(ii)]");

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private TdsSectionConfigRepository tdsSectionConfigRepository;

    @Autowired
    private StatutorySectionRepository statutorySectionRepository;

    @Autowired
    private StatutorySectionRuleRepository statutorySectionRuleRepository;

    @Autowired
    private DataMigrationService dataMigrationService;

    /** Mirrors TdsAnalysisInputFactory.attachStatutoryRules. */
    private Map<String, List<StatutorySectionRule>> rulesByCode() {
        Map<String, List<StatutorySectionRule>> byCode = new LinkedHashMap<>();
        for (StatutorySection section : statutorySectionRepository.findByStatute(Statute.INCOME_TAX)) {
            List<StatutorySectionRule> rules = statutorySectionRuleRepository.findBySectionId(section.getId());
            if (!rules.isEmpty()) {
                byCode.put(section.getCode(), rules);
            }
        }
        return byCode;
    }

    private static void assertSameValues(TdsSectionConfig expected, TdsSectionConfig actual) {
        assertEquals(expected.getRateCompany(), actual.getRateCompany(), "rateCompany");
        assertEquals(expected.getRateNonCompany(), actual.getRateNonCompany(), "rateNonCompany");
        assertEquals(expected.getThresholdPerTransaction(), actual.getThresholdPerTransaction(),
                "thresholdPerTransaction");
        assertEquals(expected.getThresholdAnnual(), actual.getThresholdAnnual(), "thresholdAnnual");
        assertEquals(expected.getHigherRateIfNoPan(), actual.getHigherRateIfNoPan(), "higherRateIfNoPan");
    }

    @Test
    void afterBoot_everyConfigHasASectionWithMirroredRpuCodes() {
        Map<String, StatutorySection> sectionsByCode = new LinkedHashMap<>();
        for (StatutorySection s : statutorySectionRepository.findByStatute(Statute.INCOME_TAX)) {
            sectionsByCode.put(s.getCode(), s);
        }

        for (TdsSectionConfig config : tdsSectionConfigRepository.findAll()) {
            StatutorySection section = sectionsByCode.get(config.getSectionCode());
            assertNotNull(section, "section " + config.getSectionCode() + " must exist after backfill");
            assertEquals(RpuFormatSpec.rpu26qCode(config.getSectionCode()), section.getRpu26qCode(),
                    "26Q code for " + config.getSectionCode());
            assertEquals(Rpu140FormatSpec.rpu140Code(config.getSectionCode()), section.getRpu140Code(),
                    "140 code for " + config.getSectionCode());
            assertEquals("194C".equals(config.getSectionCode()), section.getRpu140PayeeSplit(),
                    "payee-split flag for " + config.getSectionCode());
            assertTrue(section.getActive() && section.getSeeded());
        }
    }

    @Test
    void afterBoot_datedRulesReproduceLegacyResolutionForBothRegimes() {
        Map<String, List<StatutorySectionRule>> rulesByCode = rulesByCode();

        for (TdsSectionConfig config : tdsSectionConfigRepository.findAll()) {
            config.setDatedRules(rulesByCode.get(config.getSectionCode()));
            assertNotNull(config.getDatedRules(),
                    "dated rule rows must exist for " + config.getSectionCode());

            assertSameValues(config.effectiveForFromColumns(OLD_TO), config.effectiveFor(OLD_TO));
            assertSameValues(config.effectiveForFromColumns(NEW_FROM), config.effectiveFor(NEW_FROM));
        }
    }

    @Test
    void afterBoot_legalReferenceFollowsThePeriod() {
        Map<String, List<StatutorySectionRule>> rulesByCode = rulesByCode();

        for (TdsSectionConfig config : tdsSectionConfigRepository.findAll()) {
            List<StatutorySectionRule> rules = rulesByCode.get(config.getSectionCode());
            String code = config.getSectionCode();

            StatutorySectionRule oldRow = StatutorySectionRules.effectiveRule(rules, OLD_TO, null);
            assertEquals(code, oldRow.getLegalReference(),
                    "old regime keeps the 1961 Act section code as its reference");

            StatutorySectionRule newRow = StatutorySectionRules.effectiveRule(rules, NEW_FROM, null);
            if (config.getNewActRateCompany() != null) {
                assertEquals(EXPECTED_S393_REFS.get(code), newRow.getLegalReference(),
                        "new regime shows the s.393 table reference for " + code);
                assertEquals("Income-tax Act 2025 s.393", newRow.getSourceCitation());
                assertEquals(NEW_FROM, newRow.getEffectiveFrom());
                assertNull(newRow.getEffectiveTo(), "the s.393 row stays open-ended");
            } else {
                // 194M has no s.393 row; the old regime row is open-ended.
                assertEquals(code, newRow.getLegalReference());
                assertNull(newRow.getEffectiveTo());
            }
        }
    }

    @Test
    void afterBoot_194aContextRowsMatchTds194aRules() {
        Map<String, List<StatutorySectionRule>> rulesByCode = rulesByCode();
        List<StatutorySectionRule> rules = rulesByCode.get("194A");
        LocalDate fa2025 = Tds194aRules.FINANCE_ACT_2025_EFFECTIVE;

        assertEquals(0, Tds194aRules.OLD_ACT_BANK_THRESHOLD_ANNUAL.compareTo(
                StatutorySectionRules.effectiveRule(rules, fa2025.minusDays(1), "BANK").getThresholdAnnual()));
        assertEquals(0, Tds194aRules.BANK_THRESHOLD_ANNUAL.compareTo(
                StatutorySectionRules.effectiveRule(rules, fa2025, "BANK").getThresholdAnnual()));
        assertEquals(0, Tds194aRules.BANK_THRESHOLD_ANNUAL.compareTo(
                StatutorySectionRules.effectiveRule(rules, NEW_FROM, "BANK").getThresholdAnnual()));
        assertEquals("393(1) [Table: Sl. No. 5(ii)]",
                StatutorySectionRules.effectiveRule(rules, NEW_FROM, "BANK").getLegalReference());

        assertEquals(0, Tds194aRules.OLD_ACT_OTHER_THRESHOLD_ANNUAL.compareTo(
                StatutorySectionRules.effectiveRule(rules, fa2025.minusDays(1), "OTHER_PAYER").getThresholdAnnual()));
        assertEquals(0, Tds194aRules.OTHER_THRESHOLD_ANNUAL.compareTo(
                StatutorySectionRules.effectiveRule(rules, fa2025, "OTHER_PAYER").getThresholdAnnual()));
        assertEquals(0, Tds194aRules.OTHER_THRESHOLD_ANNUAL.compareTo(
                StatutorySectionRules.effectiveRule(rules, NEW_FROM, "OTHER_PAYER").getThresholdAnnual()));
        assertEquals("393(1) [Table: Sl. No. 5(iii)]",
                StatutorySectionRules.effectiveRule(rules, NEW_FROM, "OTHER_PAYER").getLegalReference());

        assertEquals(0, Tds194aRules.SENIOR_CITIZEN_THRESHOLD_ANNUAL.compareTo(
                StatutorySectionRules.effectiveRule(rules, NEW_FROM, "SENIOR_CITIZEN").getThresholdAnnual()));
    }

    /**
     * Seed-time tripwire: the seeded rule values for all 10 sections, both regimes, against
     * hard-coded literals — so a seed typo cannot pass just because both sides moved together.
     */
    @Test
    void afterBoot_seededRuleValuesMatchTheLegacyLiterals() {
        record Vals(String rateCompany, String rateNonCompany, String perTxn, String annual) {}
        Map<String, Vals> oldAct = Map.of(
                "194A", new Vals("10.00", "10.00", null, "10000.00"),
                "194C", new Vals("2.00", "1.00", "30000.00", "100000.00"),
                "194H", new Vals("2.00", "2.00", null, "20000.00"),
                "194I_A", new Vals("2.00", "2.00", null, "240000.00"),
                "194I_B", new Vals("10.00", "10.00", null, "240000.00"),
                "194J_A", new Vals("2.00", "2.00", null, "30000.00"),
                "194J_B", new Vals("10.00", "10.00", null, "30000.00"),
                "194J_C", new Vals("10.00", "10.00", null, "0.00"),
                "194M", new Vals("2.00", "2.00", null, "5000000.00"),
                "194Q", new Vals("0.10", "0.10", null, "5000000.00"));
        Map<String, Vals> newAct = Map.of(
                "194A", new Vals("10.00", "10.00", null, "10000.00"),
                "194C", new Vals("2.00", "1.00", "30000.00", "100000.00"),
                "194H", new Vals("2.00", "2.00", null, "20000.00"),
                "194I_A", new Vals("2.00", "2.00", null, "600000.00"),
                "194I_B", new Vals("10.00", "10.00", null, "600000.00"),
                "194J_A", new Vals("2.00", "2.00", null, "50000.00"),
                "194J_B", new Vals("10.00", "10.00", null, "50000.00"),
                "194J_C", new Vals("10.00", "10.00", null, "0.00"),
                "194Q", new Vals("0.10", "0.10", null, "5000000.00"));

        Map<String, List<StatutorySectionRule>> rulesByCode = rulesByCode();
        assertEquals(oldAct.keySet(), rulesByCode.keySet(), "seeded sections");

        for (var entry : rulesByCode.entrySet()) {
            String code = entry.getKey();
            List<StatutorySectionRule> rules = entry.getValue();
            Vals oldVals = oldAct.get(code);
            StatutorySectionRule oldRow = StatutorySectionRules.effectiveRule(rules, OLD_TO, null);
            assertEquals(0, new BigDecimal(oldVals.rateCompany()).compareTo(oldRow.getRateCompany()),
                    code + " old rateCompany");
            assertEquals(0, new BigDecimal(oldVals.rateNonCompany()).compareTo(oldRow.getRateNonCompany()),
                    code + " old rateNonCompany");
            assertEquals(oldVals.perTxn() == null ? null : new BigDecimal(oldVals.perTxn()),
                    oldRow.getThresholdPerTransaction(), code + " old perTxn");
            assertEquals(0, new BigDecimal(oldVals.annual()).compareTo(oldRow.getThresholdAnnual()),
                    code + " old annual");
            assertEquals(0, new BigDecimal("20.00").compareTo(oldRow.getHigherRateIfNoPan()),
                    code + " old higherRateIfNoPan");

            Vals newVals = newAct.get(code);
            if (newVals == null) {
                continue; // 194M has no s.393 row
            }
            StatutorySectionRule newRow = StatutorySectionRules.effectiveRule(rules, NEW_FROM, null);
            assertEquals(0, new BigDecimal(newVals.rateCompany()).compareTo(newRow.getRateCompany()),
                    code + " new rateCompany");
            assertEquals(0, new BigDecimal(newVals.rateNonCompany()).compareTo(newRow.getRateNonCompany()),
                    code + " new rateNonCompany");
            assertEquals(newVals.perTxn() == null ? null : new BigDecimal(newVals.perTxn()),
                    newRow.getThresholdPerTransaction(), code + " new perTxn");
            assertEquals(0, new BigDecimal(newVals.annual()).compareTo(newRow.getThresholdAnnual()),
                    code + " new annual");
            assertEquals(0, new BigDecimal("20.00").compareTo(newRow.getHigherRateIfNoPan()),
                    code + " new higherRateIfNoPan");
        }
    }

    @Test
    void backfill_isIdempotent() {        long sectionsBefore = statutorySectionRepository.count();
        long rulesBefore = statutorySectionRuleRepository.count();

        int created = dataMigrationService.backfillStatutorySections();

        assertEquals(0, created, "a second backfill must create nothing");
        assertEquals(sectionsBefore, statutorySectionRepository.count());
        assertEquals(rulesBefore, statutorySectionRuleRepository.count());
    }
}
