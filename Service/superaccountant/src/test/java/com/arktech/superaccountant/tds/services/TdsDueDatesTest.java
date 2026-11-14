package com.arktech.superaccountant.tds.services;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TdsDueDatesTest {

    @Test
    void paymentDueDate_isSeventhOfNextMonth() {
        assertEquals(LocalDate.of(2025, 5, 7), TdsDueDates.paymentDueDate(YearMonth.of(2025, 4)));
        assertEquals(LocalDate.of(2026, 1, 7), TdsDueDates.paymentDueDate(YearMonth.of(2025, 12)));
    }

    @Test
    void paymentDueDate_marchIsAprilThirtieth() {
        assertEquals(LocalDate.of(2026, 4, 30), TdsDueDates.paymentDueDate(YearMonth.of(2026, 3)));
    }
}
