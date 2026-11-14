package com.arktech.superaccountant.tds.services;

import java.time.LocalDate;
import java.time.YearMonth;

public final class TdsDueDates {

    private TdsDueDates() {}

    /**
     * Statutory TDS payment due date for the given month: the 7th of the next month.
     * March is the exception — TDS for the full financial year's March must be
     * deposited by 30 April of the following (same calendar) year.
     */
    public static LocalDate paymentDueDate(YearMonth month) {
        if (month.getMonthValue() == 3) {
            return LocalDate.of(month.getYear(), 4, 30);
        }
        return month.plusMonths(1).atDay(7);
    }
}
