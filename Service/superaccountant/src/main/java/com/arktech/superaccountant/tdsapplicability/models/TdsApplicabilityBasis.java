package com.arktech.superaccountant.tdsapplicability.models;

/**
 * Why a financial year's TDS applicability is what it is.
 *
 * <ul>
 *   <li>{@code ENTITY_TYPE} — non-individual entity types always deduct (derived, not asked).</li>
 *   <li>{@code DECLARED} — the user answered the turnover question.</li>
 *   <li>{@code CONFIRMED_FROM_BOOKS} — a books-based cross-check the user confirmed.</li>
 *   <li>{@code LEGACY_DEFAULT} — the organization has no applicability rows, so it keeps today's
 *       all-features-on behaviour.</li>
 *   <li>{@code CARRIED_FORWARD} — no row for this year; the nearest recorded year was carried
 *       forward until the user answers.</li>
 * </ul>
 */
public enum TdsApplicabilityBasis {
    ENTITY_TYPE,
    DECLARED,
    CONFIRMED_FROM_BOOKS,
    LEGACY_DEFAULT,
    CARRIED_FORWARD
}
