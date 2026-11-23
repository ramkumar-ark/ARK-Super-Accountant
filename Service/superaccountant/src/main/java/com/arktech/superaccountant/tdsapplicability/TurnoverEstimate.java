package com.arktech.superaccountant.tdsapplicability;

import java.math.BigDecimal;

/**
 * An estimate of the organization's turnover for one preceding financial year,
 * computed from books or filed GSTR-3B returns (never persisted).
 *
 * @param measuredFinancialYear the financial year the figure is for (FY 2025-26 = 2025)
 * @param amount                the turnover figure, or null when nothing is known
 * @param source                where the figure came from
 * @param complete              true when the source data covers the whole year;
 *                              false means the amount is an estimate from partial data
 */
public record TurnoverEstimate(int measuredFinancialYear, BigDecimal amount,
                               TurnoverSource source, boolean complete) {

    public enum TurnoverSource {
        BOOKS,
        GSTR3B,
        NONE
    }

    public static TurnoverEstimate none(int measuredFinancialYear) {
        return new TurnoverEstimate(measuredFinancialYear, null, TurnoverSource.NONE, false);
    }
}
