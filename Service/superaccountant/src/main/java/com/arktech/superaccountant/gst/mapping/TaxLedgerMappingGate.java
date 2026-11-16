package com.arktech.superaccountant.gst.mapping;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class TaxLedgerMappingGate {

    private static final String NO_MASTERS_BLOCKER = "No masters upload: tax ledgers cannot be mapped";

    private final TaxLedgerMappingService mappingService;

    private final com.arktech.superaccountant.workflow.seam.MastersJobGate mastersJobGate;

    private final com.arktech.superaccountant.masters.services.FindingOverrideMasterService findingOverrideMasterService;

    private final com.arktech.superaccountant.masters.repository.OrganizationRepository organizationRepository;

    public TaxLedgerMappingGate(TaxLedgerMappingService mappingService,
            com.arktech.superaccountant.workflow.seam.MastersJobGate mastersJobGate,
            com.arktech.superaccountant.masters.services.FindingOverrideMasterService findingOverrideMasterService,
            com.arktech.superaccountant.masters.repository.OrganizationRepository organizationRepository) {
        this.mappingService = mappingService;
        this.mastersJobGate = mastersJobGate;
        this.findingOverrideMasterService = findingOverrideMasterService;
        this.organizationRepository = organizationRepository;
    }

    public List<String> unmappedLedgers(UUID organizationId) {
        return gstLedgerNamesWithoutSavedRow(organizationId).stream()
                .map(s -> s.getLedgerName())
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toList());
    }

    public boolean isComplete(UUID organizationId) {
        return blockerMessage(organizationId).isEmpty();
    }

    public void requireComplete(UUID organizationId) {
        blockerMessage(organizationId)
                .ifPresent(message -> { throw new TaxLedgerMappingIncompleteException(message); });
    }

    public Optional<String> blockerMessage(UUID organizationId) {
        Optional<UUID> jobId = mastersJobGate.latestCompletedMastersJob(organizationId);
        if (jobId.isEmpty()) {
            if (hasGstin(organizationId)) {
                return Optional.of(NO_MASTERS_BLOCKER);
            }
            return Optional.empty();
        }
        List<String> unmapped = unmappedLedgers(organizationId);
        if (unmapped.isEmpty()) {
            return Optional.empty();
        }
        String names = unmapped.stream().limit(5).collect(Collectors.joining(", "));
        return Optional.of(unmapped.size() + " tax ledgers not mapped: " + names);
    }

    private List<com.arktech.superaccountant.masters.models.UploadLedgerSnapshot> gstLedgerNamesWithoutSavedRow(
            UUID organizationId) {
        Optional<UUID> jobId = mastersJobGate.latestCompletedMastersJob(organizationId);
        if (jobId.isEmpty()) {
            return List.of();
        }
        var mappings = mappingService.mappingsByKey(organizationId);
        return findingOverrideMasterService.snapshotsByName(jobId.get()).values().stream()
                .filter(s -> s.getCategory() == com.arktech.superaccountant.masters.models.LedgerCategory.GST)
                .filter(s -> {
                    String key = s.getLedgerName() == null ? null : s.getLedgerName().trim().toLowerCase();
                    return !mappings.containsKey(key);
                })
                .collect(Collectors.toList());
    }

    private boolean hasGstin(UUID organizationId) {
        return organizationRepository.findById(organizationId)
                .map(org -> org.getGstin() != null && !org.getGstin().isBlank())
                .orElse(false);
    }
}
