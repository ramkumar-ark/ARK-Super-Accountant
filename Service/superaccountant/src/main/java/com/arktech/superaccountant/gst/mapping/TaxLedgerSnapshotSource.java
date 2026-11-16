package com.arktech.superaccountant.gst.mapping;

import com.arktech.superaccountant.masters.models.LedgerCategory;
import com.arktech.superaccountant.masters.models.UploadLedgerSnapshot;
import com.arktech.superaccountant.masters.services.FindingOverrideMasterService;
import com.arktech.superaccountant.workflow.seam.MastersJobGate;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class TaxLedgerSnapshotSource {

    private final MastersJobGate mastersJobGate;

    private final FindingOverrideMasterService findingOverrideMasterService;

    public TaxLedgerSnapshotSource(MastersJobGate mastersJobGate,
            FindingOverrideMasterService findingOverrideMasterService) {
        this.mastersJobGate = mastersJobGate;
        this.findingOverrideMasterService = findingOverrideMasterService;
    }

    public Optional<UUID> latestCompletedMastersJob(UUID organizationId) {
        return mastersJobGate.latestCompletedMastersJob(organizationId);
    }

    public Map<String, UploadLedgerSnapshot> gstSnapshotsByName(UUID organizationId) {
        Optional<UUID> jobId = mastersJobGate.latestCompletedMastersJob(organizationId);
        if (jobId.isEmpty()) {
            return Map.of();
        }
        return findingOverrideMasterService.snapshotsByName(jobId.get()).entrySet().stream()
                .filter(e -> e.getValue().getCategory() == LedgerCategory.GST)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
