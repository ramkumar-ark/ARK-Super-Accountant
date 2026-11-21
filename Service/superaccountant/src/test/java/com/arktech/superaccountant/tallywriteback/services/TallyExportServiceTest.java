package com.arktech.superaccountant.tallywriteback.services;

import com.arktech.superaccountant.tallywriteback.models.ExistenceOutcome;
import com.arktech.superaccountant.tallywriteback.models.TallyChangeItem;
import com.arktech.superaccountant.tallywriteback.models.TallyChangeStatus;
import com.arktech.superaccountant.tallywriteback.models.TallyChangeType;
import com.arktech.superaccountant.tallywriteback.models.TallyExportBatch;
import com.arktech.superaccountant.tallywriteback.models.TallyField;
import com.arktech.superaccountant.tallywriteback.payload.LedgerLookup;
import com.arktech.superaccountant.tallywriteback.payload.MastersUpload;
import com.arktech.superaccountant.tallywriteback.repository.TallyChangeItemRepository;
import com.arktech.superaccountant.tallywriteback.repository.TallyExportBatchItemRepository;
import com.arktech.superaccountant.tallywriteback.repository.TallyExportBatchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TallyExportServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final UUID ORG = UUID.randomUUID();

    @Mock
    private TallyChangeItemRepository itemRepository;

    @Mock
    private TallyExportBatchRepository batchRepository;

    @Mock
    private TallyExportBatchItemRepository batchItemRepository;

    @Mock
    private TallyLedgerDirectory ledgerDirectory;

    @Mock
    private TallyImportRenderer renderer;

    private TallyExportService service;

    @BeforeEach
    void setUp() {
        service = new TallyExportService(itemRepository, batchRepository, batchItemRepository,
                ledgerDirectory, renderer, CLOCK);
    }

    @Test
    void staleGateUsesInjectedClock_24h1sIsStale() {
        when(ledgerDirectory.latestUpload(ORG)).thenReturn(Optional.of(new MastersUpload(
                UUID.randomUUID(), NOW.minus(Duration.ofHours(24)).minusSeconds(1), false)));

        TallyExportService.ExportResult result = service.export(ORG, "acct", null, false);

        assertTrue(result.isError());
        assertEquals("STALE_CONFIRMATION_REQUIRED", result.errorCode());
        verifyNoInteractions(itemRepository, batchRepository, batchItemRepository, renderer);
    }

    @Test
    void staleGateUsesInjectedClock_23hIsFreshDespiteDirectoryStaleFlag() {
        UUID jobId = UUID.randomUUID();
        when(ledgerDirectory.latestUpload(ORG)).thenReturn(Optional.of(new MastersUpload(
                jobId, NOW.minus(Duration.ofHours(23)), true)));

        TallyChangeItem item = new TallyChangeItem();
        item.setId(UUID.randomUUID());
        item.setOrganizationId(ORG);
        item.setChangeType(TallyChangeType.ALTER_FIELD);
        item.setField(TallyField.PAN);
        item.setLedgerName("ACME");
        item.setTallyGuid("g-1");
        item.setNewValue("AAAAA1111A");
        item.setStatus(TallyChangeStatus.PENDING);

        when(itemRepository.findForExport(ORG,
                List.of(TallyChangeStatus.PENDING, TallyChangeStatus.EXPORTED)))
                .thenReturn(List.of(item));
        when(ledgerDirectory.lookup(ORG, "g-1", "ACME")).thenReturn(new LedgerLookup(
                ExistenceOutcome.FOUND, "ACME", "AAAAA1111A", "g-1", jobId,
                NOW.minus(Duration.ofHours(23)), null, true));
        when(renderer.render(anyList())).thenReturn("{}\n");
        when(batchRepository.save(any(TallyExportBatch.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        TallyExportService.ExportResult result = service.export(ORG, "acct", null, false);

        assertFalse(result.isError());
        ArgumentCaptor<TallyExportBatch> batchCaptor = ArgumentCaptor.forClass(TallyExportBatch.class);
        verify(batchRepository).save(batchCaptor.capture());
        assertFalse(batchCaptor.getValue().getStaleConfirmed());
        assertEquals(1, batchCaptor.getValue().getItemCount());
        assertEquals(TallyChangeStatus.EXPORTED, item.getStatus());
    }
}
