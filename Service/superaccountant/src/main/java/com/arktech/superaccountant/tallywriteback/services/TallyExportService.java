package com.arktech.superaccountant.tallywriteback.services;

import com.arktech.superaccountant.tallywriteback.models.ExistenceOutcome;
import com.arktech.superaccountant.tallywriteback.models.TallyChangeItem;
import com.arktech.superaccountant.tallywriteback.models.TallyChangeStatus;
import com.arktech.superaccountant.tallywriteback.models.TallyExportBatch;
import com.arktech.superaccountant.tallywriteback.models.TallyExportBatchItem;
import com.arktech.superaccountant.tallywriteback.payload.ExportLine;
import com.arktech.superaccountant.tallywriteback.payload.LedgerLookup;
import com.arktech.superaccountant.tallywriteback.payload.MastersUpload;
import com.arktech.superaccountant.tallywriteback.payload.TallyChangeItemResponse;
import com.arktech.superaccountant.tallywriteback.payload.TallyChangeListResponse;
import com.arktech.superaccountant.tallywriteback.repository.TallyChangeItemRepository;
import com.arktech.superaccountant.tallywriteback.repository.TallyExportBatchItemRepository;
import com.arktech.superaccountant.tallywriteback.repository.TallyExportBatchRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class TallyExportService {

    private static final List<TallyChangeStatus> EXPORTABLE =
            List.of(TallyChangeStatus.PENDING, TallyChangeStatus.EXPORTED);
    private static final Duration STALE_AFTER = Duration.ofHours(24);

    private final TallyChangeItemRepository itemRepository;
    private final TallyExportBatchRepository batchRepository;
    private final TallyExportBatchItemRepository batchItemRepository;
    private final TallyLedgerDirectory ledgerDirectory;
    private final TallyImportRenderer renderer;
    private final Clock clock;

    @Autowired
    public TallyExportService(TallyChangeItemRepository itemRepository,
                              TallyExportBatchRepository batchRepository,
                              TallyExportBatchItemRepository batchItemRepository,
                              TallyLedgerDirectory ledgerDirectory,
                              TallyImportRenderer renderer) {
        this(itemRepository, batchRepository, batchItemRepository, ledgerDirectory, renderer,
                Clock.systemUTC());
    }

    TallyExportService(TallyChangeItemRepository itemRepository,
                       TallyExportBatchRepository batchRepository,
                       TallyExportBatchItemRepository batchItemRepository,
                       TallyLedgerDirectory ledgerDirectory,
                       TallyImportRenderer renderer,
                       Clock clock) {
        this.itemRepository = itemRepository;
        this.batchRepository = batchRepository;
        this.batchItemRepository = batchItemRepository;
        this.ledgerDirectory = ledgerDirectory;
        this.renderer = renderer;
        this.clock = clock;
    }

    public TallyChangeListResponse list(UUID organizationId, TallyChangeStatus status) {
        List<TallyChangeItem> items = status == null
                ? itemRepository.findByOrganizationIdOrderByCreatedAtDesc(organizationId)
                : itemRepository.findByOrganizationIdAndStatusOrderByCreatedAtDesc(organizationId, status);
        MastersUpload upload = ledgerDirectory.latestUpload(organizationId).orElse(null);
        return new TallyChangeListResponse(upload, items.stream().map(this::toResponse).toList());
    }

    @Transactional
    public ExportResult export(UUID organizationId, String generatedBy,
                               List<UUID> itemIds, Boolean confirmStale) {
        Optional<MastersUpload> latest = ledgerDirectory.latestUpload(organizationId);
        if (latest.isEmpty()) {
            return ExportResult.error("NO_MASTERS_UPLOAD");
        }
        MastersUpload upload = latest.get();
        Instant now = clock.instant();
        boolean stale = upload.uploadedAt() != null
                && upload.uploadedAt().isBefore(now.minus(STALE_AFTER));
        boolean confirmed = Boolean.TRUE.equals(confirmStale);
        if (stale && !confirmed) {
            return ExportResult.error("STALE_CONFIRMATION_REQUIRED");
        }

        List<ExportLine> lines = new ArrayList<>();
        List<TallyChangeItem> included = new ArrayList<>();
        int skipped = 0;
        for (TallyChangeItem item : select(organizationId, itemIds)) {
            LedgerLookup lookup = ledgerDirectory.lookup(
                    organizationId, item.getTallyGuid(), item.getLedgerName());
            item.setLastCheckOutcome(lookup.outcome());
            item.setLastCheckJobId(lookup.mastersJobId());
            item.setLastCheckedAt(now);
            itemRepository.save(item);

            if (lookup.outcome() == ExistenceOutcome.FOUND || lookup.outcome() == ExistenceOutcome.RENAMED) {
                String name = nameToSend(lookup, item);
                lines.add(new ExportLine(item.getChangeType(), item.getField(), name, item.getNewValue()));
                included.add(item);
            } else {
                skipped++;
            }
        }

        if (included.isEmpty()) {
            return ExportResult.error("NOTHING_ELIGIBLE");
        }

        byte[] body = renderer.render(lines).getBytes(StandardCharsets.UTF_8);

        TallyExportBatch batch = new TallyExportBatch();
        batch.setOrganizationId(organizationId);
        batch.setGeneratedBy(generatedBy);
        batch.setGeneratedAt(now);
        batch.setMastersJobId(upload.jobId());
        batch.setMastersUploadedAt(upload.uploadedAt());
        batch.setStaleConfirmed(confirmed && stale);
        batch.setItemCount(included.size());
        batch.setFileSha256(sha256(body));
        TallyExportBatch savedBatch = batchRepository.save(batch);

        for (int i = 0; i < included.size(); i++) {
            TallyChangeItem item = included.get(i);
            TallyExportBatchItem batchItem = new TallyExportBatchItem();
            batchItem.setBatchId(savedBatch.getId());
            batchItem.setItemId(item.getId());
            batchItem.setNameSent(lines.get(i).ledgerName());
            batchItem.setCheckOutcome(item.getLastCheckOutcome());
            batchItemRepository.save(batchItem);

            item.setStatus(TallyChangeStatus.EXPORTED);
            itemRepository.save(item);
        }

        String filename = "tally-pan-changes-"
                + LocalDate.ofInstant(now, ZoneOffset.UTC) + ".json";
        return ExportResult.success(body, filename, skipped);
    }

    private List<TallyChangeItem> select(UUID organizationId, List<UUID> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) {
            return itemRepository.findForExport(organizationId, EXPORTABLE);
        }
        LinkedHashSet<UUID> unique = new LinkedHashSet<>(itemIds);
        unique.remove(null);
        if (unique.isEmpty()) {
            return List.of();
        }
        return itemRepository.findForExportByIds(organizationId, unique, EXPORTABLE);
    }

    private String nameToSend(LedgerLookup lookup, TallyChangeItem item) {
        if (lookup.outcome() == ExistenceOutcome.RENAMED) {
            return lookup.currentName();
        }
        return lookup.currentName() != null ? lookup.currentName() : item.getLedgerName();
    }

    private TallyChangeItemResponse toResponse(TallyChangeItem item) {
        LedgerLookup lookup = ledgerDirectory.lookup(
                item.getOrganizationId(), item.getTallyGuid(), item.getLedgerName());
        return new TallyChangeItemResponse(
                item.getId(),
                item.getLedgerKey(),
                item.getLedgerName(),
                item.getChangeType(),
                item.getField(),
                item.getBaselineValue(),
                item.getNewValue(),
                item.getStatus(),
                item.getSourceType(),
                item.getApprovedBy(),
                item.getApprovedAt(),
                item.getObservedValue(),
                item.getFailureReason(),
                item.getLastCheckOutcome(),
                item.getLastCheckedAt(),
                item.getCreatedAt(),
                item.getResolvedAt(),
                lookup.outcome(),
                lookup.reason(),
                lookup.currentName(),
                lookup.tallyPan());
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    public record ExportResult(String errorCode, byte[] content, String filename, int skipped) {

        static ExportResult error(String code) {
            return new ExportResult(code, null, null, 0);
        }

        static ExportResult success(byte[] content, String filename, int skipped) {
            return new ExportResult(null, content, filename, skipped);
        }

        public boolean isError() {
            return errorCode != null;
        }
    }
}
