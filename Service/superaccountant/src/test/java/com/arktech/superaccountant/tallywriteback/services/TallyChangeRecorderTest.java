package com.arktech.superaccountant.tallywriteback.services;

import com.arktech.superaccountant.tallywriteback.models.ExistenceOutcome;
import com.arktech.superaccountant.tallywriteback.models.TallyChangeItem;
import com.arktech.superaccountant.tallywriteback.models.TallyChangeSourceType;
import com.arktech.superaccountant.tallywriteback.models.TallyChangeStatus;
import com.arktech.superaccountant.tallywriteback.models.TallyChangeType;
import com.arktech.superaccountant.tallywriteback.models.TallyField;
import com.arktech.superaccountant.tallywriteback.payload.LedgerLookup;
import com.arktech.superaccountant.tallywriteback.repository.TallyChangeItemRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TallyChangeRecorderTest {

    private static final UUID ORG = UUID.randomUUID();
    private static final UUID JOB = UUID.randomUUID();
    private static final UUID OVERRIDE = UUID.randomUUID();
    private static final UUID FINDING = UUID.randomUUID();
    private static final Instant AT = Instant.parse("2026-11-21T10:00:00Z");

    private final TallyLedgerDirectory directory = mock(TallyLedgerDirectory.class);
    private final TallyChangeItemRepository repository = mock(TallyChangeItemRepository.class);
    private final TallyChangeRecorder recorder = new TallyChangeRecorder(directory, repository);

    private static LedgerLookup lookup(ExistenceOutcome outcome, String tallyPan, UUID jobId, String guid) {
        return new LedgerLookup(outcome, "SALES", tallyPan, guid, jobId, AT, null, false);
    }

    private static TallyChangeItem openItem(String newValue) {
        TallyChangeItem item = new TallyChangeItem();
        item.setId(UUID.randomUUID());
        item.setOrganizationId(ORG);
        item.setLedgerKey("sales");
        item.setField(TallyField.PAN);
        item.setNewValue(newValue);
        item.setStatus(TallyChangeStatus.PENDING);
        return item;
    }

    private TallyChangeItem saveCaptured() {
        ArgumentCaptor<TallyChangeItem> captor = ArgumentCaptor.forClass(TallyChangeItem.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void newPanCreatesPendingWithBaselineUploadJobAndGuidFromDirectory() {
        when(directory.lookup(ORG, null, "Sales"))
                .thenReturn(lookup(ExistenceOutcome.FOUND, "ABCDE1234F", JOB, "guid-1"));
        when(repository.findOpenForUpdate(ORG, "sales", TallyField.PAN)).thenReturn(List.of());

        recorder.onPanSaved(ORG, "Sales", " pqrst5678u ", OVERRIDE, FINDING, "acct", AT);

        TallyChangeItem item = saveCaptured();
        assertThat(item.getChangeType()).isEqualTo(TallyChangeType.ALTER_FIELD);
        assertThat(item.getField()).isEqualTo(TallyField.PAN);
        assertThat(item.getStatus()).isEqualTo(TallyChangeStatus.PENDING);
        assertThat(item.getLedgerKey()).isEqualTo("sales");
        assertThat(item.getLedgerName()).isEqualTo("Sales");
        assertThat(item.getTallyGuid()).isEqualTo("guid-1");
        assertThat(item.getBaselineValue()).isEqualTo("ABCDE1234F");
        assertThat(item.getBaselineUploadJobId()).isEqualTo(JOB);
        assertThat(item.getNewValue()).isEqualTo("PQRST5678U");
        assertThat(item.getSourceType()).isEqualTo(TallyChangeSourceType.PAN_OVERRIDE);
        assertThat(item.getSourceOverrideId()).isEqualTo(OVERRIDE);
        assertThat(item.getSourceFindingId()).isEqualTo(FINDING);
        assertThat(item.getApprovedBy()).isEqualTo("acct");
        assertThat(item.getApprovedAt()).isEqualTo(AT);
    }

    @Test
    void samePanAsTallyCreatesNothingAndSupersedesOpenItem() {
        when(directory.lookup(ORG, null, "Sales"))
                .thenReturn(lookup(ExistenceOutcome.FOUND, "ABCDE1234F", JOB, "guid-1"));
        TallyChangeItem open = openItem("OLDVALUE12");
        when(repository.findOpenForUpdate(ORG, "sales", TallyField.PAN)).thenReturn(List.of(open));

        recorder.onPanSaved(ORG, "Sales", "abcde1234f", OVERRIDE, FINDING, "acct", AT);

        verify(repository, never()).save(any());
        assertThat(open.getStatus()).isEqualTo(TallyChangeStatus.SUPERSEDED);
        assertThat(open.getSupersededBy()).isNull();
        assertThat(open.getResolvedAt()).isEqualTo(AT);
    }

    @Test
    void openItemWithSameNewValueIsKeptWithoutDuplicate() {
        when(directory.lookup(ORG, null, "Sales"))
                .thenReturn(lookup(ExistenceOutcome.FOUND, "ABCDE1234F", JOB, "guid-1"));
        TallyChangeItem open = openItem("PQRST5678U");
        when(repository.findOpenForUpdate(ORG, "sales", TallyField.PAN)).thenReturn(List.of(open));

        recorder.onPanSaved(ORG, "Sales", "PQRST5678U", OVERRIDE, FINDING, "acct", AT);

        verify(repository, never()).save(any());
        assertThat(open.getStatus()).isEqualTo(TallyChangeStatus.PENDING);
        assertThat(open.getSupersededBy()).isNull();
    }

    @Test
    void differentNewPanSupersedesOpenItemAndLinksSupersededBy() {
        UUID newId = UUID.randomUUID();
        when(directory.lookup(ORG, null, "Sales"))
                .thenReturn(lookup(ExistenceOutcome.FOUND, "ABCDE1234F", JOB, "guid-1"));
        TallyChangeItem open = openItem("OLDVALUE12");
        when(repository.findOpenForUpdate(ORG, "sales", TallyField.PAN)).thenReturn(List.of(open));
        when(repository.save(any())).thenAnswer(invocation -> {
            TallyChangeItem item = invocation.getArgument(0);
            item.setId(newId);
            return item;
        });

        recorder.onPanSaved(ORG, "Sales", "PQRST5678U", OVERRIDE, FINDING, "acct", AT);

        TallyChangeItem saved = saveCaptured();
        assertThat(saved.getNewValue()).isEqualTo("PQRST5678U");
        assertThat(open.getStatus()).isEqualTo(TallyChangeStatus.SUPERSEDED);
        assertThat(open.getSupersededBy()).isEqualTo(newId);
        assertThat(open.getResolvedAt()).isEqualTo(AT);
    }

    @Test
    void noMastersUploadCreatesItemWithNullBaselineUploadJobAndGuid() {
        when(directory.lookup(ORG, null, "Sales"))
                .thenReturn(lookup(ExistenceOutcome.NOT_FOUND, null, null, null));
        when(repository.findOpenForUpdate(ORG, "sales", TallyField.PAN)).thenReturn(List.of());

        recorder.onPanSaved(ORG, "Sales", "PQRST5678U", OVERRIDE, FINDING, "acct", AT);

        TallyChangeItem item = saveCaptured();
        assertThat(item.getBaselineValue()).isNull();
        assertThat(item.getBaselineUploadJobId()).isNull();
        assertThat(item.getTallyGuid()).isNull();
        assertThat(item.getNewValue()).isEqualTo("PQRST5678U");
        assertThat(item.getStatus()).isEqualTo(TallyChangeStatus.PENDING);
    }

    @Test
    void notFoundLedgerHasNullBaselineButKeepsItem() {
        when(directory.lookup(ORG, null, "Sales"))
                .thenReturn(lookup(ExistenceOutcome.NOT_FOUND, null, JOB, "guid-from-stale"));
        when(repository.findOpenForUpdate(ORG, "sales", TallyField.PAN)).thenReturn(List.of());

        recorder.onPanSaved(ORG, "Sales", "PQRST5678U", OVERRIDE, FINDING, "acct", AT);

        TallyChangeItem item = saveCaptured();
        assertThat(item.getBaselineValue()).isNull();
        assertThat(item.getBaselineUploadJobId()).isNull();
        assertThat(item.getTallyGuid()).isNull();
    }

    @Test
    void organizationAndLedgerKeyAreRecordedFromArguments() {
        when(directory.lookup(ORG, null, "  Anandha Timber Depot  "))
                .thenReturn(lookup(ExistenceOutcome.FOUND, null, JOB, "guid-1"));
        when(repository.findOpenForUpdate(ORG, "anandha timber depot", TallyField.PAN)).thenReturn(List.of());

        recorder.onPanSaved(ORG, "  Anandha Timber Depot  ", "PQRST5678U", OVERRIDE, FINDING, "acct", AT);

        TallyChangeItem item = saveCaptured();
        assertThat(item.getOrganizationId()).isEqualTo(ORG);
        assertThat(item.getLedgerKey()).isEqualTo("anandha timber depot");
        assertThat(item.getLedgerName()).isEqualTo("  Anandha Timber Depot  ");
    }
}
