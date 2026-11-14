package com.arktech.superaccountant.workflow.seam;

import com.arktech.superaccountant.masters.models.UploadJob;
import com.arktech.superaccountant.masters.models.UploadJobStatus;
import com.arktech.superaccountant.masters.repository.UploadJobRepository;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;

/**
 * The latest completed masters job of an organization: COMPLETED or
 * COMPLETED_WITH_MISMATCHES, whichever is newest. FAILED and DISCARDED jobs never
 * satisfy the freshness check.
 */
@Service
public class MastersJobGate {

    private final UploadJobRepository uploadJobRepository;

    public MastersJobGate(UploadJobRepository uploadJobRepository) {
        this.uploadJobRepository = uploadJobRepository;
    }

    public Optional<UUID> latestCompletedMastersJob(UUID organizationId) {
        return uploadJobRepository.findTopByOrganizationIdAndStatusInOrderByCreatedAtDesc(
                        organizationId, EnumSet.of(UploadJobStatus.COMPLETED,
                                UploadJobStatus.COMPLETED_WITH_MISMATCHES))
                .map(UploadJob::getId);
    }
}
