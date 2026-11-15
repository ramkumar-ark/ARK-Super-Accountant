package com.arktech.superaccountant.daybook.events;

import java.util.UUID;

/**
 * Published by DayBookIntakeService.intake once the upload job is COMPLETED.
 * Listener: snapshot/tamper comparison (worker S). Contract C3-UPLEVT.
 */
public record DayBookUploadCompletedEvent(UUID organizationId, UUID uploadJobId) {
}
