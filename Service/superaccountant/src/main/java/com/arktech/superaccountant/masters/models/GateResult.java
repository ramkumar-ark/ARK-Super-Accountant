package com.arktech.superaccountant.masters.models;

/**
 * Value object returned by {@link com.arktech.superaccountant.masters.services.MastersGateService#checkGate(java.util.UUID)}.
 *
 * Reused by Phase 5 TdsReportController and Phase 6 GstValidationController
 * to determine whether the org's masters have unresolved HIGH-severity findings
 * that block access to compliance reports.
 */
public record GateResult(boolean gated, int unresolvedCount) {

    /**
     * Returns an open gate — no findings blocking access.
     */
    public static GateResult open() {
        return new GateResult(false, 0);
    }

    /**
     * Returns a closed gate with the given unresolved HIGH finding count.
     *
     * @param count number of unresolved HIGH-severity findings (may be 0 if no masters upload exists)
     */
    public static GateResult gated(int count) {
        return new GateResult(true, count);
    }
}
