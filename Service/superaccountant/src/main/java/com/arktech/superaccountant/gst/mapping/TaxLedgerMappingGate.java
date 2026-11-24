package com.arktech.superaccountant.gst.mapping;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class TaxLedgerMappingGate {

    private static final String NO_MASTERS_BLOCKER = "No masters upload: tax ledgers cannot be mapped";

    private static final String NO_GST_LEDGERS_BLOCKER = "No GST tax ledgers found - check your masters";

    private static final List<GstHead> REQUIRED_HEADS = List.of(GstHead.IGST, GstHead.CGST, GstHead.SGST);

    private static final Map<TaxLedgerRole, String> REQUIRED_AREAS = new LinkedHashMap<>();

    static {
        REQUIRED_AREAS.put(TaxLedgerRole.OUTPUT, "Output");
        REQUIRED_AREAS.put(TaxLedgerRole.INPUT, "Input");
        REQUIRED_AREAS.put(TaxLedgerRole.CASH_BALANCE, "Cash balance");
        REQUIRED_AREAS.put(TaxLedgerRole.LIABILITY_BALANCE, "Liability balance");
        REQUIRED_AREAS.put(TaxLedgerRole.CREDIT_BALANCE, "Credit balance");
        REQUIRED_AREAS.put(TaxLedgerRole.GST_TDS, "GST TDS");
    }

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

    public boolean isGstApplicable(UUID organizationId) {
        return hasGstin(organizationId);
    }

    public Optional<String> daybookBlockerMessage(UUID organizationId) {
        if (!hasGstin(organizationId)) {
            return Optional.empty();
        }
        return blockerMessage(organizationId);
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
        if (!unmapped.isEmpty()) {
            String names = unmapped.stream().limit(5).collect(Collectors.joining(", "));
            return Optional.of(unmapped.size() + " tax ledgers not mapped: " + names);
        }
        if (!hasGstin(organizationId)) {
            return Optional.empty();
        }
        return missingAreaHeads(organizationId, jobId.get());
    }

    /** A GSTIN company needs, per required area, at least one mapped ledger for each of IGST, CGST, SGST. */
    private Optional<String> missingAreaHeads(UUID organizationId, UUID jobId) {
        Set<String> gstKeys = findingOverrideMasterService.snapshotsByName(jobId).values().stream()
                .filter(s -> s.getCategory() == com.arktech.superaccountant.masters.models.LedgerCategory.GST)
                .map(s -> s.getLedgerName() == null ? null : s.getLedgerName().trim().toLowerCase())
                .collect(Collectors.toSet());
        if (gstKeys.isEmpty()) {
            return Optional.of(NO_GST_LEDGERS_BLOCKER);
        }
        Set<String> covered = new HashSet<>();
        mappingService.mappingsByKey(organizationId).forEach((key, mapping) -> {
            if (gstKeys.contains(key) && mapping.getHead() != null) {
                covered.add(mapping.getRole() + "/" + mapping.getHead());
            }
        });
        List<String> missing = new ArrayList<>();
        for (Map.Entry<TaxLedgerRole, String> area : REQUIRED_AREAS.entrySet()) {
            for (GstHead head : REQUIRED_HEADS) {
                if (!covered.contains(area.getKey() + "/" + head)) {
                    missing.add(area.getValue() + " " + head);
                }
            }
        }
        if (missing.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of("Missing GST ledger mappings: " + String.join(", ", missing));
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
