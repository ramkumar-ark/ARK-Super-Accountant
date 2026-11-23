package com.arktech.superaccountant.tdsapplicability.models;

import java.math.BigDecimal;

/**
 * The resolved TDS applicability for one organization and financial year.
 *
 * @param financialYear     the financial year the answer is for (FY 2026-27 = 2026)
 * @param applicable        whether the organization deducts TDS for that year
 * @param basis             why (see {@link TdsApplicabilityBasis})
 * @param needsAnswer       true when a carried-forward answer still needs the user's confirmation
 * @param priorYearTurnover the recorded preceding-year turnover, when the basis is turnover-based
 * @param turnoverNature    business or profession, when known
 */
public record TdsApplicability(
        int financialYear,
        boolean applicable,
        TdsApplicabilityBasis basis,
        boolean needsAnswer,
        BigDecimal priorYearTurnover,
        TurnoverNature turnoverNature) {
}
