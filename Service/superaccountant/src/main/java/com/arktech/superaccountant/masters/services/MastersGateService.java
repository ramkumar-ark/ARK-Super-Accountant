package com.arktech.superaccountant.masters.services;

import com.arktech.superaccountant.masters.models.GateResult;
import com.arktech.superaccountant.masters.models.UploadJob;
import com.arktech.superaccountant.masters.models.UploadJobStatus;
import com.arktech.superaccountant.masters.repository.UploadJobRepository;
import com.arktech.superaccountant.masters.repository.ValidationFindingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/**
 * Reusable gate service that checks whether an organization's masters have
 * unresolved HIGH-severity findings that must block downstream compliance endpoints.
 *
 * Called by Phase 5 TdsReportController and Phase 6 GstValidationController.
 *
 * SECURITY: The {@code orgId} parameter MUST be sourced from the JWT principal
 * ({@code UserDetailsImpl.getOrganizationId()}) at the controller layer — never
 * from a client-supplied HTTP parameter (T-4-02).
 */
@Service
public class MastersGateService {

    @Autowired
    private UploadJobRepository uploadJobRepository;

    @Autowired
    private ValidationFindingRepository findingRepository;

    /**
     * Checks whether this organization's latest completed masters upload has
     * unresolved findings (of any severity).
     *
     * @param orgId Organization UUID from JWT principal — NEVER trust client-supplied org ID.
     * @return {@link GateResult#gated(int)} with unresolvedCount if findings exist
     *         with resolveStatus NOT IN (APPROVED, RESOLVED, DISCARDED) for the latest
     *         completed masters upload; {@link GateResult#open()} otherwise.
     *         If no completed masters upload exists, returns {@code GateResult.gated(0)}
     *         (no data validated → access blocked).
     */
    public GateResult checkGate(UUID orgId) {
        Optional<UploadJob> latestJob = uploadJobRepository
                .findTopByOrganizationIdAndStatusInOrderByCreatedAtDesc(orgId,
                        java.util.List.of(UploadJobStatus.COMPLETED, UploadJobStatus.COMPLETED_WITH_MISMATCHES));

        if (latestJob.isEmpty()) {
            // No completed masters upload at all — treat as gated (nothing validated)
            return GateResult.gated(0);
        }

        UploadJob job = latestJob.get();

        // 0 ledgers parsed means masters are not configured — block downstream
        if (job.getTotalLedgersParsed() == null || job.getTotalLedgersParsed() == 0) {
            return GateResult.gated(0);
        }

        long count = findingRepository.countUnresolvedForGate(job.getId());
        return count > 0
                ? GateResult.gated((int) count)
                : GateResult.open();
    }
}
