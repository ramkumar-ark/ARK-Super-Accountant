package com.arktech.superaccountant.tds.mapping;

import com.arktech.superaccountant.masters.models.UploadLedgerSnapshot;
import com.arktech.superaccountant.masters.repository.UploadLedgerSnapshotRepository;
import com.arktech.superaccountant.tds.mapping.payload.SaveTdsLedgerMappingRequest;
import com.arktech.superaccountant.tds.mapping.payload.TdsLedgerMappingView;
import com.arktech.superaccountant.tds.models.TdsSectionConfig;
import com.arktech.superaccountant.tds.repository.TdsSectionConfigRepository;
import com.arktech.superaccountant.workflow.seam.MastersJobGate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TdsLedgerMappingService {

    private final TdsLedgerMappingRepository repository;

    private final MastersJobGate mastersJobGate;

    private final UploadLedgerSnapshotRepository snapshotRepository;

    private final TdsSectionConfigRepository sectionConfigRepository;

    private final ApplicationEventPublisher eventPublisher;

    public TdsLedgerMappingService(TdsLedgerMappingRepository repository,
            MastersJobGate mastersJobGate,
            UploadLedgerSnapshotRepository snapshotRepository,
            TdsSectionConfigRepository sectionConfigRepository,
            ApplicationEventPublisher eventPublisher) {
        this.repository = repository;
        this.mastersJobGate = mastersJobGate;
        this.snapshotRepository = snapshotRepository;
        this.sectionConfigRepository = sectionConfigRepository;
        this.eventPublisher = eventPublisher;
    }

    public TdsLedgerMappingView view(UUID organizationId, TdsLedgerMappingGate gate) {
        Map<String, TdsLedgerMapping> mappings = mappingsByKey(organizationId);
        List<TdsLedgerMappingView.Row> rows = rows(organizationId, mappings);
        List<String> receivableWarnings = warnings(organizationId, mappings);

        return new TdsLedgerMappingView(
                gate.isComplete(organizationId),
                mastersJobGate.latestCompletedMastersJob(organizationId).orElse(null),
                gate.blockerMessage(organizationId).map(List::of).orElseGet(List::of),
                receivableWarnings,
                rows);
    }

    private List<TdsLedgerMappingView.Row> rows(UUID organizationId, Map<String, TdsLedgerMapping> mappings) {
        Map<String, UploadLedgerSnapshot> candidates = candidateSnapshots(organizationId);
        Set<String> sectionCodes = sectionCodes();
        List<TdsLedgerMappingView.Row> rows = new ArrayList<>();
        for (Map.Entry<String, UploadLedgerSnapshot> entry : candidates.entrySet()) {
            UploadLedgerSnapshot snapshot = entry.getValue();
            TdsLedgerMapping saved = mappings.get(entry.getKey());
            TdsLedgerSuggester.Suggestion suggestion = TdsLedgerSuggester.suggest(snapshot, sectionCodes);
            TdsLedgerRole role = saved != null ? saved.getRole() : suggestion.role();
            boolean receivable = role == TdsLedgerRole.TDS_RECEIVABLE
                    || (role == TdsLedgerRole.NOT_TDS && suggestion.role() == TdsLedgerRole.TDS_RECEIVABLE);
            TdsLedgerArea area = receivable
                    ? TdsLedgerArea.RECEIVABLE
                    : TdsLedgerArea.PAYABLE;
            rows.add(new TdsLedgerMappingView.Row(
                    snapshot.getLedgerName(),
                    snapshot.getParentGroup(),
                    saved != null,
                    role,
                    saved != null ? saved.getSection() : null,
                    suggestion.role(),
                    suggestion.section(),
                    area,
                    saved != null ? saved.getSavedBy() : null,
                    saved != null ? saved.getSavedAt() : null));
        }
        rows.sort(Comparator.comparing(TdsLedgerMappingView.Row::ledgerName, String.CASE_INSENSITIVE_ORDER));
        return rows;
    }

    private List<String> warnings(UUID organizationId, Map<String, TdsLedgerMapping> mappings) {
        List<String> unmapped = unmappedReceivableLedgers(organizationId, mappings);
        if (unmapped.isEmpty()) {
            return List.of();
        }
        return List.of(unmapped.size() + " TDS receivable ledgers not mapped - receivable checks skip them until mapped");
    }

    List<String> unmappedReceivableLedgers(UUID organizationId) {
        return unmappedReceivableLedgers(organizationId, mappingsByKey(organizationId));
    }

    private List<String> unmappedReceivableLedgers(UUID organizationId, Map<String, TdsLedgerMapping> mappings) {
        Set<String> sectionCodes = sectionCodes();
        return candidateSnapshots(organizationId).values().stream()
                .filter(s -> !mappings.containsKey(ledgerKey(s.getLedgerName())))
                .filter(s -> TdsLedgerSuggester.suggest(s, sectionCodes).role() == TdsLedgerRole.TDS_RECEIVABLE)
                .map(UploadLedgerSnapshot::getLedgerName)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toList());
    }

    @Transactional
    public void save(UUID organizationId, String actor, SaveTdsLedgerMappingRequest request) {
        if (request == null || request.items() == null || request.items().isEmpty()) {
            throw new IllegalArgumentException("No TDS ledger mapping items provided.");
        }

        Map<String, UploadLedgerSnapshot> candidates = candidateSnapshots(organizationId);
        Set<String> sectionCodes = sectionCodes();

        for (SaveTdsLedgerMappingRequest.Item item : request.items()) {
            if (item.ledgerName() == null || item.ledgerName().isBlank()) {
                throw new IllegalArgumentException("Ledger name is required.");
            }
            String key = ledgerKey(item.ledgerName());
            if (!candidates.containsKey(key)) {
                throw new IllegalArgumentException("Unknown TDS ledger: " + item.ledgerName());
            }
            if (item.role() == null) {
                throw new IllegalArgumentException("TDS ledger role is required for: " + item.ledgerName());
            }
            if (item.section() != null && item.role() != TdsLedgerRole.TDS_PAYABLE) {
                throw new IllegalArgumentException("Section must be empty when role is not TDS payable.");
            }
            if (item.section() != null && !sectionCodes.contains(item.section())) {
                throw new IllegalArgumentException("Unknown TDS section: " + item.section());
            }

            TdsLedgerMapping mapping = repository.findByOrganizationIdAndLedgerKey(organizationId, key)
                    .orElseGet(TdsLedgerMapping::new);
            mapping.setOrganizationId(organizationId);
            mapping.setLedgerName(candidates.get(key).getLedgerName());
            mapping.setLedgerKey(key);
            mapping.setRole(item.role());
            mapping.setSection(item.section());
            mapping.setSavedBy(actor);
            mapping.setSavedAt(Instant.now());
            repository.save(mapping);
        }

        eventPublisher.publishEvent(new TdsLedgerMappingSavedEvent(organizationId));
    }

    public Map<String, TdsLedgerMapping> mappingsByKey(UUID organizationId) {
        Map<String, TdsLedgerMapping> byKey = new TreeMap<>();
        for (TdsLedgerMapping mapping : repository.findByOrganizationId(organizationId)) {
            byKey.put(mapping.getLedgerKey(), mapping);
        }
        return byKey;
    }

    public Set<String> tdsLedgerKeys(UUID organizationId) {
        return keysWithRole(organizationId, role -> role != TdsLedgerRole.NOT_TDS);
    }

    public Set<String> receivableLedgerKeys(UUID organizationId) {
        return keysWithRole(organizationId, role -> role == TdsLedgerRole.TDS_RECEIVABLE);
    }

    private Set<String> keysWithRole(UUID organizationId, java.util.function.Predicate<TdsLedgerRole> roleFilter) {
        return repository.findByOrganizationId(organizationId).stream()
                .filter(m -> roleFilter.test(m.getRole()))
                .map(TdsLedgerMapping::getLedgerKey)
                .collect(Collectors.toSet());
    }

    private Map<String, UploadLedgerSnapshot> candidateSnapshots(UUID organizationId) {
        Optional<UUID> jobId = mastersJobGate.latestCompletedMastersJob(organizationId);
        if (jobId.isEmpty()) {
            return Map.of();
        }
        Map<String, UploadLedgerSnapshot> candidates = new LinkedHashMap<>();
        for (UploadLedgerSnapshot snapshot : snapshotRepository.findByUploadJobId(jobId.get())) {
            if (TdsLedgerSuggester.isCandidate(snapshot)) {
                candidates.putIfAbsent(ledgerKey(snapshot.getLedgerName()), snapshot);
            }
        }
        return candidates;
    }

    private Set<String> sectionCodes() {
        return sectionConfigRepository.findAll().stream()
                .map(TdsSectionConfig::getSectionCode)
                .collect(Collectors.toSet());
    }

    static String ledgerKey(String ledgerName) {
        return ledgerName == null ? null : ledgerName.trim().toLowerCase(Locale.ROOT);
    }
}
