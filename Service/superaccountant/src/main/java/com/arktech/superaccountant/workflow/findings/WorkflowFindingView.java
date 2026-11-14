package com.arktech.superaccountant.workflow.findings;

import com.arktech.superaccountant.masters.models.ResolveStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * The workflow-facing shape of one finding, independent of the owning module.
 */
public record WorkflowFindingView(UUID id, String source, String findingType, String severity, boolean blocking,
                                  String message, String expectedValue, String actualValue, String voucherNumber,
                                  LocalDate voucherDate, String ledgerName, ResolveStatus resolveStatus,
                                  String resolveNote, String proposedValue, String submittedBy, Instant submittedAt,
                                  String reviewedBy, Instant reviewedAt, String reviewNote, String resolvedBy,
                                  Instant resolvedAt, UUID carriedFromFindingId) {
}
