package com.arktech.superaccountant.gst.mapping;

import java.time.Instant;
import java.util.UUID;

import com.arktech.superaccountant.masters.models.LedgerCategory;
import com.arktech.superaccountant.masters.services.FindingOverrideMasterService;

public final class TaxMappingTestData {

    private TaxMappingTestData() {
    }

    /** Standard GST ledger names covering every required area for each of IGST, CGST, SGST. */
    public static java.util.List<String> standardLedgerNames() {
        java.util.List<String> names = new java.util.ArrayList<>();
        for (String head : java.util.List.of("IGST", "CGST", "SGST")) {
            names.add("Output " + head);
            names.add("Input " + head);
            names.add(head + " Cash Balance A/c");
            names.add(head + " Liability Balance A/c");
            names.add(head + " Credit Balance A/c");
            names.add(head + " TDS");
        }
        return names;
    }

    /** JSON items mapping every standard ledger to its role and head, for the save API. */
    public static String standardMappingItemsJson() {
        return standardLedgerNames().stream().map(name -> {
            TaxLedgerSuggester.Suggestion suggestion = TaxLedgerSuggester.suggest(name);
            return "{\"ledgerName\":\"" + name + "\",\"role\":\"" + suggestion.role()
                    + "\",\"head\":\"" + suggestion.head() + "\"}";
        }).collect(java.util.stream.Collectors.joining(",", "{\"items\":[", "]}"));
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
