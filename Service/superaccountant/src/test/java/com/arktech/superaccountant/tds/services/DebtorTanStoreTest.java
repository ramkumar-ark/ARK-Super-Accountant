package com.arktech.superaccountant.tds.services;

import com.arktech.superaccountant.tds.models.DebtorTan;
import com.arktech.superaccountant.tds.repository.DebtorTanRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class DebtorTanStoreTest {

    private static final UUID ORG = UUID.randomUUID();
    private final DebtorTanRepository repository = mock(DebtorTanRepository.class);
    private final DebtorTanStore store = new DebtorTanStore(repository);

    @Test
    void findTanLooksUpNormalizedLedgerKey() {
        DebtorTan existing = new DebtorTan();
        existing.setTan("BLRA12345C");
        when(repository.findByOrganizationIdAndLedgerKey(ORG, "acme corp"))
                .thenReturn(Optional.of(existing));

        assertThat(store.findTan(ORG, "ACME CORP")).contains("BLRA12345C");
    }

    @Test
    void saveTanCreatesNewRecordWithNormalizedValuesAndAudit() {
        UUID findingId = UUID.randomUUID();
        Instant at = Instant.parse("2026-11-22T10:00:00Z");
        when(repository.findByOrganizationIdAndLedgerKey(ORG, "acme corp"))
                .thenReturn(Optional.empty());

        store.saveTan(ORG, "Acme Corp", " blra12345c ", "guid-1", findingId, "accountant1", at);

        ArgumentCaptor<DebtorTan> captor = ArgumentCaptor.forClass(DebtorTan.class);
        verify(repository).save(captor.capture());
        DebtorTan saved = captor.getValue();
        assertThat(saved.getOrganizationId()).isEqualTo(ORG);
        assertThat(saved.getLedgerName()).isEqualTo("Acme Corp");
        assertThat(saved.getLedgerKey()).isEqualTo("acme corp");
        assertThat(saved.getTan()).isEqualTo("BLRA12345C");
        assertThat(saved.getTallyGuid()).isEqualTo("guid-1");
        assertThat(saved.getSourceFindingId()).isEqualTo(findingId);
        assertThat(saved.getUpdatedBy()).isEqualTo("accountant1");
        assertThat(saved.getUpdatedAt()).isEqualTo(at);
    }

    @Test
    void saveTanUpdatesExistingRecordInsteadOfInsertingSecond() {
        DebtorTan existing = new DebtorTan();
        when(repository.findByOrganizationIdAndLedgerKey(ORG, "acme corp"))
                .thenReturn(Optional.of(existing));

        store.saveTan(ORG, "Acme Corp", "BLRA12345C", "guid-1", null, "accountant1", Instant.now());

        verify(repository).save(existing);
    }

    @Test
    void saveTanPreservesExistingGuidWhenIncomingGuidIsNull() {
        DebtorTan existing = new DebtorTan();
        existing.setTallyGuid("guid-1");
        when(repository.findByOrganizationIdAndLedgerKey(ORG, "acme corp"))
                .thenReturn(Optional.of(existing));

        store.saveTan(ORG, "Acme Corp", "BLRA12345C", null, null, "accountant1", Instant.now());

        assertThat(existing.getTallyGuid()).isEqualTo("guid-1");
    }

    @Test
    void saveTanRejectsInvalidTan() {
        assertThatThrownBy(() ->
                store.saveTan(ORG, "Acme Corp", "not-a-tan", "guid-1", null, "accountant1", Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Enter a valid 10-character TAN (format AAAA99999A, e.g. BLRA12345C).");
        verifyNoInteractions(repository);
    }

    @Test
    void deleteTanRemovesRecordByNormalizedKey() {
        DebtorTan existing = new DebtorTan();
        when(repository.findByOrganizationIdAndLedgerKey(ORG, "acme corp"))
                .thenReturn(Optional.of(existing));

        store.deleteTan(ORG, "ACME CORP");

        verify(repository).delete(existing);
    }
}
