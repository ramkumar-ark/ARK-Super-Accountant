package com.arktech.superaccountant.statutory.models;

/**
 * Context keys for {@link StatutorySectionRule} rows. A section can carry one row set
 * for the default case and additional row sets for payer/payee contexts; selection
 * (see {@link StatutorySectionRules}) prefers the requested context and falls back
 * to DEFAULT.
 */
public final class StatutoryRuleContext {

    private StatutoryRuleContext() {
    }

    /** Values with no payer/payee qualification. */
    public static final String DEFAULT = "DEFAULT";

    /** Bank-like payers of interest (bank, co-operative bank, post office) — s.393 Table Sl. No. 5(ii). */
    public static final String BANK = "BANK";

    /** Every payer of interest other than bank-like — s.393 Table Sl. No. 5(iii). */
    public static final String OTHER_PAYER = "OTHER_PAYER";

    /**
     * Payments to senior citizens. Implies the bank-like Table 5(ii) threshold (the
     * Rs 1,00,000 seam only exists there), so callers still gate on payer type; this
     * context exists so the value lives in data, not code.
     */
    public static final String SENIOR_CITIZEN = "SENIOR_CITIZEN";
}
