package com.arktech.superaccountant.tds;

import com.arktech.superaccountant.tds.models.TdsSectionConfig;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TdsSectionConfigActResolutionTest {

    private TdsSectionConfig config() {
        TdsSectionConfig c = new TdsSectionConfig();
        c.setSectionCode("194J_A");
        c.setDescription("Fees for technical services");
        c.setRateCompany(new BigDecimal("2.00"));
        c.setRateNonCompany(new BigDecimal("2.00"));
        c.setThresholdAnnual(new BigDecimal("30000.00"));
        c.setHigherRateIfNoPan(new BigDecimal("20.00"));
        c.setNewActRateCompany(new BigDecimal("2.00"));
        c.setNewActRateNonCompany(new BigDecimal("2.00"));
        c.setNewActThresholdAnnual(new BigDecimal("50000.00"));
        return c;
    }

    @Test
    void newActAppliesOnOrAfterFirstApril2026() {
        assertTrue(TdsSectionConfig.newActApplies(LocalDate.of(2026, 4, 1)));
        assertTrue(TdsSectionConfig.newActApplies(LocalDate.of(2027, 3, 31)));
        assertFalse(TdsSectionConfig.newActApplies(LocalDate.of(2026, 3, 31)));
        assertFalse(TdsSectionConfig.newActApplies(LocalDate.of(2025, 4, 1)));
    }

    @Test
    void beforeEffectiveDateReturnsOldValuesUntouched() {
        TdsSectionConfig resolved = config().effectiveFor(LocalDate.of(2026, 3, 31));
        assertEquals("30000.00", resolved.getThresholdAnnual().toPlainString());
    }

    @Test
    void onEffectiveDateReturnsNewActValues() {
        TdsSectionConfig resolved = config().effectiveFor(LocalDate.of(2026, 4, 1));
        assertEquals("50000.00", resolved.getThresholdAnnual().toPlainString());
        assertEquals("2.00", resolved.getRateCompany().toPlainString());
    }

    @Test
    void resolvedCopyDoesNotMutateOriginal() {
        TdsSectionConfig original = config();
        TdsSectionConfig resolved = original.effectiveFor(LocalDate.of(2026, 4, 1));
        assertEquals("30000.00", original.getThresholdAnnual().toPlainString());
        assertEquals("50000.00", resolved.getThresholdAnnual().toPlainString());
    }

    @Test
    void configWithoutNewActValuesKeepsOldBehaviourEvenAfterEffectiveDate() {
        TdsSectionConfig c = config();
        c.setNewActRateCompany(null);
        TdsSectionConfig resolved = c.effectiveFor(LocalDate.of(2026, 4, 1));
        assertEquals("30000.00", resolved.getThresholdAnnual().toPlainString());
    }
}
