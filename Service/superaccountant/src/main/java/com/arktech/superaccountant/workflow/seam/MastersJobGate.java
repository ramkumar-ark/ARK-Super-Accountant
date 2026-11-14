package com.arktech.superaccountant.workflow.seam;

import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/**
 * Seam for the scoped-masters worker (M): the latest completed masters job
 * for an organization. No-op default treats the requirement as satisfied.
 */
@Service
public class MastersJobGate {
    public Optional<UUID> latestCompletedMastersJob(UUID organizationId) {
        return Optional.empty();
    }
}
