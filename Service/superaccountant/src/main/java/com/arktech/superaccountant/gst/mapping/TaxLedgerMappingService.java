package com.arktech.superaccountant.gst.mapping;

import com.arktech.superaccountant.gst.mapping.payload.SaveTaxLedgerMappingRequest;
import com.arktech.superaccountant.gst.mapping.payload.TaxLedgerMappingView;
import com.arktech.superaccountant.masters.models.UploadLedgerSnapshot;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaxLedgerMappingService {

    @Autowired
    private TaxLedgerMappingRepository repository;

    @Autowired
    private TaxLedgerSnapshotSource snapshotSource;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    public TaxLedgerMappingView view(UUID organizationId, TaxLedgerMappingGate gate) {
        Map<String, TaxLedgerMapping> mappings = mappingsByKey(organizationId);
        List<TaxLedgerMappingView.Row> rows = rows(organizationId, mappings);

        return new TaxLedgerMappingView(
                gate.isComplete(organizationId),
                snapshotSource.latestCompletedMastersJob(organizationId).orElse(null),
                gate.blockerMessage(organizationId).map(List::of).orElseGet(List::of),
                rows);
    }

    private List<TaxLedgerMappingView.Row> rows(UUID organizationId, Map<String, TaxLedgerMapping> mappings) {
        Map<String, UploadLedgerSnapshot> snapshots = snapshotSource.gstSnapshotsByName(organizationId);
        List<TaxLedgerMappingView.Row> rows = new ArrayList<>();
        for (Map.Entry<String, UploadLedgerSnapshot> entry : snapshots.entrySet()) {
            UploadLedgerSnapshot snapshot = entry.getValue();
            TaxLedgerMapping saved = mappings.get(entry.getKey());
            TaxLedgerSuggester.Suggestion suggestion = TaxLedgerSuggester.suggest(snapshot.getLedgerName());
            rows.add(new TaxLedgerMappingView.Row(
                    snapshot.getLedgerName(),
                    snapshot.getParentGroup(),
                    saved != null,
                    saved != null ? saved.getRole() : null,
                    saved != null ? saved.getHead() : null,
                    suggestion.role(),
                    suggestion.head(),
                    saved != null ? saved.getSavedBy() : null,
                    saved != null ? saved.getSavedAt() : null));
        }
        rows.sort(Comparator.comparing(TaxLedgerMappingView.Row::ledgerName, String.CASE_INSENSITIVE_ORDER));
        return rows;
    }

    @Transactional
    public void save(UUID organizationId, String actor, SaveTaxLedgerMappingRequest request) {
        if (request == null || request.items() == null) {
            throw new IllegalArgumentException("No tax ledger mapping items provided.");
        }

        Map<String, UploadLedgerSnapshot> gstLedgers = snapshotSource.gstSnapshotsByName(organizationId);

        for (SaveTaxLedgerMappingRequest.Item item : request.items()) {
            if (item.ledgerName() == null || item.ledgerName().isBlank()) {
                throw new IllegalArgumentException("Ledger name is required.");
            }
            String key = ledgerKey(item.ledgerName());
            if (!gstLedgers.containsKey(key)) {
                throw new IllegalArgumentException("Unknown GST ledger: " + item.ledgerName());
            }
            if (item.role() == null) {
                throw new IllegalArgumentException("Tax ledger role is required for: " + item.ledgerName());
            }
            if (item.role() == TaxLedgerRole.NOT_TAX) {
                if (item.head() != null) {
                    throw new IllegalArgumentException(
                            "Head must be empty when role is NOT_TAX: " + item.ledgerName());
                }
            } else if (item.head() == null) {
                throw new IllegalArgumentException("Head is required for: " + item.ledgerName());
            }

            TaxLedgerMapping mapping = repository.findByOrganizationIdAndLedgerKey(organizationId, key)
                    .orElseGet(TaxLedgerMapping::new);
            mapping.setOrganizationId(organizationId);
            mapping.setLedgerName(gstLedgers.get(key).getLedgerName());
            mapping.setLedgerKey(key);
            mapping.setRole(item.role());
            mapping.setHead(item.head());
            mapping.setSavedBy(actor);
            mapping.setSavedAt(Instant.now());
            repository.save(mapping);
        }

        eventPublisher.publishEvent(new TaxLedgerMappingSavedEvent(organizationId));
    }

    public Map<String, TaxLedgerMapping> mappingsByKey(UUID organizationId) {
        Map<String, TaxLedgerMapping> byKey = new TreeMap<>();
        for (TaxLedgerMapping mapping : repository.findByOrganizationId(organizationId)) {
            byKey.put(mapping.getLedgerKey(), mapping);
        }
        return byKey;
    }

    static String ledgerKey(String ledgerName) {
        return ledgerName == null ? null : ledgerName.trim().toLowerCase();
    }
}
