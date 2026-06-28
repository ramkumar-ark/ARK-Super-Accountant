package com.arktech.superaccountant.daybook.services;

import com.arktech.superaccountant.masters.models.UploadJob;
import com.arktech.superaccountant.masters.models.UploadJobStatus;
import com.arktech.superaccountant.masters.repository.UploadJobRepository;
import com.arktech.superaccountant.masters.repository.UploadLedgerSnapshotRepository;
import com.arktech.superaccountant.masters.repository.ValidationFindingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class DayBookMasterValidationService {

    @Autowired
    private UploadJobRepository uploadJobRepository;

    @Autowired
    private UploadLedgerSnapshotRepository uploadLedgerSnapshotRepository;

    @Autowired
    private ValidationFindingRepository findingRepository;

    /**
     * Returns list of ledger names from the daybook that don't exist in the org's
     * validated masters snapshot. Empty list means all ledgers are known.
     *
     * @param orgId          org from JWT principal
     * @param daybookLedgers all unique ledger names from ParsedVoucherEntry records
     * @return list of unknown ledger names (empty = all valid)
     */
    public List<String> findUnknownLedgers(UUID orgId, Set<String> daybookLedgers) {
        // Find the org's latest validated masters job (all findings resolved)
        Optional<UploadJob> validatedJobOpt = uploadJobRepository
                .findTopByOrganizationIdAndStatusInOrderByCreatedAtDesc(orgId,
                        List.of(UploadJobStatus.COMPLETED, UploadJobStatus.COMPLETED_WITH_MISMATCHES));

        if (validatedJobOpt.isEmpty()) {
            // No validated masters at all — gate should have blocked this, but return all as unknown
            return List.copyOf(daybookLedgers);
        }

        UploadJob validatedJob = validatedJobOpt.get();

        // Verify this job is actually fully validated
        long unresolvedCount = findingRepository.countUnresolvedForGate(validatedJob.getId());
        if (unresolvedCount > 0) {
            // Gate should have blocked this upload; defensively treat all as unknown
            return List.copyOf(daybookLedgers);
        }

        // Fetch all known ledger names from the validated job's snapshots
        List<String> knownLedgerNames = uploadLedgerSnapshotRepository
                .findLedgerNamesByUploadJobId(validatedJob.getId());

        // Case-insensitive comparison
        Set<String> knownNormalized = knownLedgerNames.stream()
                .map(n -> n.trim().toLowerCase())
                .collect(Collectors.toSet());

        return daybookLedgers.stream()
                .filter(name -> !knownNormalized.contains(name.trim().toLowerCase()))
                .sorted()
                .collect(Collectors.toList());
    }
}
