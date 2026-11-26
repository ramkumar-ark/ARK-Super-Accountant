package com.arktech.superaccountant.statutory.models;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class StatutorySectionRulesTest {

    private static final LocalDate OLD_TO = LocalDate.of(2026, 3, 31);
    private static final LocalDate NEW_FROM = LocalDate.of(2026, 4, 1);

    private final StatutorySection section = new StatutorySection();

    private StatutorySectionRule rule(String context, String annual,
                                      LocalDate from, LocalDate to) {
        StatutorySectionRule r = new StatutorySectionRule();
        r.setSectionId(section.getId());
        r.setRuleContext(context);
        r.setThresholdAnnual(annual == null ? null : new BigDecimal(annual));
        r.setRateCompany(new BigDecimal("10.00"));
        r.setRateNonCompany(new BigDecimal("10.00"));
        r.setHigherRateIfNoPan(new BigDecimal("20.00"));
        r.setEffectiveFrom(from);
        r.setEffectiveTo(to);
        return r;
    }

    @Test
    void boundaryDate_resolvesOldRowOn31MarchAndNewRowOn1April() {
        List<StatutorySectionRule> rows = List.of(
                rule(StatutoryRuleContext.DEFAULT, "100000.00", LocalDate.of(2000, 4, 1), OLD_TO),
                rule(StatutoryRuleContext.DEFAULT, "200000.00", NEW_FROM, null));

        assertEquals(0, new BigDecimal("100000.00")
                .compareTo(StatutorySectionRules.effectiveRule(rows, OLD_TO, null).getThresholdAnnual()));
        assertEquals(0, new BigDecimal("200000.00")
                .compareTo(StatutorySectionRules.effectiveRule(rows, NEW_FROM, null).getThresholdAnnual()));
    }

    @Test
    void effectiveBounds_areInclusiveOnBothEnds() {
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 12, 31);
        StatutorySectionRule r = rule(StatutoryRuleContext.DEFAULT, "100.00", from, to);
        List<StatutorySectionRule> rows = List.of(r);

        assertEquals(r, StatutorySectionRules.effectiveRule(rows, from, StatutoryRuleContext.DEFAULT));
        assertEquals(r, StatutorySectionRules.effectiveRule(rows, to, StatutoryRuleContext.DEFAULT));
        assertNull(StatutorySectionRules.effectiveRule(rows, from.minusDays(1), StatutoryRuleContext.DEFAULT));
        assertNull(StatutorySectionRules.effectiveRule(rows, to.plusDays(1), StatutoryRuleContext.DEFAULT));
    }

    @Test
    void overlappingRows_latestEffectiveFromWins() {
        StatutorySectionRule earlier = rule(StatutoryRuleContext.DEFAULT, "100.00",
                LocalDate.of(2000, 4, 1), null);
        StatutorySectionRule later = rule(StatutoryRuleContext.DEFAULT, "200.00",
                LocalDate.of(2010, 4, 1), null);

        assertEquals(later, StatutorySectionRules.effectiveRule(
                List.of(earlier, later), LocalDate.of(2020, 1, 1), StatutoryRuleContext.DEFAULT));
        assertEquals(later, StatutorySectionRules.effectiveRule(
                List.of(later, earlier), LocalDate.of(2020, 1, 1), StatutoryRuleContext.DEFAULT));
    }

    @Test
    void contextWithoutCoveringRow_fallsBackToDefault() {
        List<StatutorySectionRule> rows = List.of(
                rule(StatutoryRuleContext.DEFAULT, "10000.00", LocalDate.of(2000, 4, 1), null),
                rule(StatutoryRuleContext.SENIOR_CITIZEN, "100000.00", NEW_FROM, null));

        assertEquals(0, new BigDecimal("100000.00").compareTo(
                StatutorySectionRules.effectiveRule(rows, NEW_FROM, StatutoryRuleContext.SENIOR_CITIZEN)
                        .getThresholdAnnual()));
        // Before the senior row starts, the request falls back to the DEFAULT row.
        assertEquals(0, new BigDecimal("10000.00").compareTo(
                StatutorySectionRules.effectiveRule(rows, OLD_TO, StatutoryRuleContext.SENIOR_CITIZEN)
                        .getThresholdAnnual()));
    }

    @Test
    void contextWithNoRowsAtAll_fallsBackToDefault() {
        StatutorySectionRule r = rule("SOME_FUTURE_CONTEXT", "999.00", LocalDate.of(2000, 4, 1), null);
        StatutorySectionRule dflt = rule(StatutoryRuleContext.DEFAULT, "100.00", LocalDate.of(2000, 4, 1), null);

        assertEquals(dflt, StatutorySectionRules.effectiveRule(
                List.of(r, dflt), LocalDate.of(2020, 1, 1), "NO_SUCH_CONTEXT"));
        // But a context that has its own covering row never falls back.
        assertEquals(r, StatutorySectionRules.effectiveRule(
                List.of(r, dflt), LocalDate.of(2020, 1, 1), "SOME_FUTURE_CONTEXT"));
    }

    @Test
    void nothingCoversTheDate_returnsNull() {
        List<StatutorySectionRule> rows = List.of(
                rule(StatutoryRuleContext.DEFAULT, "100.00", NEW_FROM, null));

        assertNull(StatutorySectionRules.effectiveRule(rows, OLD_TO, StatutoryRuleContext.DEFAULT));
    }

    @Test
    void nullDate_returnsNull_andNullContextMeansDefault() {
        StatutorySectionRule r = rule(StatutoryRuleContext.DEFAULT, "100.00",
                LocalDate.of(2000, 4, 1), null);
        List<StatutorySectionRule> rows = List.of(r);

        assertNull(StatutorySectionRules.effectiveRule(rows, null, StatutoryRuleContext.DEFAULT));
        assertNull(StatutorySectionRules.effectiveRule(null, LocalDate.of(2026, 4, 1), null));
        assertEquals(r, StatutorySectionRules.effectiveRule(rows, LocalDate.of(2026, 4, 1), null));
    }

    @Test
    void seeded194aContexts_resolveTheFinanceAct2025Seams() {
        LocalDate fa2025From = LocalDate.of(2025, 4, 1);
        List<StatutorySectionRule> rows = List.of(
                rule(StatutoryRuleContext.DEFAULT, "10000.00", LocalDate.of(2000, 4, 1), null),
                rule(StatutoryRuleContext.BANK, "40000.00", LocalDate.of(2000, 4, 1), fa2025From.minusDays(1)),
                rule(StatutoryRuleContext.BANK, "50000.00", fa2025From, OLD_TO),
                rule(StatutoryRuleContext.BANK, "50000.00", NEW_FROM, null),
                rule(StatutoryRuleContext.OTHER_PAYER, "5000.00", LocalDate.of(2000, 4, 1), fa2025From.minusDays(1)),
                rule(StatutoryRuleContext.OTHER_PAYER, "10000.00", fa2025From, OLD_TO),
                rule(StatutoryRuleContext.OTHER_PAYER, "10000.00", NEW_FROM, null),
                rule(StatutoryRuleContext.SENIOR_CITIZEN, "100000.00", NEW_FROM, null));

        String bank = StatutoryRuleContext.BANK;
        String other = StatutoryRuleContext.OTHER_PAYER;
        String senior = StatutoryRuleContext.SENIOR_CITIZEN;

        assertEquals(0, new BigDecimal("40000.00").compareTo(
                StatutorySectionRules.effectiveRule(rows, fa2025From.minusDays(1), bank).getThresholdAnnual()));
        assertEquals(0, new BigDecimal("50000.00").compareTo(
                StatutorySectionRules.effectiveRule(rows, fa2025From, bank).getThresholdAnnual()));
        assertEquals(0, new BigDecimal("50000.00").compareTo(
                StatutorySectionRules.effectiveRule(rows, OLD_TO, bank).getThresholdAnnual()));
        assertEquals(0, new BigDecimal("50000.00").compareTo(
                StatutorySectionRules.effectiveRule(rows, NEW_FROM, bank).getThresholdAnnual()));

        assertEquals(0, new BigDecimal("5000.00").compareTo(
                StatutorySectionRules.effectiveRule(rows, fa2025From.minusDays(1), other).getThresholdAnnual()));
        assertEquals(0, new BigDecimal("10000.00").compareTo(
                StatutorySectionRules.effectiveRule(rows, fa2025From, other).getThresholdAnnual()));
        assertEquals(0, new BigDecimal("10000.00").compareTo(
                StatutorySectionRules.effectiveRule(rows, NEW_FROM, other).getThresholdAnnual()));

        assertEquals(0, new BigDecimal("100000.00").compareTo(
                StatutorySectionRules.effectiveRule(rows, NEW_FROM, senior).getThresholdAnnual()));
    }
}
