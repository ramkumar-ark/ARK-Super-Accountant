package com.arktech.superaccountant.tallywriteback.services;

import com.arktech.superaccountant.masters.pan.PanChangeRecorder;
import com.arktech.superaccountant.masters.pan.PanFormat;
import com.arktech.superaccountant.tallywriteback.models.ExistenceOutcome;
import com.arktech.superaccountant.tallywriteback.models.TallyChangeItem;
import com.arktech.superaccountant.tallywriteback.models.TallyChangeSourceType;
import com.arktech.superaccountant.tallywriteback.models.TallyChangeStatus;
import com.arktech.superaccountant.tallywriteback.models.TallyChangeType;
import com.arktech.superaccountant.tallywriteback.models.TallyField;
import com.arktech.superaccountant.tallywriteback.payload.LedgerLookup;
import com.arktech.superaccountant.tallywriteback.repository.TallyChangeItemRepository;
import com.arktech.superaccountant.tds.engine.TdsEngine;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class TallyChangeRecorder implements PanChangeRecorder {

    private final TallyLedgerDirectory directory;
    private final TallyChangeItemRepository repository;

    public TallyChangeRecorder(TallyLedgerDirectory directory, TallyChangeItemRepository repository) {
        this.directory = directory;
        this.repository = repository;
    }

    @Override
    public void onPanSaved(UUID organizationId, String ledgerName, String pan, UUID overrideId,
                           UUID sourceFindingId, String approvedBy, Instant approvedAt) {
        LedgerLookup lookup = directory.lookup(organizationId, null, ledgerName);
        String baseline = lookup.tallyPan();
        UUID baselineUploadJobId = baseline == null ? null : lookup.mastersJobId();
        String tallyGuid = lookup.outcome() == ExistenceOutcome.NOT_FOUND ? null : lookup.tallyGuid();
        String newValue = PanFormat.normalize(pan);
        String ledgerKey = TdsEngine.key(ledgerName);

        List<TallyChangeItem> open = repository.findOpenForUpdate(organizationId, ledgerKey, TallyField.PAN);

        if (canonical(newValue).equals(canonical(baseline))) {
            // One open item per (organization, ledger key, field): supersede before returning.
            for (TallyChangeItem item : open) {
                item.setStatus(TallyChangeStatus.SUPERSEDED);
                item.setSupersededBy(null);
                item.setResolvedAt(approvedAt);
            }
            return;
        }

        for (TallyChangeItem item : open) {
            if (canonical(newValue).equals(canonical(item.getNewValue()))) {
                return;
            }
        }

        TallyChangeItem created = new TallyChangeItem();
        created.setOrganizationId(organizationId);
        created.setChangeType(TallyChangeType.ALTER_FIELD);
        created.setField(TallyField.PAN);
        created.setLedgerKey(ledgerKey);
        created.setLedgerName(ledgerName);
        created.setTallyGuid(tallyGuid);
        created.setBaselineValue(baseline);
        created.setBaselineUploadJobId(baselineUploadJobId);
        created.setNewValue(newValue);
        created.setSourceType(TallyChangeSourceType.PAN_OVERRIDE);
        created.setSourceOverrideId(overrideId);
        created.setSourceFindingId(sourceFindingId);
        created.setApprovedBy(approvedBy);
        created.setApprovedAt(approvedAt);
        created.setStatus(TallyChangeStatus.PENDING);
        created.setCreatedAt(Instant.now());
        created = repository.save(created);

        // One open item per (organization, ledger key, field): supersede the previous ones.
        for (TallyChangeItem item : open) {
            item.setStatus(TallyChangeStatus.SUPERSEDED);
            item.setSupersededBy(created.getId());
            item.setResolvedAt(approvedAt);
        }
    }

    /** Null and blank mean the same "no PAN" for comparison, while the stored new value stays as-is. */
    private static String canonical(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.trim();
    }
}
