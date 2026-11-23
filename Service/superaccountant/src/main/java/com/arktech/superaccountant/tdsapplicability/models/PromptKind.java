package com.arktech.superaccountant.tdsapplicability.models;

/**
 * The kind of TDS applicability prompt shown to the user.
 *
 * <ul>
 *   <li>{@code BOOKS_EXCEED_LIMIT} — the computed preceding-year turnover crosses the
 *       statutory limit, so a year recorded as not applicable is in question.</li>
 *   <li>{@code NEXT_YEAR_DECLARATION} — a financial year closed and the next year has no
 *       recorded applicability answer yet.</li>
 *   <li>{@code LEGACY_INDIVIDUAL_NOTE} — a one-time note for existing individual/HUF
 *       organizations that have no recorded answers at all.</li>
 * </ul>
 */
public enum PromptKind {
    BOOKS_EXCEED_LIMIT,
    NEXT_YEAR_DECLARATION,
    LEGACY_INDIVIDUAL_NOTE
}
