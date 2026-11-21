package com.arktech.superaccountant.tallywriteback.services;

import com.arktech.superaccountant.tallywriteback.models.TallyChangeItem;
import com.arktech.superaccountant.tallywriteback.models.TallyChangeStatus;
import com.arktech.superaccountant.tallywriteback.models.TallyChangeType;
import com.arktech.superaccountant.tallywriteback.models.TallyField;
import com.arktech.superaccountant.tallywriteback.repository.TallyChangeItemRepository;
import com.arktech.superaccountant.workflow.snapshot.AppliedPanChange;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class AppliedPanChangeLookup
        implements com.arktech.superaccountant.workflow.snapshot.AppliedChangeLookup {

    private final TallyChangeItemRepository itemRepository;

    @Autowired
    public AppliedPanChangeLookup(TallyChangeItemRepository itemRepository) {
        this.itemRepository = itemRepository;
    }

    @Override
    public List<AppliedPanChange> appliedPanChanges(UUID uploadJobId) {
        if (uploadJobId == null) {
            return List.of();
        }
        return itemRepository.findByAppliedUploadJobIdAndStatusAndFieldAndChangeType(
                        uploadJobId, TallyChangeStatus.APPLIED, TallyField.PAN, TallyChangeType.ALTER_FIELD)
                .stream()
                .map(item -> new AppliedPanChange(item.getLedgerKey(), item.getBaselineValue(),
                        item.getNewValue()))
                .toList();
    }
}
