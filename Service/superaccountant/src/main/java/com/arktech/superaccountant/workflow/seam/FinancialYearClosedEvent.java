package com.arktech.superaccountant.workflow.seam;

import java.util.UUID;

/**
 * Published after a financial year is successfully closed so next-year work
 * (such as the TDS applicability declaration) can start.
 */
public record FinancialYearClosedEvent(UUID organizationId, int financialYear) {
}
