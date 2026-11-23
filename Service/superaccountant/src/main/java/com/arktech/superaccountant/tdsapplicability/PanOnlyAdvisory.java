package com.arktech.superaccountant.tdsapplicability;

import java.math.BigDecimal;

/**
 * A PAN-only TDS advisory (section 194M or 194-IB) for an individual/HUF
 * organization in a financial year it is not a TDS deductor. Advisories only:
 * no findings table, no workflow, no TAN requirement.
 *
 * <ul>
 *   <li>{@code section} — "194M" or "194-IB"</li>
 *   <li>{@code month} — "2026-07" style calendar month for 194-IB, null for 194M</li>
 *   <li>{@code form} — "Form 26QD" (194M) or "Form 26QC" (194-IB) when the
 *       threshold was first crossed before 1 April 2026, otherwise "Form 141"</li>
 * </ul>
 */
public record PanOnlyAdvisory(String section, String partyLedgerName, int financialYear,
                              String month, BigDecimal amount, String form) {
}
