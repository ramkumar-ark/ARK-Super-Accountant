package com.arktech.superaccountant.tallywriteback.services;

import com.arktech.superaccountant.masters.models.UploadJob;
import com.arktech.superaccountant.masters.models.UploadLedgerSnapshot;
import com.arktech.superaccountant.masters.repository.UploadJobRepository;
import com.arktech.superaccountant.masters.repository.UploadLedgerSnapshotRepository;
import com.arktech.superaccountant.tallywriteback.models.ExistenceOutcome;
import com.arktech.superaccountant.tallywriteback.payload.LedgerLookup;
import com.arktech.superaccountant.tallywriteback.payload.LookupReason;
import com.arktech.superaccountant.tallywriteback.payload.MastersUpload;
import com.arktech.superaccountant.workflow.seam.MastersJobGate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TallyLedgerDirectoryTest {

    private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Mock
    private MastersJobGate mastersJobGate;

    @Mock
    private UploadJobRepository uploadJobRepository;

    @Mock
    private UploadLedgerSnapshotRepository snapshotRepository;

    private TallyLedgerDirectory directory;

    private final UUID org = UUID.randomUUID();
    private final UUID jobId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        directory = new TallyLedgerDirectory(mastersJobGate, uploadJobRepository, snapshotRepository, CLOCK);
    }

    private void stubCompletedJob(Instant uploadedAt) {
        UploadJob job = new UploadJob();
        job.setId(jobId);
        job.setCreatedAt(uploadedAt);
        when(mastersJobGate.latestCompletedMastersJob(org)).thenReturn(Optional.of(jobId));
        when(uploadJobRepository.findById(jobId)).thenReturn(Optional.of(job));
    }

    private static UploadLedgerSnapshot snapshot(String name, String guid, String pan) {
        UploadLedgerSnapshot snapshot = new UploadLedgerSnapshot();
        snapshot.setLedgerName(name);
        snapshot.setTallyGuid(guid);
        snapshot.setPan(pan);
        return snapshot;
    }

    @Test
    void guidMatchSameNameIsFound() {
        stubCompletedJob(NOW);
        UploadLedgerSnapshot snapshot = snapshot("Sales", "guid-1", null);
        when(snapshotRepository.findByUploadJobIdAndTallyGuid(jobId, "guid-1"))
                .thenReturn(Optional.of(snapshot));

        LedgerLookup lookup = directory.lookup(org, "guid-1", "Sales");

        assertEquals(ExistenceOutcome.FOUND, lookup.outcome());
        assertEquals("Sales", lookup.currentName());
    }

    @Test
    void guidMatchDifferentNameIsRenamedWithCurrentName() {
        stubCompletedJob(NOW);
        UploadLedgerSnapshot snapshot = snapshot("Sales New", "guid-1", null);
        when(snapshotRepository.findByUploadJobIdAndTallyGuid(jobId, "guid-1"))
                .thenReturn(Optional.of(snapshot));

        LedgerLookup lookup = directory.lookup(org, "guid-1", "Sales");

        assertEquals(ExistenceOutcome.RENAMED, lookup.outcome());
        assertEquals("Sales New", lookup.currentName());
    }

    @Test
    void nullGuidSnapshotMatchesByNormalizedName() {
        stubCompletedJob(NOW);
        UploadLedgerSnapshot snapshot = snapshot("  SALES  ", null, " AB1234567C ");
        when(snapshotRepository.findByUploadJobIdAndNormalizedName(jobId, "sales"))
                .thenReturn(List.of(snapshot));

        LedgerLookup lookup = directory.lookup(org, null, "Sales");

        assertEquals(ExistenceOutcome.FOUND, lookup.outcome());
        assertEquals("  SALES  ", lookup.currentName());
        assertEquals("AB1234567C", lookup.tallyPan());
    }

    @Test
    void absentLedgerIsNotFound() {
        stubCompletedJob(NOW);
        when(snapshotRepository.findByUploadJobIdAndTallyGuid(jobId, "guid-x"))
                .thenReturn(Optional.empty());
        when(snapshotRepository.findByUploadJobIdAndNormalizedName(jobId, "mystery"))
                .thenReturn(List.of());

        LedgerLookup lookup = directory.lookup(org, "guid-x", "Mystery");

        assertEquals(ExistenceOutcome.NOT_FOUND, lookup.outcome());
        assertNull(lookup.reason());
        assertEquals(jobId, lookup.mastersJobId());
    }

    @Test
    void noMastersUploadIsNotFoundWithReason() {
        when(mastersJobGate.latestCompletedMastersJob(org)).thenReturn(Optional.empty());

        LedgerLookup lookup = directory.lookup(org, "guid-1", "Sales");

        assertEquals(ExistenceOutcome.NOT_FOUND, lookup.outcome());
        assertEquals(LookupReason.NO_MASTERS_UPLOAD, lookup.reason());
        assertNull(lookup.mastersJobId());
        assertNull(lookup.mastersUploadedAt());
    }

    @Test
    void uploadOlderThan24HoursIsStale() {
        Instant uploadedAt = NOW.minus(Duration.ofHours(24)).minusSeconds(1);
        stubCompletedJob(uploadedAt);

        Optional<MastersUpload> upload = directory.latestUpload(org);

        assertTrue(upload.orElseThrow().stale());
        assertEquals(uploadedAt, upload.orElseThrow().uploadedAt());
    }

    @Test
    void uploadAt23HoursIsNotStale() {
        Instant uploadedAt = NOW.minus(Duration.ofHours(23));
        stubCompletedJob(uploadedAt);

        Optional<MastersUpload> upload = directory.latestUpload(org);

        assertFalse(upload.orElseThrow().stale());
    }

    @Test
    void blankPanBecomesNull() {
        stubCompletedJob(NOW);
        UploadLedgerSnapshot snapshot = snapshot("Sales", "guid-1", "   ");
        when(snapshotRepository.findByUploadJobIdAndTallyGuid(jobId, "guid-1"))
                .thenReturn(Optional.of(snapshot));

        LedgerLookup lookup = directory.lookup(org, "guid-1", "Sales");

        assertNull(lookup.tallyPan());
    }
}
