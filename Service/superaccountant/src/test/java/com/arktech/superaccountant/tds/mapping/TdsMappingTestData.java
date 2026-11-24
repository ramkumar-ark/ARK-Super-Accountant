package com.arktech.superaccountant.tds.mapping;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.arktech.superaccountant.masters.models.UploadLedgerSnapshot;
import com.arktech.superaccountant.masters.repository.UploadLedgerSnapshotRepository;
import com.arktech.superaccountant.tds.models.TdsSectionConfig;
import com.arktech.superaccountant.tds.repository.TdsSectionConfigRepository;

public final class TdsMappingTestData {

    private TdsMappingTestData() {
    }

    private record Seed(String name, String parentGroup, String groupChain, boolean tdsApplicable) {
    }

    private static List<Seed> narayananSeeds() {
        return List.of(
                new Seed("TDS Payable 194C", "Direct Expenses",
                        "Expenses|Direct Expenses|Duties & Taxes", false),
                new Seed("TDS Receivable 206A", "Current Assets",
                        "Current Assets|Loans & Advances", false),
                new Seed("Rent", "Indirect Expenses",
                        "Indirect Expenses|Duties & Taxes", true));
    }

    /** Narayanan-shaped TDS payable ledger names. */
    public static List<String> narayananPayableNames() {
        return List.of("TDS Payable 194C", "Rent");
    }

    /** Narayanan-shaped TDS receivable ledger names. */
    public static List<String> narayananReceivableNames() {
        return List.of("TDS Receivable 206A");
    }

    /** Seeds the Narayanan-shaped masters snapshots for a (completed) masters job. */
    public static void seedNarayananSnapshots(UUID organizationId, UUID mastersJobId,
            UploadLedgerSnapshotRepository snapshotRepository) {
        for (Seed seed : narayananSeeds()) {
            UploadLedgerSnapshot snapshot = new UploadLedgerSnapshot();
            snapshot.setUploadJobId(mastersJobId);
            snapshot.setOrganizationId(organizationId);
            snapshot.setLedgerName(seed.name());
            snapshot.setParentGroup(seed.parentGroup());
            snapshot.setGroupChain(seed.groupChain());
            snapshot.setTdsApplicable(seed.tdsApplicable());
            snapshotRepository.save(snapshot);
        }
    }

    /** JSON items mapping every Narayanan TDS ledger to its suggested role and section, for the save API. */
    public static String mappingItemsJson(Set<String> sectionCodes) {
        List<String> items = new ArrayList<>();
        for (Seed seed : narayananSeeds()) {
            UploadLedgerSnapshot snapshot = new UploadLedgerSnapshot();
            snapshot.setLedgerName(seed.name());
            snapshot.setParentGroup(seed.parentGroup());
            snapshot.setGroupChain(seed.groupChain());
            TdsLedgerSuggester.Suggestion suggestion = TdsLedgerSuggester.suggest(snapshot, sectionCodes);
            String section = suggestion.section() == null
                    ? ""
                    : ",\"section\":\"" + suggestion.section() + "\"";
            items.add("{\"ledgerName\":\"" + seed.name() + "\",\"role\":\"" + suggestion.role()
                    + "\"" + section + "}");
        }
        return items.stream().collect(Collectors.joining(",", "{\"items\":[", "]}"));
    }

    /** Bulk-seeds a mapping row for every TDS candidate of the masters job, using the suggester. */
    public static void mapAll(UUID organizationId, UUID mastersJobId,
            UploadLedgerSnapshotRepository snapshotRepository,
            TdsLedgerMappingRepository repository,
            TdsSectionConfigRepository sectionConfigRepository) {
        Set<String> sectionCodes = sectionConfigRepository.findAll().stream()
                .map(TdsSectionConfig::getSectionCode)
                .collect(Collectors.toSet());
        for (UploadLedgerSnapshot snapshot : snapshotRepository.findByUploadJobId(mastersJobId)) {
            if (!TdsLedgerSuggester.isCandidate(snapshot)) {
                continue;
            }
            TdsLedgerSuggester.Suggestion suggestion = TdsLedgerSuggester.suggest(snapshot, sectionCodes);
            TdsLedgerMapping mapping = new TdsLedgerMapping();
            mapping.setOrganizationId(organizationId);
            mapping.setLedgerName(snapshot.getLedgerName());
            mapping.setLedgerKey(TdsLedgerMappingService.ledgerKey(snapshot.getLedgerName()));
            mapping.setRole(suggestion.role() == null ? TdsLedgerRole.NOT_TDS : suggestion.role());
            mapping.setSection(suggestion.section());
            mapping.setSavedBy("test");
            mapping.setSavedAt(Instant.now());
            repository.save(mapping);
        }
    }
}
