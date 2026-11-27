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
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    /** Sections whose dated rows were corrected to the Finance Act 2025 text after the legacy seed. */
    private static final java.util.Set<String> FINANCE_ACT_2025_CORRECTED =
            java.util.Set.of("194I_A", "194I_B", "194J_A", "194J_B", "194Q");

    /** Expected s.393 legal references, hard-coded so a seed/data change cannot self-confirm. */
    private static final Map<String, String> EXPECTED_S393_REFS = Map.ofEntries(
            Map.entry("194M", "393(1) [Table: Sl. No. 6(ii)]"),
            Map.entry("194A", "393(1) [Table: Sl. No. 5(ii) and 5(iii)]"),
            Map.entry("194C", "393(1) [Table: Sl. No. 6(i)]"),
            Map.entry("194H", "393(1) [Table: Sl. No. 1(ii)]"),
            Map.entry("194I_A", "393(1) [Table: Sl. No. 2(ii).D(a)]"),
            Map.entry("194I_B", "393(1) [Table: Sl. No. 2(ii).D(b)]"),
            Map.entry("194J_A", "393(1) [Table: Sl. No. 6(iii).D(a)]"),
            Map.entry("194J_B", "393(1) [Table: Sl. No. 6(iii).D(b)]"),
            Map.entry("194J_C", "393(1) [Table: Sl. No. 6(iii).D(b)]"),
            Map.entry("194Q", "393(1) [Table: Sl. No. 8(ii)]"));

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
            if (FINANCE_ACT_2025_CORRECTED.contains(config.getSectionCode())) {
                // The frozen legacy columns predate the Finance Act 2025 corrections, so the dated
                // rows intentionally differ; afterBoot_seededRuleValuesMatchTheLegacyLiterals and
                // afterBoot_financeAct2025SplitKeepsPreAmendmentValues pin these sections instead.
                continue;
            }
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
            if (config.getNewActRateCompany() != null || "194M".equals(code)) {
                assertEquals(EXPECTED_S393_REFS.get(code), newRow.getLegalReference(),
                        "new regime shows the s.393 table reference for " + code);
                assertEquals("Income-tax Act 2025 s.393", newRow.getSourceCitation());
                assertEquals(NEW_FROM, newRow.getEffectiveFrom());
                assertNull(newRow.getEffectiveTo(), "the s.393 row stays open-ended");
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
     * Seed-time tripwire: the rule values in force on 31 March 2026 (Finance Act 2025 rows) and
     * from 1 April 2026 (s.393 rows) for every section, against hard-coded literals so a seed typo
     * cannot pass just because both sides moved together.
     */
    @Test
    void afterBoot_seededRuleValuesMatchTheLegacyLiterals() {
        // annual null = no annual test (rent uses the monthly threshold instead)
        record Vals(String rateCompany, String rateNonCompany, String perTxn, String annual, String monthly,
                    String noPan, boolean excessOnly) {}
        Map<String, Vals> fa2025 = Map.ofEntries(
                Map.entry("194A", new Vals("10.00", "10.00", null, "10000.00", null, "20.00", false)),
                Map.entry("194C", new Vals("2.00", "1.00", "30000.00", "100000.00", null, "20.00", false)),
                Map.entry("194H", new Vals("2.00", "2.00", null, "20000.00", null, "20.00", false)),
                Map.entry("194I_A", new Vals("2.00", "2.00", null, null, "50000.00", "20.00", false)),
                Map.entry("194I_B", new Vals("10.00", "10.00", null, null, "50000.00", "20.00", false)),
                Map.entry("194J_A", new Vals("2.00", "2.00", null, "50000.00", null, "20.00", false)),
                Map.entry("194J_B", new Vals("10.00", "10.00", null, "50000.00", null, "20.00", false)),
                Map.entry("194J_C", new Vals("10.00", "10.00", null, "0.00", null, "20.00", false)),
                Map.entry("194M", new Vals("2.00", "2.00", null, "5000000.00", null, "20.00", false)),
                Map.entry("194Q", new Vals("0.10", "0.10", null, "5000000.00", null, "20.00", true)),
                Map.entry("194D", new Vals("10.00", "2.00", null, "20000.00", null, "20.00", false)),
                Map.entry("193", new Vals("10.00", "10.00", null, "10000.00", null, "20.00", false)),
                Map.entry("194T", new Vals("10.00", "10.00", null, "20000.00", null, "20.00", false)));
        Map<String, Vals> newAct = Map.ofEntries(
                Map.entry("194A", new Vals("10.00", "10.00", null, "10000.00", null, "20.00", false)),
                Map.entry("194C", new Vals("2.00", "1.00", "30000.00", "100000.00", null, "20.00", false)),
                Map.entry("194H", new Vals("2.00", "2.00", null, "20000.00", null, "20.00", false)),
                Map.entry("194I_A", new Vals("2.00", "2.00", null, null, "50000.00", "20.00", false)),
                Map.entry("194I_B", new Vals("10.00", "10.00", null, null, "50000.00", "20.00", false)),
                Map.entry("194J_A", new Vals("2.00", "2.00", null, "50000.00", null, "20.00", false)),
                Map.entry("194J_B", new Vals("10.00", "10.00", null, "50000.00", null, "20.00", false)),
                Map.entry("194J_C", new Vals("10.00", "10.00", null, "0.00", null, "20.00", false)),
                Map.entry("194M", new Vals("2.00", "2.00", null, "5000000.00", null, "20.00", false)),
                Map.entry("194Q", new Vals("0.10", "0.10", null, "5000000.00", null, "5.00", true)),
                Map.entry("194D", new Vals("10.00", "2.00", null, "20000.00", null, "20.00", false)),
                Map.entry("193", new Vals("10.00", "10.00", null, "10000.00", null, "20.00", false)),
                Map.entry("194T", new Vals("10.00", "10.00", null, "20000.00", null, "20.00", false)));

        Map<String, List<StatutorySectionRule>> rulesByCode = rulesByCode();
        assertEquals(fa2025.keySet(), rulesByCode.keySet(), "seeded sections");

        for (var entry : rulesByCode.entrySet()) {
            String code = entry.getKey();
            List<StatutorySectionRule> rules = entry.getValue();
            assertRow(code + " FA2025", fa2025.get(code).rateCompany(), fa2025.get(code).rateNonCompany(),
                    fa2025.get(code).perTxn(), fa2025.get(code).annual(), fa2025.get(code).monthly(),
                    fa2025.get(code).noPan(), fa2025.get(code).excessOnly(),
                    StatutorySectionRules.effectiveRule(rules, OLD_TO, null));
            assertRow(code + " new Act", newAct.get(code).rateCompany(), newAct.get(code).rateNonCompany(),
                    newAct.get(code).perTxn(), newAct.get(code).annual(), newAct.get(code).monthly(),
                    newAct.get(code).noPan(), newAct.get(code).excessOnly(),
                    StatutorySectionRules.effectiveRule(rules, NEW_FROM, null));
        }
    }

    private static void assertRow(String label, String rateCompany, String rateNonCompany, String perTxn,
                                  String annual, String monthly, String noPan, boolean excessOnly,
                                  StatutorySectionRule row) {
        assertEquals(0, new BigDecimal(rateCompany).compareTo(row.getRateCompany()), label + " rateCompany");
        assertEquals(0, new BigDecimal(rateNonCompany).compareTo(row.getRateNonCompany()),
                label + " rateNonCompany");
        assertEquals(perTxn == null ? null : new BigDecimal(perTxn), row.getThresholdPerTransaction(),
                label + " perTxn");
        if (annual == null) {
            assertNull(row.getThresholdAnnual(), label + " annual");
        } else {
            assertEquals(0, new BigDecimal(annual).compareTo(row.getThresholdAnnual()), label + " annual");
        }
        if (monthly == null) {
            assertNull(row.getThresholdMonthly(), label + " monthly");
        } else {
            assertEquals(0, new BigDecimal(monthly).compareTo(row.getThresholdMonthly()), label + " monthly");
        }
        assertEquals(0, new BigDecimal(noPan).compareTo(row.getHigherRateIfNoPan()), label + " noPan");
        assertEquals(excessOnly, Boolean.TRUE.equals(row.getThresholdExcessOnly()), label + " excessOnly");
    }

    /** The Finance Act 2025 split keeps the pre-amendment threshold on the row ending 31 March 2025. */
    @Test
    void afterBoot_financeAct2025SplitKeepsPreAmendmentValues() {
        LocalDate beforeFa = Tds194aRules.FINANCE_ACT_2025_EFFECTIVE.minusDays(1);
        Map<String, List<StatutorySectionRule>> rulesByCode = rulesByCode();
        Map<String, String> preAmendmentAnnual = Map.of(
                "194H", "15000.00", "194I_A", "240000.00", "194I_B", "240000.00",
                "194J_A", "30000.00", "194J_B", "30000.00");
        for (var e : preAmendmentAnnual.entrySet()) {
            StatutorySectionRule row = StatutorySectionRules.effectiveRule(
                    rulesByCode.get(e.getKey()), beforeFa, null);
            assertEquals(0, new BigDecimal(e.getValue()).compareTo(row.getThresholdAnnual()),
                    e.getKey() + " pre-FA2025 annual");
            assertNull(row.getThresholdMonthly(), e.getKey() + " has no monthly test before FA 2025");
            assertEquals(e.getKey(), row.getLegalReference());
        }
        // The new sections are only seeded from the date their rates are verified for.
        for (String code : List.of("194D", "193", "194T")) {
            List<StatutorySectionRule> rows = rulesByCode.get(code);
            assertEquals(2, rows.size(), code + " rows");
            assertTrue(rows.stream().allMatch(r -> !r.getEffectiveFrom().isBefore(
                    Tds194aRules.FINANCE_ACT_2025_EFFECTIVE)));
        }
    }

    @Test
    void afterBoot_newSectionsCarryVerifiedReturnCodesAndReferences() {
        record Codes(String code26q, String code140, String ref) {}
        Map<String, Codes> expected = Map.of(
                "194D", new Codes("94D", "1005", "393(1) [Table: Sl. No. 1(i)]"),
                "193", new Codes("193", "1019", "393(1) [Table: Sl. No. 5(i)]"),
                "194T", new Codes("94T", "1067", "393(3) [Table: Sl. No. 7]"));
        Map<String, List<StatutorySectionRule>> rulesByCode = rulesByCode();
        for (var e : expected.entrySet()) {
            StatutorySection section = statutorySectionRepository
                    .findByStatuteAndCode(Statute.INCOME_TAX, e.getKey()).orElseThrow();
            assertEquals(e.getValue().code26q(), section.getRpu26qCode(), e.getKey() + " 26Q");
            assertEquals(e.getValue().code140(), section.getRpu140Code(), e.getKey() + " 140");
            assertFalse(section.getRpu140PayeeSplit(), e.getKey() + " payee split");
            assertTrue(section.getActive());
            assertEquals(e.getValue().ref(),
                    StatutorySectionRules.effectiveRule(rulesByCode.get(e.getKey()), NEW_FROM, null)
                            .getLegalReference());
            assertEquals(e.getKey(), StatutorySectionRules.effectiveRule(
                    rulesByCode.get(e.getKey()), OLD_TO, null).getLegalReference());
        }
        assertTrue(statutorySectionRepository.findByStatuteAndCode(Statute.INCOME_TAX, "194B").isEmpty(),
                "194B stays out of the picker");
    }

    @Test
    void backfill_isIdempotent() {        long sectionsBefore = statutorySectionRepository.count();
        long rulesBefore = statutorySectionRuleRepository.count();

        int created = dataMigrationService.backfillStatutorySections();

        assertEquals(0, created, "a second backfill must create nothing");
        assertEquals(0, dataMigrationService.seedFinanceAct2025Sections(), "new sections seed once");
        assertEquals(0, dataMigrationService.applyFinanceAct2025RuleCorrections(), "corrections apply once");
        assertEquals(sectionsBefore, statutorySectionRepository.count());
        assertEquals(rulesBefore, statutorySectionRuleRepository.count());
    }
}
