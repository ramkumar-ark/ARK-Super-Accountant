package com.arktech.superaccountant.tds.models;

import com.arktech.superaccountant.statutory.models.StatutoryRuleContext;
import com.arktech.superaccountant.statutory.models.StatutorySectionRule;
import com.arktech.superaccountant.statutory.models.StatutorySectionRules;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Dated-rule delegation on TdsSectionConfig: with attached statutory rule rows,
 * effectiveFor must resolve both regimes from those rows and stay byte-identical
 * to the legacy effectiveForFromColumns resolution.
 */
class TdsSectionConfigDatedRulesTest {

    private static final LocalDate OLD_TO = LocalDate.of(2026, 3, 31);
    private static final LocalDate NEW_FROM = LocalDate.of(2026, 4, 1);

    private StatutorySectionRule rule(String rateCompany, String rateNonCompany,
                                      String perTransaction, String annual,
                                      LocalDate from, LocalDate to) {
        StatutorySectionRule r = new StatutorySectionRule();
        r.setSectionId(UUID.randomUUID());
        r.setRuleContext(StatutoryRuleContext.DEFAULT);
        r.setRateCompany(rateCompany == null ? null : new BigDecimal(rateCompany));
        r.setRateNonCompany(rateNonCompany == null ? null : new BigDecimal(rateNonCompany));
        r.setThresholdPerTransaction(perTransaction == null ? null : new BigDecimal(perTransaction));
        r.setThresholdAnnual(annual == null ? null : new BigDecimal(annual));
        r.setHigherRateIfNoPan(new BigDecimal("20.00"));
        r.setEffectiveFrom(from);
        r.setEffectiveTo(to);
        return r;
    }

    private TdsSectionConfig config194Ia() {
        TdsSectionConfig c = new TdsSectionConfig();
        c.setSectionCode("194I_A");
        c.setRateCompany(new BigDecimal("2.00"));
        c.setRateNonCompany(new BigDecimal("2.00"));
        c.setThresholdAnnual(new BigDecimal("240000.00"));
        c.setHigherRateIfNoPan(new BigDecimal("20.00"));
        c.setNewActRateCompany(new BigDecimal("2.00"));
        c.setNewActRateNonCompany(new BigDecimal("2.00"));
        c.setNewActThresholdPerTransaction(null);
        c.setNewActThresholdAnnual(new BigDecimal("600000.00"));
        return c;
    }

    @Test
    void withDatedRules_bothRegimesResolveFromRowsAndMatchTheColumnPath() {
        TdsSectionConfig c = config194Ia();
        c.setDatedRules(List.of(
                rule("2.00", "2.00", null, "240000.00", LocalDate.of(2000, 4, 1), OLD_TO),
                rule("2.00", "2.00", null, "600000.00", NEW_FROM, null)));

        TdsSectionConfig datedOld = c.effectiveFor(OLD_TO);
        TdsSectionConfig columnOld = c.effectiveForFromColumns(OLD_TO);
        assertEquals(columnOld.getRateCompany(), datedOld.getRateCompany());
        assertEquals(columnOld.getRateNonCompany(), datedOld.getRateNonCompany());
        assertEquals(columnOld.getThresholdPerTransaction(), datedOld.getThresholdPerTransaction());
        assertEquals(columnOld.getThresholdAnnual(), datedOld.getThresholdAnnual());
        assertEquals(columnOld.getHigherRateIfNoPan(), datedOld.getHigherRateIfNoPan());
        assertEquals(new BigDecimal("240000.00"), datedOld.getThresholdAnnual());

        TdsSectionConfig datedNew = c.effectiveFor(NEW_FROM);
        TdsSectionConfig columnNew = c.effectiveForFromColumns(NEW_FROM);
        assertEquals(columnNew.getRateCompany(), datedNew.getRateCompany());
        assertEquals(columnNew.getRateNonCompany(), datedNew.getRateNonCompany());
        assertEquals(columnNew.getThresholdPerTransaction(), datedNew.getThresholdPerTransaction());
        assertEquals(columnNew.getThresholdAnnual(), datedNew.getThresholdAnnual());
        assertEquals(columnNew.getHigherRateIfNoPan(), datedNew.getHigherRateIfNoPan());
        assertEquals(new BigDecimal("600000.00"), datedNew.getThresholdAnnual());
    }

    @Test
    void withDatedRules_originalIsNeverMutatedAndCopiesAreDetached() {
        TdsSectionConfig c = config194Ia();
        c.setId(UUID.randomUUID());
        c.setDatedRules(List.of(
                rule("2.00", "2.00", null, "240000.00", LocalDate.of(2000, 4, 1), OLD_TO),
                rule("2.00", "2.00", null, "600000.00", NEW_FROM, null)));

        TdsSectionConfig resolved = c.effectiveFor(NEW_FROM);
        assertNotSame(c, resolved);
        assertEquals(c.getId(), resolved.getId());
        // The stored entity keeps its own regime values.
        assertEquals(new BigDecimal("240000.00"), c.getThresholdAnnual());
        assertNull(c.getThresholdPerTransaction());
        assertEquals(new BigDecimal("600000.00"), resolved.getThresholdAnnual());
    }

    @Test
    void ruleNullingPerTransaction_overridesTheStoredValue() {
        TdsSectionConfig c = config194Ia();
        c.setThresholdPerTransaction(new BigDecimal("240000.00"));
        c.setDatedRules(List.of(
                rule("2.00", "2.00", null, "600000.00", NEW_FROM, null)));

        TdsSectionConfig resolved = c.effectiveFor(NEW_FROM);
        assertNull(resolved.getThresholdPerTransaction(),
                "a dated rule row must be able to clear a per-transaction limit");
        assertEquals(new BigDecimal("600000.00"), resolved.getThresholdAnnual());
    }

    @Test
    void dateBeforeEveryRow_returnsThisInstanceWithStoredValues() {
        TdsSectionConfig c = config194Ia();
        c.setDatedRules(List.of(
                rule("2.00", "2.00", null, "240000.00", LocalDate.of(2000, 4, 1), OLD_TO),
                rule("2.00", "2.00", null, "600000.00", NEW_FROM, null)));

        TdsSectionConfig resolved = c.effectiveFor(LocalDate.of(1999, 6, 1));
        assertSame(c, resolved,
                "no covering row must leave the stored regime untouched, like the legacy path");
    }

    @Test
    void withoutNewActValues_singleOpenEndedRuleKeepsOldValuesPastTheSeam() {
        TdsSectionConfig c = new TdsSectionConfig();
        c.setSectionCode("194M");
        c.setRateCompany(new BigDecimal("2.00"));
        c.setRateNonCompany(new BigDecimal("2.00"));
        c.setThresholdAnnual(new BigDecimal("5000000.00"));
        c.setHigherRateIfNoPan(new BigDecimal("20.00"));
        c.setDatedRules(List.of(
                rule("2.00", "2.00", null, "5000000.00", LocalDate.of(2000, 4, 1), null)));

        TdsSectionConfig dated = c.effectiveFor(NEW_FROM);
        TdsSectionConfig column = c.effectiveForFromColumns(NEW_FROM);
        assertEquals(column.getRateCompany(), dated.getRateCompany());
        assertEquals(column.getRateNonCompany(), dated.getRateNonCompany());
        assertEquals(column.getThresholdPerTransaction(), dated.getThresholdPerTransaction());
        assertEquals(column.getThresholdAnnual(), dated.getThresholdAnnual());
        assertEquals(new BigDecimal("5000000.00"), dated.getThresholdAnnual());
    }

    @Test
    void withoutDatedRules_delegatesToTheLegacyColumnPath() {
        TdsSectionConfig c = config194Ia();

        TdsSectionConfig resolved = c.effectiveFor(NEW_FROM);
        assertEquals(new BigDecimal("600000.00"), resolved.getThresholdAnnual(),
                "bare configs must keep resolving from the new-act columns");
    }
}
