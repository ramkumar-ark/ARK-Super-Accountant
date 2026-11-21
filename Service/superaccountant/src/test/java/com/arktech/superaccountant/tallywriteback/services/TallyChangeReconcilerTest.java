package com.arktech.superaccountant.tallywriteback.services;

import com.arktech.superaccountant.masters.models.UploadJob;
import com.arktech.superaccountant.masters.models.UploadLedgerSnapshot;
import com.arktech.superaccountant.masters.repository.UploadJobRepository;
import com.arktech.superaccountant.masters.repository.UploadLedgerSnapshotRepository;
import com.arktech.superaccountant.tallywriteback.models.ExistenceOutcome;
import com.arktech.superaccountant.tallywriteback.models.TallyChangeItem;
import com.arktech.superaccountant.tallywriteback.models.TallyChangeStatus;
import com.arktech.superaccountant.tallywriteback.models.TallyFailureReason;
import com.arktech.superaccountant.tallywriteback.models.TallyField;
import com.arktech.superaccountant.tallywriteback.repository.TallyChangeItemRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TallyChangeReconcilerTest {

    private static final UUID ORG = UUID.randomUUID();
    private static final UUID OTHER_ORG = UUID.randomUUID();
    private static final UUID JOB = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-11-22T09:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private final UploadJobRepository uploadJobRepository = mock(UploadJobRepository.class);
    private final UploadLedgerSnapshotRepository snapshotRepository = mock(UploadLedgerSnapshotRepository.class);
    private final TallyChangeItemRepository itemRepository = mock(TallyChangeItemRepository.class);
    private final TallyChangeReconciler reconciler = new TallyChangeReconciler(
            uploadJobRepository, snapshotRepository, itemRepository, CLOCK);

    private static UploadJob job(UUID organizationId) {
        UploadJob job = new UploadJob();
        job.setId(JOB);
        job.setOrganizationId(organizationId);
        return job;
    }

    private static TallyChangeItem item(UUID organizationId, String tallyGuid, String ledgerName,
                                        String baseline, String newValue, TallyChangeStatus status) {
        TallyChangeItem item = new TallyChangeItem();
        item.setId(UUID.randomUUID());
        item.setOrganizationId(organizationId);
        item.setChangeType(com.arktech.superaccountant.tallywriteback.models.TallyChangeType.ALTER_FIELD);
        item.setField(TallyField.PAN);
        item.setLedgerKey(ledgerName.trim().toLowerCase());
        item.setLedgerName(ledgerName);
        item.setTallyGuid(tallyGuid);
        item.setBaselineValue(baseline);
        item.setNewValue(newValue);
        item.setStatus(status);
        return item;
    }

    private static UploadLedgerSnapshot snapshot(String ledgerName, String tallyGuid, String pan) {
        UploadLedgerSnapshot snapshot = new UploadLedgerSnapshot();
        snapshot.setLedgerName(ledgerName);
        snapshot.setTallyGuid(tallyGuid);
        snapshot.setPan(pan);
        return snapshot;
    }

    private void stubJobAndItems(UUID organizationId, TallyChangeItem... items) {
        when(uploadJobRepository.findById(JOB)).thenReturn(Optional.of(job(organizationId)));
        when(itemRepository.findByOrganizationIdAndStatusInOrderByCreatedAtDesc(eq(organizationId), any()))
                .thenReturn(List.of(items));
    }

    @Test
    void appliedWhenTallyHoldsNewValue() {
        TallyChangeItem item = item(ORG, "guid-1", "Sales", "AAAAA1111A", "BBBBB2222B",
                TallyChangeStatus.PENDING);
        stubJobAndItems(ORG, item);
        when(snapshotRepository.findByUploadJobIdAndTallyGuid(JOB, "guid-1"))
                .thenReturn(Optional.of(snapshot("Sales", "guid-1", " BBBBB2222B ")));

        reconciler.reconcile(JOB);

        assertThat(item.getStatus()).isEqualTo(TallyChangeStatus.APPLIED);
        assertThat(item.getAppliedUploadJobId()).isEqualTo(JOB);
        assertThat(item.getResolvedAt()).isEqualTo(NOW);
        assertThat(item.getFailureReason()).isNull();
        assertThat(item.getObservedValue()).isNull();
    }

    @Test
    void failedItemThatNowHoldsNewValueBecomesAppliedAndClearsFailure() {
        TallyChangeItem item = item(ORG, "guid-1", "Sales", "AAAAA1111A", "BBBBB2222B",
                TallyChangeStatus.FAILED);
        item.setFailureReason(TallyFailureReason.TALLY_VALUE_CHANGED);
        item.setObservedValue("CCCCC3333C");
        stubJobAndItems(ORG, item);
        when(snapshotRepository.findByUploadJobIdAndTallyGuid(JOB, "guid-1"))
                .thenReturn(Optional.of(snapshot("Sales", "guid-1", "BBBBB2222B")));

        reconciler.reconcile(JOB);

        assertThat(item.getStatus()).isEqualTo(TallyChangeStatus.APPLIED);
        assertThat(item.getAppliedUploadJobId()).isEqualTo(JOB);
        assertThat(item.getResolvedAt()).isEqualTo(NOW);
        assertThat(item.getFailureReason()).isNull();
        assertThat(item.getObservedValue()).isNull();
    }

    @Test
    void pendingStaysPendingWhenTallyStillHoldsBaseline() {
        TallyChangeItem item = item(ORG, "guid-1", "Sales", "AAAAA1111A", "BBBBB2222B",
                TallyChangeStatus.PENDING);
        stubJobAndItems(ORG, item);
        when(snapshotRepository.findByUploadJobIdAndTallyGuid(JOB, "guid-1"))
                .thenReturn(Optional.of(snapshot("Sales", "guid-1", " AAAAA1111A ")));

        reconciler.reconcile(JOB);

        assertThat(item.getStatus()).isEqualTo(TallyChangeStatus.PENDING);
        assertThat(item.getAppliedUploadJobId()).isNull();
        assertThat(item.getResolvedAt()).isNull();
        assertThat(item.getFailureReason()).isNull();
    }

    @Test
    void exportedStaysExportedWhenTallyStillHoldsBaseline() {
        TallyChangeItem item = item(ORG, "guid-1", "Sales", "AAAAA1111A", "BBBBB2222B",
                TallyChangeStatus.EXPORTED);
        stubJobAndItems(ORG, item);
        when(snapshotRepository.findByUploadJobIdAndTallyGuid(JOB, "guid-1"))
                .thenReturn(Optional.of(snapshot("Sales", "guid-1", "AAAAA1111A")));

        reconciler.reconcile(JOB);

        assertThat(item.getStatus()).isEqualTo(TallyChangeStatus.EXPORTED);
        assertThat(item.getAppliedUploadJobId()).isNull();
        assertThat(item.getResolvedAt()).isNull();
    }

    @Test
    void nullBaselineAndBlankTallyPanAreUnchanged() {
        TallyChangeItem item = item(ORG, "guid-1", "Sales", null, "BBBBB2222B",
                TallyChangeStatus.PENDING);
        stubJobAndItems(ORG, item);
        when(snapshotRepository.findByUploadJobIdAndTallyGuid(JOB, "guid-1"))
                .thenReturn(Optional.of(snapshot("Sales", "guid-1", "   ")));

        reconciler.reconcile(JOB);

        assertThat(item.getStatus()).isEqualTo(TallyChangeStatus.PENDING);
        assertThat(item.getAppliedUploadJobId()).isNull();
    }

    @Test
    void tallyValueChangedFailsWithObservedValue() {
        TallyChangeItem item = item(ORG, "guid-1", "Sales", "AAAAA1111A", "BBBBB2222B",
                TallyChangeStatus.PENDING);
        stubJobAndItems(ORG, item);
        when(snapshotRepository.findByUploadJobIdAndTallyGuid(JOB, "guid-1"))
                .thenReturn(Optional.of(snapshot("Sales", "guid-1", " CCCCC3333C ")));

        reconciler.reconcile(JOB);

        assertThat(item.getStatus()).isEqualTo(TallyChangeStatus.FAILED);
        assertThat(item.getFailureReason()).isEqualTo(TallyFailureReason.TALLY_VALUE_CHANGED);
        assertThat(item.getObservedValue()).isEqualTo("CCCCC3333C");
        assertThat(item.getAppliedUploadJobId()).isNull();
        assertThat(item.getResolvedAt()).isNull();
    }

    @Test
    void failedStaysFailedWhenTallyStillHoldsBaseline() {
        TallyChangeItem item = item(ORG, "guid-1", "Sales", "AAAAA1111A", "BBBBB2222B",
                TallyChangeStatus.FAILED);
        item.setFailureReason(TallyFailureReason.TALLY_VALUE_CHANGED);
        item.setObservedValue("CCCCC3333C");
        stubJobAndItems(ORG, item);
        when(snapshotRepository.findByUploadJobIdAndTallyGuid(JOB, "guid-1"))
                .thenReturn(Optional.of(snapshot("Sales", "guid-1", "AAAAA1111A")));

        reconciler.reconcile(JOB);

        assertThat(item.getStatus()).isEqualTo(TallyChangeStatus.FAILED);
        assertThat(item.getFailureReason()).isEqualTo(TallyFailureReason.TALLY_VALUE_CHANGED);
        assertThat(item.getObservedValue()).isEqualTo("CCCCC3333C");
        assertThat(item.getAppliedUploadJobId()).isNull();
    }

    @Test
    void missingLedgerFailsWithLedgerMissing() {
        TallyChangeItem item = item(ORG, "guid-1", "Sales", "AAAAA1111A", "BBBBB2222B",
                TallyChangeStatus.PENDING);
        stubJobAndItems(ORG, item);
        when(snapshotRepository.findByUploadJobIdAndTallyGuid(JOB, "guid-1"))
                .thenReturn(Optional.empty());

        reconciler.reconcile(JOB);

        assertThat(item.getStatus()).isEqualTo(TallyChangeStatus.FAILED);
        assertThat(item.getFailureReason()).isEqualTo(TallyFailureReason.LEDGER_MISSING);
        assertThat(item.getObservedValue()).isNull();
        assertThat(item.getAppliedUploadJobId()).isNull();
    }

    @Test
    void guidMatchFollowsLedgerRename() {
        TallyChangeItem item = item(ORG, "guid-1", "Old Sales Name", "AAAAA1111A", "BBBBB2222B",
                TallyChangeStatus.PENDING);
        stubJobAndItems(ORG, item);
        when(snapshotRepository.findByUploadJobIdAndTallyGuid(JOB, "guid-1"))
                .thenReturn(Optional.of(snapshot("New Sales Name", "guid-1", "BBBBB2222B")));

        reconciler.reconcile(JOB);

        assertThat(item.getStatus()).isEqualTo(TallyChangeStatus.APPLIED);
        assertThat(item.getAppliedUploadJobId()).isEqualTo(JOB);
        verify(snapshotRepository, never()).findByUploadJobIdAndNormalizedName(any(), any());
    }

    @Test
    void nameMatchWhenItemHasNoGuid() {
        TallyChangeItem item = item(ORG, null, "  SALES  ", "AAAAA1111A", "BBBBB2222B",
                TallyChangeStatus.PENDING);
        stubJobAndItems(ORG, item);
        when(snapshotRepository.findByUploadJobIdAndNormalizedName(JOB, "sales"))
                .thenReturn(List.of(snapshot("SALES", "guid-new", "BBBBB2222B")));

        reconciler.reconcile(JOB);

        assertThat(item.getStatus()).isEqualTo(TallyChangeStatus.APPLIED);
        assertThat(item.getAppliedUploadJobId()).isEqualTo(JOB);
        verify(snapshotRepository, never()).findByUploadJobIdAndTallyGuid(any(), any());
    }

    @Test
    void appliedAndSupersededItemsAreIgnored() {
        TallyChangeItem applied = item(ORG, "guid-1", "Sales", "AAAAA1111A", "BBBBB2222B",
                TallyChangeStatus.APPLIED);
        TallyChangeItem superseded = item(ORG, "guid-2", "Purchases", "AAAAA1111A", "BBBBB2222B",
                TallyChangeStatus.SUPERSEDED);
        stubJobAndItems(ORG, applied, superseded);

        reconciler.reconcile(JOB);

        assertThat(applied.getStatus()).isEqualTo(TallyChangeStatus.APPLIED);
        assertThat(superseded.getStatus()).isEqualTo(TallyChangeStatus.SUPERSEDED);
        assertThat(applied.getAppliedUploadJobId()).isNull();
        assertThat(superseded.getAppliedUploadJobId()).isNull();
        verifyNoInteractions(snapshotRepository);
    }

    @Test
    void onlyOpenStatusesAreLoaded() {
        stubJobAndItems(ORG);

        reconciler.reconcile(JOB);

        ArgumentCaptor<Collection<TallyChangeStatus>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(itemRepository).findByOrganizationIdAndStatusInOrderByCreatedAtDesc(eq(ORG), captor.capture());
        assertThat(captor.getValue()).containsExactlyInAnyOrder(
                TallyChangeStatus.PENDING, TallyChangeStatus.EXPORTED, TallyChangeStatus.FAILED);
    }

    @Test
    void itemsFromAnotherOrganizationAreNotLoadedOrTouched() {
        TallyChangeItem mine = item(ORG, "guid-1", "Sales", "AAAAA1111A", "BBBBB2222B",
                TallyChangeStatus.PENDING);
        TallyChangeItem foreign = item(OTHER_ORG, "guid-1", "Sales", "AAAAA1111A", "BBBBB2222B",
                TallyChangeStatus.PENDING);
        stubJobAndItems(ORG, mine);
        when(snapshotRepository.findByUploadJobIdAndTallyGuid(JOB, "guid-1"))
                .thenReturn(Optional.of(snapshot("Sales", "guid-1", "BBBBB2222B")));

        reconciler.reconcile(JOB);

        assertThat(mine.getStatus()).isEqualTo(TallyChangeStatus.APPLIED);
        assertThat(foreign.getStatus()).isEqualTo(TallyChangeStatus.PENDING);
        assertThat(foreign.getAppliedUploadJobId()).isNull();
        verify(itemRepository, never())
                .findByOrganizationIdAndStatusInOrderByCreatedAtDesc(eq(OTHER_ORG), any());
    }

    @Test
    void exportCheckFieldsAreLeftAlone() {
        TallyChangeItem item = item(ORG, "guid-1", "Sales", "AAAAA1111A", "BBBBB2222B",
                TallyChangeStatus.PENDING);
        item.setLastCheckOutcome(ExistenceOutcome.FOUND);
        UUID checkJob = UUID.randomUUID();
        item.setLastCheckJobId(checkJob);
        Instant checkedAt = Instant.parse("2026-01-01T00:00:00Z");
        item.setLastCheckedAt(checkedAt);
        stubJobAndItems(ORG, item);
        when(snapshotRepository.findByUploadJobIdAndTallyGuid(JOB, "guid-1"))
                .thenReturn(Optional.of(snapshot("Sales", "guid-1", "CCCCC3333C")));

        reconciler.reconcile(JOB);

        assertThat(item.getLastCheckOutcome()).isEqualTo(ExistenceOutcome.FOUND);
        assertThat(item.getLastCheckJobId()).isEqualTo(checkJob);
        assertThat(item.getLastCheckedAt()).isEqualTo(checkedAt);
    }

    @Test
    void missingUploadJobDoesNothing() {
        when(uploadJobRepository.findById(JOB)).thenReturn(Optional.empty());

        reconciler.reconcile(JOB);

        verifyNoInteractions(itemRepository, snapshotRepository);
    }
}
