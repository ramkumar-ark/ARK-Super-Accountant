package com.arktech.superaccountant.masters.events;

import java.util.UUID;

/**
 * Published by UploadController.uploadMasters once the masters job completes
 * (COMPLETED or COMPLETED_WITH_MISMATCHES). Listener: snapshot masters tamper
 * comparison (worker Sm). Contract C4-MEVT.
 */
public record MastersUploadCompletedEvent(UUID organizationId, UUID uploadJobId) {
}
