package com.arktech.superaccountant.tallywriteback.services;

import com.arktech.superaccountant.masters.models.UploadJob;
import com.arktech.superaccountant.masters.models.UploadLedgerSnapshot;
import com.arktech.superaccountant.masters.repository.UploadJobRepository;
import com.arktech.superaccountant.masters.repository.UploadLedgerSnapshotRepository;
import com.arktech.superaccountant.masters.util.LedgerNames;
import com.arktech.superaccountant.tallywriteback.models.ExistenceOutcome;
import com.arktech.superaccountant.tallywriteback.payload.LedgerLookup;
import com.arktech.superaccountant.tallywriteback.payload.LookupReason;
import com.arktech.superaccountant.tallywriteback.payload.MastersUpload;
import com.arktech.superaccountant.workflow.seam.MastersJobGate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class TallyLedgerDirectory {

    private static final Duration STALE_AFTER = Duration.ofHours(24);

    private final MastersJobGate mastersJobGate;
    private final UploadJobRepository uploadJobRepository;
    private final UploadLedgerSnapshotRepository snapshotRepository;
    private final Clock clock;

    @Autowired
    public TallyLedgerDirectory(MastersJobGate mastersJobGate,
                                UploadJobRepository uploadJobRepository,
                                UploadLedgerSnapshotRepository snapshotRepository) {
        this(mastersJobGate, uploadJobRepository, snapshotRepository, Clock.systemUTC());
    }

    TallyLedgerDirectory(MastersJobGate mastersJobGate,
                         UploadJobRepository uploadJobRepository,
                         UploadLedgerSnapshotRepository snapshotRepository,
                         Clock clock) {
        this.mastersJobGate = mastersJobGate;
        this.uploadJobRepository = uploadJobRepository;
        this.snapshotRepository = snapshotRepository;
        this.clock = clock;
    }

    public LedgerLookup lookup(UUID organizationId, String tallyGuid, String ledgerName) {
        Optional<UUID> latestJob = mastersJobGate.latestCompletedMastersJob(organizationId);
        if (latestJob.isEmpty()) {
            return new LedgerLookup(ExistenceOutcome.NOT_FOUND, null, null, null, null,
                    LookupReason.NO_MASTERS_UPLOAD, false);
        }

        UUID mastersJobId = latestJob.get();
        Instant uploadedAt = uploadJobRepository.findById(mastersJobId)
                .map(UploadJob::getCreatedAt)
                .orElse(null);
        boolean stale = isStale(uploadedAt);
        String normalizedName = LedgerNames.normalize(ledgerName);

        if (tallyGuid != null && !tallyGuid.isBlank()) {
            Optional<UploadLedgerSnapshot> byGuid =
                    snapshotRepository.findByUploadJobIdAndTallyGuid(mastersJobId, tallyGuid);
            if (byGuid.isPresent()) {
                UploadLedgerSnapshot snapshot = byGuid.get();
                ExistenceOutcome outcome = normalizedName.equals(LedgerNames.normalize(snapshot.getLedgerName()))
                        ? ExistenceOutcome.FOUND
                        : ExistenceOutcome.RENAMED;
                return result(outcome, snapshot, mastersJobId, uploadedAt, stale);
            }
        }

        Optional<UploadLedgerSnapshot> byName =
                snapshotRepository.findByUploadJobIdAndNormalizedName(mastersJobId, normalizedName)
                        .stream()
                        .findFirst();
        if (byName.isPresent()) {
            return result(ExistenceOutcome.FOUND, byName.get(), mastersJobId, uploadedAt, stale);
        }

        return new LedgerLookup(ExistenceOutcome.NOT_FOUND, null, null, mastersJobId, uploadedAt, null, stale);
    }

    public Optional<MastersUpload> latestUpload(UUID organizationId) {
        return mastersJobGate.latestCompletedMastersJob(organizationId)
                .flatMap(jobId -> uploadJobRepository.findById(jobId)
                        .map(job -> new MastersUpload(jobId, job.getCreatedAt(), isStale(job.getCreatedAt()))));
    }

    private LedgerLookup result(ExistenceOutcome outcome, UploadLedgerSnapshot snapshot,
                                UUID mastersJobId, Instant uploadedAt, boolean stale) {
        return new LedgerLookup(outcome, snapshot.getLedgerName(), pan(snapshot), mastersJobId,
                uploadedAt, null, stale);
    }

    private static String pan(UploadLedgerSnapshot snapshot) {
        String pan = snapshot.getPan();
        if (pan == null || pan.isBlank()) {
            return null;
        }
        return pan.trim();
    }

    private boolean isStale(Instant uploadedAt) {
        return uploadedAt != null && uploadedAt.isBefore(clock.instant().minus(STALE_AFTER));
    }
}
