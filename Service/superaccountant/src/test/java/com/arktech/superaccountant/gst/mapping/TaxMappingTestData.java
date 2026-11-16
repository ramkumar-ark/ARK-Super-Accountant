package com.arktech.superaccountant.gst.mapping;

import java.time.Instant;
import java.util.UUID;

import com.arktech.superaccountant.masters.models.LedgerCategory;
import com.arktech.superaccountant.masters.services.FindingOverrideMasterService;

public final class TaxMappingTestData {

    private TaxMappingTestData() {
    }

    public static void mapAll(UUID organizationId, UUID mastersJobId, TaxLedgerMappingRepository repository,
            FindingOverrideMasterService mastersService) {
        mastersService.snapshotsByName(mastersJobId).values().stream()
                .filter(snapshot -> snapshot.getCategory() == LedgerCategory.GST)
                .forEach(snapshot -> {
                    TaxLedgerSuggester.Suggestion suggestion = TaxLedgerSuggester.suggest(snapshot.getLedgerName());
                    TaxLedgerMapping mapping = new TaxLedgerMapping();
                    mapping.setOrganizationId(organizationId);
                    mapping.setLedgerName(snapshot.getLedgerName());
                    mapping.setLedgerKey(TaxLedgerMappingService.ledgerKey(snapshot.getLedgerName()));
                    mapping.setRole(suggestion.role());
                    mapping.setHead(suggestion.head());
                    mapping.setSavedBy("test");
                    mapping.setSavedAt(Instant.now());
                    repository.save(mapping);
                });
    }
}
