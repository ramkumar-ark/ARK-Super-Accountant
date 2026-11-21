package com.arktech.superaccountant.masters.pan;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** The single company PAN override store, as masters sees it. Implemented in tds. */
public interface PanOverrideGateway {
    Optional<String> findPan(UUID organizationId, String ledgerName);

    void savePan(UUID organizationId, String ledgerName, String pan, UUID sourceFindingId,
                 String approvedBy, Instant approvedAt);
}
