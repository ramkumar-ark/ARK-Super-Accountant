package com.arktech.superaccountant.tds.mapping;

import com.arktech.superaccountant.masters.models.UploadLedgerSnapshot;
import com.arktech.superaccountant.masters.repository.UploadLedgerSnapshotRepository;
import com.arktech.superaccountant.tdsapplicability.TdsApplicabilityService;
import com.arktech.superaccountant.workflow.seam.MastersJobGate;
import com.arktech.superaccountant.workflow.service.CoverageService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class TdsLedgerMappingGate {

    private static final String NO_MASTERS_BLOCKER = "No masters upload: TDS ledgers cannot be mapped";

    private final TdsLedgerMappingService mappingService;

    private final MastersJobGate mastersJobGate;

    private final UploadLedgerSnapshotRepository snapshotRepository;

    private final TdsApplicabilityService tdsApplicabilityService;

    private final CoverageService coverageService;

    public TdsLedgerMappingGate(TdsLedgerMappingService mappingService,
            MastersJobGate mastersJobGate,
            UploadLedgerSnapshotRepository snapshotRepository,
            TdsApplicabilityService tdsApplicabilityService,
            CoverageService coverageService) {
        this.mappingService = mappingService;
        this.mastersJobGate = mastersJobGate;
        this.snapshotRepository = snapshotRepository;
        this.tdsApplicabilityService = tdsApplicabilityService;
        this.coverageService = coverageService;
    }

    public List<String> unmappedPayableLedgers(UUID organizationId) {
        return candidateLedgersWithoutSavedRow(organizationId).entrySet().stream()
                .filter(e -> e.getValue() == TdsLedgerArea.PAYABLE)
                .map(Map.Entry::getKey)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toList());
    }

    public boolean isComplete(UUID organizationId) {
        return blockerMessage(organizationId).isEmpty();
    }

    public void requireComplete(UUID organizationId) {
        blockerMessage(organizationId)
                .ifPresent(message -> { throw new TdsLedgerMappingIncompleteException(message); });
    }

    public Optional<String> blockerMessage(UUID organizationId) {
        if (!isDeductorOrganization(organizationId)) {
            return Optional.empty();
        }
        if (mastersJobGate.latestCompletedMastersJob(organizationId).isEmpty()) {
            return Optional.of(NO_MASTERS_BLOCKER);
        }
        List<String> unmapped = unmappedPayableLedgers(organizationId);
        if (!unmapped.isEmpty()) {
            String names = unmapped.stream().limit(5).collect(Collectors.joining(", "));
            return Optional.of(unmapped.size() + " TDS ledgers not mapped: " + names);
        }
        return Optional.empty();
    }

    private boolean isDeductorOrganization(UUID organizationId) {
        return tdsApplicabilityService.anyApplicableFrom(organizationId,
                coverageService.coverageStart(organizationId));
    }

    /**
     * Unmapped TDS candidates of the latest completed masters job with their area, where an
     * unsaved row defaults to PAYABLE unless it is suggested receivable.
     */
    private Map<String, TdsLedgerArea> candidateLedgersWithoutSavedRow(UUID organizationId) {
        Optional<UUID> jobId = mastersJobGate.latestCompletedMastersJob(organizationId);
        if (jobId.isEmpty()) {
            return Map.of();
        }
        Map<String, TdsLedgerArea> unmapped = new java.util.LinkedHashMap<>();
        Map<String, TdsLedgerMapping> mappings = mappingService.mappingsByKey(organizationId);
        for (UploadLedgerSnapshot snapshot : snapshotRepository.findByUploadJobId(jobId.get())) {
            if (!TdsLedgerSuggester.isCandidate(snapshot)) {
                continue;
            }
            String key = TdsLedgerMappingService.ledgerKey(snapshot.getLedgerName());
            if (mappings.containsKey(key)) {
                continue;
            }
            TdsLedgerRole suggested = TdsLedgerSuggester.suggest(snapshot, Set.of()).role();
            unmapped.put(snapshot.getLedgerName(), suggested == TdsLedgerRole.TDS_RECEIVABLE
                    ? TdsLedgerArea.RECEIVABLE
                    : TdsLedgerArea.PAYABLE);
        }
        return unmapped;
    }
}
