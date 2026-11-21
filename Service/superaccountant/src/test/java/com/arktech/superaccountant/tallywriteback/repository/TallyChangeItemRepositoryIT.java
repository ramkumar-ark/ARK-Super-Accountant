package com.arktech.superaccountant.tallywriteback.repository;

import com.arktech.superaccountant.support.TestDatabase;
import com.arktech.superaccountant.tallywriteback.models.ExistenceOutcome;
import com.arktech.superaccountant.tallywriteback.models.TallyChangeItem;
import com.arktech.superaccountant.tallywriteback.models.TallyChangeSourceType;
import com.arktech.superaccountant.tallywriteback.models.TallyChangeStatus;
import com.arktech.superaccountant.tallywriteback.models.TallyChangeType;
import com.arktech.superaccountant.tallywriteback.models.TallyExportBatch;
import com.arktech.superaccountant.tallywriteback.models.TallyExportBatchItem;
import com.arktech.superaccountant.tallywriteback.models.TallyFailureReason;
import com.arktech.superaccountant.tallywriteback.models.TallyField;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
@TestPropertySource(properties = "JWT_SECRET=test-jwt-secret-must-be-at-least-32-characters-long")
class TallyChangeItemRepositoryIT {

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        TestDatabase.register(registry);
    }

    @Autowired
    private TallyChangeItemRepository items;

    @Autowired
    private TallyExportBatchRepository batches;

    @Autowired
    private TallyExportBatchItemRepository batchItems;

    @Autowired
    private EntityManager entityManager;

    @Test
    void roundTripsEveryColumnAndEnumConverter() {
        UUID organizationId = UUID.randomUUID();
        UUID baselineUploadJobId = UUID.randomUUID();
        UUID sourceOverrideId = UUID.randomUUID();
        UUID sourceFindingId = UUID.randomUUID();
        UUID supersededBy = UUID.randomUUID();
        UUID lastCheckJobId = UUID.randomUUID();
        UUID appliedUploadJobId = UUID.randomUUID();
        Instant approvedAt = Instant.parse("2026-01-02T03:04:05Z");
        Instant lastCheckedAt = Instant.parse("2026-02-03T04:05:06Z");
        Instant resolvedAt = Instant.parse("2026-03-04T05:06:07Z");
        Instant createdAt = Instant.parse("2026-04-05T06:07:08Z");

        TallyChangeItem item = new TallyChangeItem();
        item.setOrganizationId(organizationId);
        item.setChangeType(TallyChangeType.ALTER_FIELD);
        item.setField(TallyField.PAN);
        item.setLedgerKey("acme traders");
        item.setLedgerName("Acme Traders");
        item.setTallyGuid("tally-guid-1");
        item.setBaselineValue("AAAAA1111A");
        item.setBaselineUploadJobId(baselineUploadJobId);
        item.setNewValue("BBBBB2222B");
        item.setSourceType(TallyChangeSourceType.PAN_OVERRIDE);
        item.setSourceOverrideId(sourceOverrideId);
        item.setSourceFindingId(sourceFindingId);
        item.setApprovedBy("approver@example.com");
        item.setApprovedAt(approvedAt);
        item.setStatus(TallyChangeStatus.FAILED);
        item.setSupersededBy(supersededBy);
        item.setObservedValue("CCCCC3333C");
        item.setFailureReason(TallyFailureReason.TALLY_VALUE_CHANGED);
        item.setLastCheckOutcome(ExistenceOutcome.RENAMED);
        item.setLastCheckJobId(lastCheckJobId);
        item.setLastCheckedAt(lastCheckedAt);
        item.setAppliedUploadJobId(appliedUploadJobId);
        item.setResolvedAt(resolvedAt);
        item.setCreatedAt(createdAt);

        TallyChangeItem saved = items.saveAndFlush(item);
        entityManager.clear();

        TallyChangeItem loaded = items.findById(saved.getId()).orElseThrow();
        assertThat(loaded.getOrganizationId()).isEqualTo(organizationId);
        assertThat(loaded.getChangeType()).isEqualTo(TallyChangeType.ALTER_FIELD);
        assertThat(loaded.getField()).isEqualTo(TallyField.PAN);
        assertThat(loaded.getLedgerKey()).isEqualTo("acme traders");
        assertThat(loaded.getLedgerName()).isEqualTo("Acme Traders");
        assertThat(loaded.getTallyGuid()).isEqualTo("tally-guid-1");
        assertThat(loaded.getBaselineValue()).isEqualTo("AAAAA1111A");
        assertThat(loaded.getBaselineUploadJobId()).isEqualTo(baselineUploadJobId);
        assertThat(loaded.getNewValue()).isEqualTo("BBBBB2222B");
        assertThat(loaded.getSourceType()).isEqualTo(TallyChangeSourceType.PAN_OVERRIDE);
        assertThat(loaded.getSourceOverrideId()).isEqualTo(sourceOverrideId);
        assertThat(loaded.getSourceFindingId()).isEqualTo(sourceFindingId);
        assertThat(loaded.getApprovedBy()).isEqualTo("approver@example.com");
        assertThat(loaded.getApprovedAt()).isEqualTo(approvedAt);
        assertThat(loaded.getStatus()).isEqualTo(TallyChangeStatus.FAILED);
        assertThat(loaded.getSupersededBy()).isEqualTo(supersededBy);
        assertThat(loaded.getObservedValue()).isEqualTo("CCCCC3333C");
        assertThat(loaded.getFailureReason()).isEqualTo(TallyFailureReason.TALLY_VALUE_CHANGED);
        assertThat(loaded.getLastCheckOutcome()).isEqualTo(ExistenceOutcome.RENAMED);
        assertThat(loaded.getLastCheckJobId()).isEqualTo(lastCheckJobId);
        assertThat(loaded.getLastCheckedAt()).isEqualTo(lastCheckedAt);
        assertThat(loaded.getAppliedUploadJobId()).isEqualTo(appliedUploadJobId);
        assertThat(loaded.getResolvedAt()).isEqualTo(resolvedAt);
        assertThat(loaded.getCreatedAt()).isEqualTo(createdAt);

        TallyChangeItem minimal = new TallyChangeItem();
        minimal.setOrganizationId(organizationId);
        minimal.setChangeType(TallyChangeType.CREATE_LEDGER);
        minimal.setField(TallyField.PAN);
        minimal.setLedgerKey("minimal ledger");
        minimal.setLedgerName("Minimal Ledger");
        minimal.setNewValue("DDDDD4444D");
        minimal.setSourceType(TallyChangeSourceType.PAN_OVERRIDE);
        minimal.setSourceOverrideId(UUID.randomUUID());
        minimal.setStatus(TallyChangeStatus.PENDING);

        TallyChangeItem savedMinimal = items.saveAndFlush(minimal);
        entityManager.clear();

        TallyChangeItem loadedMinimal = items.findById(savedMinimal.getId()).orElseThrow();
        assertThat(loadedMinimal.getChangeType()).isEqualTo(TallyChangeType.CREATE_LEDGER);
        assertThat(loadedMinimal.getStatus()).isEqualTo(TallyChangeStatus.PENDING);
        assertThat(loadedMinimal.getTallyGuid()).isNull();
        assertThat(loadedMinimal.getBaselineValue()).isNull();
        assertThat(loadedMinimal.getFailureReason()).isNull();
        assertThat(loadedMinimal.getLastCheckOutcome()).isNull();
        assertThat(loadedMinimal.getLastCheckedAt()).isNull();
        assertThat(loadedMinimal.getCreatedAt()).isNotNull();
    }

    @Test
    void openItemFinderIsOrganizationScopedAndExcludesClosedStatuses() {
        UUID organizationId = UUID.randomUUID();
        UUID otherOrganizationId = UUID.randomUUID();

        TallyChangeItem pending = item(organizationId, "acme traders", TallyChangeStatus.PENDING);
        TallyChangeItem exported = item(organizationId, "beta ledger", TallyChangeStatus.EXPORTED);
        TallyChangeItem failed = item(organizationId, "gamma ledger", TallyChangeStatus.FAILED);
        TallyChangeItem applied = item(organizationId, "acme traders", TallyChangeStatus.APPLIED);
        TallyChangeItem superseded = item(organizationId, "acme traders", TallyChangeStatus.SUPERSEDED);
        TallyChangeItem otherOrg = item(otherOrganizationId, "acme traders", TallyChangeStatus.PENDING);
        items.saveAllAndFlush(List.of(pending, exported, failed, applied, superseded, otherOrg));
        entityManager.clear();

        assertThat(items.findOpenForUpdate(organizationId, "acme traders", TallyField.PAN))
                .extracting(TallyChangeItem::getId)
                .containsExactly(pending.getId());
        assertThat(items.findOpenForUpdate(organizationId, "beta ledger", TallyField.PAN))
                .extracting(TallyChangeItem::getId)
                .containsExactly(exported.getId());
        assertThat(items.findOpenForUpdate(organizationId, "gamma ledger", TallyField.PAN))
                .extracting(TallyChangeItem::getId)
                .containsExactly(failed.getId());
        assertThat(items.findOpenForUpdate(otherOrganizationId, "acme traders", TallyField.PAN))
                .extracting(TallyChangeItem::getId)
                .containsExactly(otherOrg.getId());
        assertThat(items.findByIdAndOrganizationId(applied.getId(), organizationId)).isPresent();
        assertThat(items.findByIdAndOrganizationId(applied.getId(), otherOrganizationId)).isEmpty();
    }

    @Test
    void batchAndItsItemsPersistAndAreFoundByBatchId() {
        UUID organizationId = UUID.randomUUID();
        UUID mastersJobId = UUID.randomUUID();
        UUID itemId1 = UUID.randomUUID();
        UUID itemId2 = UUID.randomUUID();
        Instant generatedAt = Instant.parse("2026-05-06T07:08:09Z");
        Instant mastersUploadedAt = Instant.parse("2026-05-05T06:07:08Z");

        TallyExportBatch batch = new TallyExportBatch();
        batch.setOrganizationId(organizationId);
        batch.setGeneratedBy("generator@example.com");
        batch.setGeneratedAt(generatedAt);
        batch.setMastersJobId(mastersJobId);
        batch.setMastersUploadedAt(mastersUploadedAt);
        batch.setStaleConfirmed(true);
        batch.setItemCount(2);
        batch.setFileSha256("a".repeat(64));

        TallyExportBatch savedBatch = batches.saveAndFlush(batch);

        TallyExportBatchItem first = new TallyExportBatchItem();
        first.setBatchId(savedBatch.getId());
        first.setItemId(itemId1);
        first.setNameSent("Acme Traders");
        first.setCheckOutcome(ExistenceOutcome.FOUND);

        TallyExportBatchItem second = new TallyExportBatchItem();
        second.setBatchId(savedBatch.getId());
        second.setItemId(itemId2);
        second.setNameSent("Beta Ledger");
        second.setCheckOutcome(ExistenceOutcome.NOT_FOUND);

        batchItems.saveAllAndFlush(List.of(first, second));
        entityManager.clear();

        TallyExportBatch loadedBatch = batches.findByIdAndOrganizationId(savedBatch.getId(), organizationId).orElseThrow();
        assertThat(loadedBatch.getOrganizationId()).isEqualTo(organizationId);
        assertThat(loadedBatch.getGeneratedBy()).isEqualTo("generator@example.com");
        assertThat(loadedBatch.getGeneratedAt()).isEqualTo(generatedAt);
        assertThat(loadedBatch.getMastersJobId()).isEqualTo(mastersJobId);
        assertThat(loadedBatch.getMastersUploadedAt()).isEqualTo(mastersUploadedAt);
        assertThat(loadedBatch.getStaleConfirmed()).isTrue();
        assertThat(loadedBatch.getItemCount()).isEqualTo(2);
        assertThat(loadedBatch.getFileSha256()).isEqualTo("a".repeat(64));

        List<TallyExportBatchItem> loadedItems =
                batchItems.findByBatchIdAndOrganizationId(savedBatch.getId(), organizationId);
        assertThat(loadedItems).hasSize(2);
        assertThat(loadedItems)
                .extracting(TallyExportBatchItem::getItemId)
                .containsExactlyInAnyOrder(itemId1, itemId2);
        assertThat(loadedItems)
                .extracting(TallyExportBatchItem::getCheckOutcome)
                .containsExactlyInAnyOrder(ExistenceOutcome.FOUND, ExistenceOutcome.NOT_FOUND);
        assertThat(batchItems.findByBatchIdAndOrganizationId(savedBatch.getId(), UUID.randomUUID())).isEmpty();
    }

    private TallyChangeItem item(UUID organizationId, String ledgerKey, TallyChangeStatus status) {
        TallyChangeItem item = new TallyChangeItem();
        item.setOrganizationId(organizationId);
        item.setChangeType(TallyChangeType.ALTER_FIELD);
        item.setField(TallyField.PAN);
        item.setLedgerKey(ledgerKey);
        item.setLedgerName(ledgerKey);
        item.setNewValue("AAAAA1111A");
        item.setSourceType(TallyChangeSourceType.PAN_OVERRIDE);
        item.setSourceOverrideId(UUID.randomUUID());
        item.setStatus(status);
        return item;
    }
}
