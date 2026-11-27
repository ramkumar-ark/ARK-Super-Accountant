package com.arktech.superaccountant.statutory.services;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Expectations are written out here (not read from the seed) so a seed edit cannot self-confirm. */
class GstParametersTest {

    private final GstParameters parameters = GstStatutorySeed.asParameters();

    @Test
    void b2clThresholdChangesExactlyOnTheNotificationDate() {
        assertEquals(0, new BigDecimal("250000").compareTo(parameters.b2clThreshold(LocalDate.of(2024, 7, 31))));
        assertEquals(0, new BigDecimal("100000").compareTo(parameters.b2clThreshold(LocalDate.of(2024, 8, 1))));
    }

    @Test
    void b2clThresholdByPeriodSplitsJulyAndAugust2024() {
        assertEquals(0, new BigDecimal("250000").compareTo(parameters.b2clThreshold(YearMonth.of(2024, 7))));
        assertEquals(0, new BigDecimal("100000").compareTo(parameters.b2clThreshold(YearMonth.of(2024, 8))));
        assertEquals(0, new BigDecimal("100000").compareTo(parameters.b2clThreshold(YearMonth.of(2026, 3))));
    }

    @Test
    void periodAndDocumentLookupsAgreeAtTheBoundary() {
        // prepare/validate resolve by period, recon by document date: same answer on both sides
        for (LocalDate date : List.of(LocalDate.of(2024, 7, 31), LocalDate.of(2024, 8, 1),
                LocalDate.of(2024, 8, 31), LocalDate.of(2025, 1, 15))) {
            assertEquals(0, parameters.b2clThreshold(date)
                    .compareTo(parameters.b2clThreshold(YearMonth.from(date))), date.toString());
        }
    }

    @Test
    void b2clThresholdBeforeGstStartIsAConfigurationGap() {
        assertThrows(IllegalStateException.class, () -> parameters.b2clThreshold(LocalDate.of(2017, 6, 30)));
    }

    @Test
    void iffCapIsFiftyLakh() {
        assertEquals(0, new BigDecimal("5000000").compareTo(parameters.iffCap(LocalDate.of(2026, 3, 1))));
    }

    @Test
    void allowedRatesAreTheEstablishedSlabsRegardlessOfScale() {
        for (String rate : List.of("0", "0.1", "0.25", "1", "1.5", "3", "5", "7.5", "12", "18", "28", "40",
                "18.00", "7.50")) {
            assertTrue(parameters.isAllowedRate(new BigDecimal(rate), LocalDate.of(2026, 3, 1)), rate);
        }
        for (String rate : List.of("2", "6", "14.5", "-1")) {
            assertFalse(parameters.isAllowedRate(new BigDecimal(rate), LocalDate.of(2026, 3, 1)), rate);
        }
        assertFalse(parameters.isAllowedRate(null, LocalDate.of(2026, 3, 1)));
    }

    @Test
    void aNewDatedRowTakesOverWithoutAnyCodeChange() {
        LocalDate from = LocalDate.of(2030, 1, 1);
        GstStatutorySeed.Row current = GstStatutorySeed.ROWS.get(1);
        GstStatutorySeed.Row next = new GstStatutorySeed.Row(GstParameters.B2CL_THRESHOLD, "x", 1,
                "DEFAULT", new BigDecimal("50000"), from, null, "test");
        var closed = GstStatutorySeed.toRule(current, null);
        closed.setEffectiveTo(from.minusDays(1));
        GstParameters changed = GstParameters.of(Map.of(GstParameters.B2CL_THRESHOLD,
                List.of(closed, GstStatutorySeed.toRule(next, null))));
        assertEquals(0, new BigDecimal("100000").compareTo(changed.b2clThreshold(from.minusDays(1))));
        assertEquals(0, new BigDecimal("50000").compareTo(changed.b2clThreshold(from)));
    }
}
