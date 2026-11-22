package com.arktech.superaccountant.tallywriteback.services;

import com.arktech.superaccountant.masters.models.UploadJob;
import com.arktech.superaccountant.masters.models.UploadLedgerSnapshot;
import com.arktech.superaccountant.masters.repository.UploadJobRepository;
import com.arktech.superaccountant.masters.repository.UploadLedgerSnapshotRepository;
import com.arktech.superaccountant.masters.util.LedgerNames;
import com.arktech.superaccountant.tallywriteback.models.TallyChangeItem;
import com.arktech.superaccountant.tallywriteback.models.TallyChangeStatus;
import com.arktech.superaccountant.tallywriteback.models.TallyFailureReason;
import com.arktech.superaccountant.tallywriteback.repository.TallyChangeItemRepository;
import com.arktech.superaccountant.workflow.snapshot.TallyUploadReconciliation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class TallyChangeReconciler implements TallyUploadReconciliation {

    private static final List<TallyChangeStatus> RECONCILABLE = List.of(
            TallyChangeStatus.PENDING, TallyChangeStatus.EXPORTED, TallyChangeStatus.FAILED);

    private final UploadJobRepository uploadJobRepository;
    private final UploadLedgerSnapshotRepository snapshotRepository;
    private final TallyChangeItemRepository itemRepository;
    private final Clock clock;

    @Autowired
    public TallyChangeReconciler(UploadJobRepository uploadJobRepository,
                                 UploadLedgerSnapshotRepository snapshotRepository,
                                 TallyChangeItemRepository itemRepository) {
        this(uploadJobRepository, snapshotRepository, itemRepository, Clock.systemUTC());
    }

    TallyChangeReconciler(UploadJobRepository uploadJobRepository,
                          UploadLedgerSnapshotRepository snapshotRepository,
                          TallyChangeItemRepository itemRepository,
                          Clock clock) {
        this.uploadJobRepository = uploadJobRepository;
        this.snapshotRepository = snapshotRepository;
        this.itemRepository = itemRepository;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reconcile(UUID uploadJobId) {
        UploadJob job = uploadJobRepository.findById(uploadJobId).orElse(null);
        if (job == null) {
            return;
        }
        List<TallyChangeItem> items = itemRepository
                .findByOrganizationIdAndStatusInOrderByCreatedAtDesc(job.getOrganizationId(), RECONCILABLE);
        for (TallyChangeItem item : items) {
            if (!RECONCILABLE.contains(item.getStatus())) {
                continue;
            }
            reconcile(item, snapshot(item, uploadJobId), uploadJobId);
        }
    }

    private Optional<UploadLedgerSnapshot> snapshot(TallyChangeItem item, UUID uploadJobId) {
        String tallyGuid = trimToNull(item.getTallyGuid());
        if (tallyGuid != null) {
            Optional<UploadLedgerSnapshot> byGuid =
                    snapshotRepository.findByUploadJobIdAndTallyGuid(uploadJobId, tallyGuid);
            if (byGuid.isPresent()) {
                return byGuid;
            }
        }
        // Found by GUID, else name: a ledger deleted and recreated in Tally keeps its name but gets a new GUID.
        return snapshotRepository.findByUploadJobIdAndNormalizedName(
                        uploadJobId, LedgerNames.normalize(item.getLedgerName()))
                .stream()
                .findFirst();
    }

    private void reconcile(TallyChangeItem item, Optional<UploadLedgerSnapshot> snapshot, UUID uploadJobId) {
        if (snapshot.isEmpty()) {
            fail(item, TallyFailureReason.LEDGER_MISSING, null);
            return;
        }
        String tallyPan = trimToNull(snapshot.get().getPan());
        if (Objects.equals(tallyPan, trimToNull(item.getNewValue()))) {
            item.setStatus(TallyChangeStatus.APPLIED);
            item.setAppliedUploadJobId(uploadJobId);
            item.setResolvedAt(clock.instant());
            item.setFailureReason(null);
            item.setObservedValue(null);
            return;
        }
        if (Objects.equals(tallyPan, trimToNull(item.getBaselineValue()))) {
            return;
        }
        fail(item, TallyFailureReason.TALLY_VALUE_CHANGED, tallyPan);
    }

    private void fail(TallyChangeItem item, TallyFailureReason reason, String observedValue) {
        item.setStatus(TallyChangeStatus.FAILED);
        item.setFailureReason(reason);
        item.setObservedValue(observedValue);
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
