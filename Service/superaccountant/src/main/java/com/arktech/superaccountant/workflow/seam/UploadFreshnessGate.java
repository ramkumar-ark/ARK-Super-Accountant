package com.arktech.superaccountant.workflow.seam;

import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Seam for the upload-binding worker (B): whether the given upload job is
 * still the organization's latest upload. No-op default returns true.
 */
@Service
public class UploadFreshnessGate {
    public boolean isStillLatest(UUID organizationId, UUID uploadJobId) {
        return true;
    }
}
