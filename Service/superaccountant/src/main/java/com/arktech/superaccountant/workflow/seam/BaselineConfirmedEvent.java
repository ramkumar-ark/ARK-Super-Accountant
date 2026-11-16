package com.arktech.superaccountant.workflow.seam;

import java.util.UUID;

/**
 * Published after baseline rows are confirmed so reconciliations and other period-start
 * work can re-run over the now-bound upload.
 */
public record BaselineConfirmedEvent(UUID organizationId) {
}
