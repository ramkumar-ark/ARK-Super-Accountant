package com.arktech.superaccountant.statutory.models;

/**
 * Why a new rule row starts on its effective_from date, which decides how the date is
 * validated: a regime change (Finance Act) must start on a financial-year start (1 April);
 * a mid-year change must carry the exact date the notification or amendment states.
 */
public enum EffectiveDateKind {
    /** Annual regime change: effective_from must be 1 April. */
    FINANCIAL_YEAR_START,
    /** Mid-year notification/amendment: effective_from is the exact statutory date. */
    STATUTORY_DATE
}
