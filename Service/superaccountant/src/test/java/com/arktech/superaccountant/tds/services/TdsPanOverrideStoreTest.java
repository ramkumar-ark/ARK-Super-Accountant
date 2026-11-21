package com.arktech.superaccountant.tds.services;

import com.arktech.superaccountant.masters.models.Organization;
import com.arktech.superaccountant.masters.pan.PanChangeRecorder;
import com.arktech.superaccountant.masters.repository.OrganizationRepository;
import com.arktech.superaccountant.tds.models.TdsPanOverride;
import com.arktech.superaccountant.tds.repository.TdsPanOverrideRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class TdsPanOverrideStoreTest {

    private static final UUID ORG = UUID.randomUUID();
    private final TdsPanOverrideRepository repository = mock(TdsPanOverrideRepository.class);
    private final OrganizationRepository organizationRepository = mock(OrganizationRepository.class);
    private final PanChangeRecorder recorder = mock(PanChangeRecorder.class);
    private final TdsPanOverrideStore store =
            new TdsPanOverrideStore(repository, organizationRepository, recorder);

    @BeforeEach
    void lockOrganization() {
        Organization org = new Organization();
        org.setId(ORG);
        when(organizationRepository.findByIdForUpdate(ORG)).thenReturn(Optional.of(org));
    }

    @Test
    void findPanLooksUpNormalizedPartyKey() {
        TdsPanOverride existing = new TdsPanOverride();
        existing.setPan("AAPPK2262D");
        when(repository.findByOrganizationIdAndPartyKey(ORG, "anandha timber depot"))
                .thenReturn(Optional.of(existing));

        assertThat(store.findPan(ORG, "ANANDHA TIMBER DEPOT")).contains("AAPPK2262D");
    }

    @Test
    void savePanCreatesNewOverrideWithNormalizedPanAndAudit() {
        UUID findingId = UUID.randomUUID();
        Instant at = Instant.parse("2026-11-21T10:00:00Z");
        when(repository.findByOrganizationIdAndPartyKey(ORG, "anandha timber depot"))
                .thenReturn(Optional.empty());

        store.savePan(ORG, "ANANDHA TIMBER DEPOT", " aappk2262d ", findingId, "acct", at);

        ArgumentCaptor<TdsPanOverride> captor = ArgumentCaptor.forClass(TdsPanOverride.class);
        verify(repository).save(captor.capture());
        TdsPanOverride saved = captor.getValue();
        assertThat(saved.getOrganizationId()).isEqualTo(ORG);
        assertThat(saved.getPartyLedgerName()).isEqualTo("ANANDHA TIMBER DEPOT");
        assertThat(saved.getPartyKey()).isEqualTo("anandha timber depot");
        assertThat(saved.getPan()).isEqualTo("AAPPK2262D");
        assertThat(saved.getSourceFindingId()).isEqualTo(findingId);
        assertThat(saved.getApprovedBy()).isEqualTo("acct");
        assertThat(saved.getApprovedAt()).isEqualTo(at);
    }

    @Test
    void savePanNotifiesChangeRecorderWithSavedOverrideId() {
        UUID findingId = UUID.randomUUID();
        UUID overrideId = UUID.randomUUID();
        Instant at = Instant.parse("2026-11-21T10:00:00Z");
        when(repository.findByOrganizationIdAndPartyKey(ORG, "anandha timber depot"))
                .thenReturn(Optional.empty());
        when(repository.save(any(TdsPanOverride.class))).thenAnswer(invocation -> {
            TdsPanOverride override = invocation.getArgument(0);
            override.setId(overrideId);
            return override;
        });

        store.savePan(ORG, "ANANDHA TIMBER DEPOT", " aappk2262d ", findingId, "acct", at);

        verify(recorder).onPanSaved(ORG, "ANANDHA TIMBER DEPOT", "AAPPK2262D",
                overrideId, findingId, "acct", at);
    }

    @Test
    void savePanUpdatesExistingOverrideInsteadOfInsertingSecond() {
        TdsPanOverride existing = new TdsPanOverride();
        when(repository.findByOrganizationIdAndPartyKey(ORG, "anandha timber depot"))
                .thenReturn(Optional.of(existing));

        store.savePan(ORG, "ANANDHA TIMBER DEPOT", "AAPPK2262D", UUID.randomUUID(), "acct", Instant.now());

        verify(repository).save(existing);
    }

    @Test
    void savePanWithEmptyPanStoresNoPanAndNotifiesRecorderWithEmptyString() {
        UUID findingId = UUID.randomUUID();
        UUID overrideId = UUID.randomUUID();
        Instant at = Instant.parse("2026-11-21T10:00:00Z");
        when(repository.findByOrganizationIdAndPartyKey(ORG, "anandha timber depot"))
                .thenReturn(Optional.empty());
        when(repository.save(any(TdsPanOverride.class))).thenAnswer(invocation -> {
            TdsPanOverride override = invocation.getArgument(0);
            override.setId(overrideId);
            return override;
        });

        store.savePan(ORG, "ANANDHA TIMBER DEPOT", "", findingId, "acct", at);

        ArgumentCaptor<TdsPanOverride> captor = ArgumentCaptor.forClass(TdsPanOverride.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getPan()).isEmpty();
        verify(recorder).onPanSaved(ORG, "ANANDHA TIMBER DEPOT", "", overrideId, findingId, "acct", at);
    }

    @Test
    void savePanLocksOrganizationBeforeLookupAndSave() {
        when(repository.findByOrganizationIdAndPartyKey(ORG, "anandha timber depot"))
                .thenReturn(Optional.empty());

        store.savePan(ORG, "ANANDHA TIMBER DEPOT", "AAPPK2262D", UUID.randomUUID(), "acct", Instant.now());

        InOrder inOrder = inOrder(organizationRepository, repository);
        inOrder.verify(organizationRepository).findByIdForUpdate(ORG);
        inOrder.verify(repository).findByOrganizationIdAndPartyKey(ORG, "anandha timber depot");
        inOrder.verify(repository).save(any(TdsPanOverride.class));
    }

    @Test
    void savePanThrowsWhenOrganizationIsMissing() {
        when(organizationRepository.findByIdForUpdate(ORG)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> store.savePan(ORG, "ANANDHA TIMBER DEPOT", "AAPPK2262D",
                UUID.randomUUID(), "acct", Instant.now()))
                .isInstanceOf(IllegalStateException.class);

        verifyNoInteractions(repository, recorder);
    }

    @Test
    void findPanReturnsEmptyStringForNoPanOverride() {
        TdsPanOverride existing = new TdsPanOverride();
        existing.setPan("");
        when(repository.findByOrganizationIdAndPartyKey(ORG, "anandha timber depot"))
                .thenReturn(Optional.of(existing));

        assertThat(store.findPan(ORG, "ANANDHA TIMBER DEPOT")).contains("");
    }
}
