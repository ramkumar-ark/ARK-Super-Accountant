package com.arktech.superaccountant.masters.pan;

import java.time.Instant;
import java.util.UUID;

/** Called after a PAN override is saved, so Tally write-back can record the change. */
public interface PanChangeRecorder {
    void onPanSaved(UUID organizationId, String ledgerName, String pan, UUID overrideId,
                    UUID sourceFindingId, String approvedBy, Instant approvedAt);
}
