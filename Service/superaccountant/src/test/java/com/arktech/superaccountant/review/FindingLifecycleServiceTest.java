package com.arktech.superaccountant.review;

import com.arktech.superaccountant.masters.models.ResolveStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FindingLifecycleServiceTest {

    @lombok.Data
    static class FakeFinding implements LifecycleFinding {
        private UUID id = UUID.randomUUID();
        private ResolveStatus resolveStatus = ResolveStatus.OPEN;
        private String resolveNote;
        private String proposedValue;
        private String submittedBy;
        private Instant submittedAt;
        private String reviewedBy;
        private Instant reviewedAt;
        private String reviewNote;
        private String resolvedBy;
        private Instant resolvedAt;
        private String findingKey;
        private String evidenceHash;
        private UUID carriedFromFindingId;
    }

    static final Instant NOW = Instant.parse("2025-04-01T10:00:00Z");
    final FindingLifecycleService service = new FindingLifecycleService();

    static FakeFinding finding(String key, String hash, ResolveStatus status) {
        FakeFinding f = new FakeFinding();
        f.setFindingKey(key);
        f.setEvidenceHash(hash);
        f.setResolveStatus(status);
        return f;
    }

    static final String KEY_A = "GST_DAYBOOK|v1";
    static final String KEY_B = "GST_DAYBOOK|v2";
    static final String HASH_1 = "hash-1";
    static final String HASH_2 = "hash-2";

    @Test
    void reconcile_sameHash_carriesReviewStateAndDiscardsOld() {
        FakeFinding prev = finding(KEY_A, HASH_1, ResolveStatus.ACKNOWLEDGED);
        prev.setProposedValue("V");
        prev.setResolveNote("note");
        prev.setSubmittedBy("op");
        prev.setSubmittedAt(NOW);
        FakeFinding fresh = finding(KEY_A, HASH_1, ResolveStatus.OPEN);

        LifecycleOutcome outcome = service.reconcile(List.of(prev), List.of(fresh), NOW);

        assertThat(fresh.getResolveStatus()).isEqualTo(ResolveStatus.ACKNOWLEDGED);
        assertThat(fresh.getProposedValue()).isEqualTo("V");
        assertThat(fresh.getResolveNote()).isEqualTo("note");
        assertThat(fresh.getSubmittedBy()).isEqualTo("op");
        assertThat(fresh.getSubmittedAt()).isEqualTo(NOW);
        assertThat(fresh.getCarriedFromFindingId()).isEqualTo(prev.getId());
        assertThat(prev.getResolveStatus()).isEqualTo(ResolveStatus.DISCARDED);
        assertThat(new LifecycleOutcome(1, 0, 1, 0, 0)).usingRecursiveComparison().isEqualTo(outcome);
    }

    @Test
    void reconcile_sameHashApprovedOld_keepsOldApproved() {
        FakeFinding prev = finding(KEY_A, HASH_1, ResolveStatus.APPROVED);
        FakeFinding fresh = finding(KEY_A, HASH_1, ResolveStatus.OPEN);

        service.reconcile(List.of(prev), List.of(fresh), NOW);

        assertThat(prev.getResolveStatus()).isEqualTo(ResolveStatus.APPROVED);
        assertThat(fresh.getResolveStatus()).isEqualTo(ResolveStatus.APPROVED);
        assertThat(fresh.getCarriedFromFindingId()).isEqualTo(prev.getId());
    }

    @Test
    void reconcile_changedHash_newOpenLinkedToOld() {
        FakeFinding prev = finding(KEY_A, HASH_1, ResolveStatus.ACKNOWLEDGED);
        prev.setProposedValue("V");
        FakeFinding fresh = finding(KEY_A, HASH_2, ResolveStatus.OPEN);

        LifecycleOutcome outcome = service.reconcile(List.of(prev), List.of(fresh), NOW);

        assertThat(fresh.getResolveStatus()).isEqualTo(ResolveStatus.OPEN);
        assertThat(fresh.getProposedValue()).isNull();
        assertThat(fresh.getCarriedFromFindingId()).isEqualTo(prev.getId());
        assertThat(prev.getResolveStatus()).isEqualTo(ResolveStatus.DISCARDED);
        assertThat(new LifecycleOutcome(0, 1, 1, 0, 0)).usingRecursiveComparison().isEqualTo(outcome);
    }

    @Test
    void reconcile_unmatchedOld_autoResolvedBySystem() {
        FakeFinding prev = finding(KEY_A, HASH_1, ResolveStatus.OPEN);
        FakeFinding fresh = finding(KEY_B, HASH_1, ResolveStatus.OPEN);

        LifecycleOutcome outcome = service.reconcile(List.of(prev), List.of(fresh), NOW);

        assertThat(prev.getResolveStatus()).isEqualTo(ResolveStatus.RESOLVED);
        assertThat(prev.getResolvedBy()).isEqualTo("system");
        assertThat(prev.getResolvedAt()).isEqualTo(NOW);
        assertThat(prev.getResolveNote()).isEqualTo("No longer present after re-upload.");
        assertThat(new LifecycleOutcome(0, 0, 1, 1, 0)).usingRecursiveComparison().isEqualTo(outcome);
    }

    @Test
    void reconcile_unmatchedApprovedOld_untouched() {
        FakeFinding prev = finding(KEY_A, HASH_1, ResolveStatus.APPROVED);
        FakeFinding fresh = finding(KEY_B, HASH_1, ResolveStatus.OPEN);

        LifecycleOutcome outcome = service.reconcile(List.of(prev), List.of(fresh), NOW);

        assertThat(prev.getResolveStatus()).isEqualTo(ResolveStatus.APPROVED);
        assertThat(prev.getResolvedBy()).isNull();
        assertThat(new LifecycleOutcome(0, 0, 1, 0, 0)).usingRecursiveComparison().isEqualTo(outcome);
    }

    @Test
    void reconcile_duplicateKeysInFreshRun_startOpen() {
        FakeFinding prev = finding(KEY_A, HASH_1, ResolveStatus.ACKNOWLEDGED);
        FakeFinding dup1 = finding(KEY_A, HASH_1, ResolveStatus.OPEN);
        FakeFinding dup2 = finding(KEY_A, HASH_1, ResolveStatus.OPEN);

        LifecycleOutcome outcome = service.reconcile(List.of(prev), List.of(dup1, dup2), NOW);

        assertThat(dup1.getResolveStatus()).isEqualTo(ResolveStatus.OPEN);
        assertThat(dup2.getResolveStatus()).isEqualTo(ResolveStatus.OPEN);
        assertThat(dup1.getCarriedFromFindingId()).isNull();
        assertThat(dup2.getCarriedFromFindingId()).isNull();
        assertThat(prev.getResolveStatus()).isEqualTo(ResolveStatus.ACKNOWLEDGED); // untouched: no match
        assertThat(outcome).usingRecursiveComparison().isEqualTo(new LifecycleOutcome(0, 0, 0, 0, 0));
    }
}
