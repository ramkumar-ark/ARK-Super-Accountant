package com.arktech.superaccountant.workflow.seam;

import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Seam for the snapshot worker (S): freezes a snapshot when a workflow
 * finalizes. No-op default until snapshot/tamper work lands.
 */
@Service
public class SnapshotService {
    public void freeze(UUID workflowId, UUID organizationId, String actor) {
        // no-op seam
    }
}
