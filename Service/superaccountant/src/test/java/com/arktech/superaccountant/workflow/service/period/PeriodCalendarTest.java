package com.arktech.superaccountant.workflow.service.period;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PeriodCalendarTest {

    private static final int FY_START = 4;

    @Test
    void financialYearOf_respectsFyStart() {
        assertEquals(2026, PeriodCalendar.financialYearOf(LocalDate.of(2026, 4, 1), FY_START));
        assertEquals(2026, PeriodCalendar.financialYearOf(LocalDate.of(2027, 3, 31), FY_START));
        assertEquals(2025, PeriodCalendar.financialYearOf(LocalDate.of(2026, 3, 31), FY_START));
    }

    @Test
    void resolveMonthly_returnsBounds() {
        var range = PeriodCalendar.resolveMonthly("2026-04", FY_START);
        assertEquals(LocalDate.of(2026, 4, 1), range.start());
        assertEquals(LocalDate.of(2026, 4, 30), range.end());
        assertEquals(2026, range.financialYear());
    }

    @Test
    void resolveQuarter_anchorsAtFyStart() {
        var q1 = PeriodCalendar.resolveQuarter("2026-Q1", FY_START);
        assertEquals(LocalDate.of(2026, 4, 1), q1.start());
        assertEquals(LocalDate.of(2026, 6, 30), q1.end());

        var q4 = PeriodCalendar.resolveQuarter("2026-Q4", FY_START);
        assertEquals(LocalDate.of(2027, 1, 1), q4.start());
        assertEquals(LocalDate.of(2027, 3, 31), q4.end());
    }

    @Test
    void resolveMonthly_rejectsBadKeys() {
        assertThrows(IllegalArgumentException.class,
                () -> PeriodCalendar.resolveMonthly("2026-Q1", FY_START));
        assertThrows(IllegalArgumentException.class,
                () -> PeriodCalendar.resolveQuarter("2026-04", FY_START));
        assertThrows(IllegalArgumentException.class,
                () -> PeriodCalendar.resolveMonthly("abc", FY_START));
    }

    @Test
    void previousKeys_crossFinancialYear() {
        assertEquals("2026-03", PeriodCalendar.previousMonthlyKey("2026-04"));
        assertEquals("2025-Q4", PeriodCalendar.previousQuarterKey("2026-Q1"));
        assertEquals("2026-Q2", PeriodCalendar.previousQuarterKey("2026-Q3"));
    }

    @Test
    void quarterMonths_followsFyAnchor() {
        assertEquals(List.of("2026-04", "2026-05", "2026-06"),
                PeriodCalendar.quarterMonths("2026-Q1", FY_START));
        assertEquals(List.of("2027-01", "2027-02", "2027-03"),
                PeriodCalendar.quarterMonths("2026-Q4", FY_START));
    }

    @Test
    void isQuarterStart_detectsQuarterBoundaries() {
        assertTrue(PeriodCalendar.isQuarterStart(LocalDate.of(2026, 4, 1), FY_START));
        assertTrue(PeriodCalendar.isQuarterStart(LocalDate.of(2026, 7, 1), FY_START));
        assertFalse(PeriodCalendar.isQuarterStart(LocalDate.of(2026, 5, 1), FY_START));
        assertFalse(PeriodCalendar.isQuarterStart(LocalDate.of(2026, 4, 15), FY_START));
    }
}
