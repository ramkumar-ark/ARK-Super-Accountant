package com.arktech.superaccountant.review;

import com.arktech.superaccountant.masters.models.ResolveStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FindingReviewServiceTest {

    @lombok.Data
    static class FakeFinding implements ReviewableFinding {
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
    }

    static class RecordingApplier implements FindingApplier<FakeFinding> {
        final List<FakeFinding> applied = new ArrayList<>();
        RuntimeException throwOnApply;

        @Override
        public void validate(FakeFinding finding, String proposedValue, String note) {
        }

        @Override
        public void apply(FakeFinding finding, String approver) {
            if (throwOnApply != null) {
                throw throwOnApply;
            }
            applied.add(finding);
        }
    }

    static final Instant NOW = Instant.parse("2025-04-01T10:00:00Z");
    final FindingReviewService service = new FindingReviewService();
    final FakeFinding finding = new FakeFinding();
    final RecordingApplier applier = new RecordingApplier();

    static ReviewActor operator() {
        return new ReviewActor("operator", false, true);
    }

    static ReviewActor accountant() {
        return new ReviewActor("accountant", true, true);
    }

    static ReviewActor auditor() {
        return new ReviewActor("auditor", false, false);
    }

    @Test
    void acknowledge_byOperator_setsAcknowledgedAndSubmitter() {
        service.submit(finding, "V", "note", operator(), NOW, applier);

        assertThat(finding.getResolveStatus()).isEqualTo(ResolveStatus.ACKNOWLEDGED);
        assertThat(finding.getProposedValue()).isEqualTo("V");
        assertThat(finding.getResolveNote()).isEqualTo("note");
        assertThat(finding.getSubmittedBy()).isEqualTo("operator");
        assertThat(finding.getSubmittedAt()).isEqualTo(NOW);
        assertThat(finding.getReviewedBy()).isNull();
        assertThat(applier.applied).isEmpty();
    }

    @Test
    void acknowledge_byAccountant_approvesInOneStepAndCallsApply() {
        service.submit(finding, "V", "note", accountant(), NOW, applier);

        assertThat(finding.getResolveStatus()).isEqualTo(ResolveStatus.APPROVED);
        assertThat(finding.getReviewedBy()).isEqualTo("accountant");
        assertThat(finding.getReviewedAt()).isEqualTo(NOW);
        assertThat(finding.getResolvedBy()).isEqualTo("accountant");
        assertThat(finding.getResolvedAt()).isEqualTo(NOW);
        assertThat(applier.applied).containsExactly(finding);
    }

    @Test
    void acknowledge_notOpen_400() {
        finding.setResolveStatus(ResolveStatus.ACKNOWLEDGED);

        assertThatThrownBy(() -> service.submit(finding, "V", null, operator(), NOW, applier))
                .isInstanceOfSatisfying(FindingReviewException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(400);
                    assertThat(e.getMessage()).isEqualTo("Only OPEN findings can be resolved.");
                });
    }

    @Test
    void acknowledge_byAuditor_403() {
        assertThatThrownBy(() -> service.submit(finding, "V", null, auditor(), NOW, applier))
                .isInstanceOfSatisfying(FindingReviewException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(403);
                    assertThat(e.getMessage()).isEqualTo("Only OPERATOR or ACCOUNTANT can resolve findings.");
                });
    }

    @Test
    void approve_byOperator_403() {
        finding.setResolveStatus(ResolveStatus.ACKNOWLEDGED);

        assertThatThrownBy(() -> service.approve(finding, operator(), NOW, applier))
                .isInstanceOfSatisfying(FindingReviewException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(403);
                    assertThat(e.getMessage()).isEqualTo("Only ACCOUNTANT can approve findings.");
                });
    }

    @Test
    void approve_notAcknowledged_400() {
        assertThatThrownBy(() -> service.approve(finding, accountant(), NOW, applier))
                .isInstanceOfSatisfying(FindingReviewException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(400);
                    assertThat(e.getMessage()).isEqualTo("Only findings pending review can be approved.");
                });
    }

    @Test
    void reject_withoutNote_400() {
        finding.setResolveStatus(ResolveStatus.ACKNOWLEDGED);

        assertThatThrownBy(() -> service.reject(finding, accountant(), "  ", NOW))
                .isInstanceOfSatisfying(FindingReviewException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(400);
                    assertThat(e.getMessage()).isEqualTo("A note is required to reject a finding.");
                });
    }

    @Test
    void reject_returnsToOpenKeepsProposalSetsReviewFieldsClearsResolved() {
        finding.setResolveStatus(ResolveStatus.ACKNOWLEDGED);
        finding.setProposedValue("V");
        finding.setResolveNote("prop note");
        finding.setSubmittedBy("operator");
        finding.setSubmittedAt(NOW);
        finding.setResolvedBy("someone");
        finding.setResolvedAt(NOW);
        Instant later = NOW.plusSeconds(60);

        service.reject(finding, accountant(), "bad value", later);

        assertThat(finding.getResolveStatus()).isEqualTo(ResolveStatus.OPEN);
        assertThat(finding.getProposedValue()).isEqualTo("V");
        assertThat(finding.getResolveNote()).isEqualTo("prop note");
        assertThat(finding.getSubmittedBy()).isEqualTo("operator");
        assertThat(finding.getReviewedBy()).isEqualTo("accountant");
        assertThat(finding.getReviewedAt()).isEqualTo(later);
        assertThat(finding.getReviewNote()).isEqualTo("bad value");
        assertThat(finding.getResolvedBy()).isNull();
        assertThat(finding.getResolvedAt()).isNull();
    }

    @Test
    void resubmit_clearsEarlierReview() {
        finding.setResolveStatus(ResolveStatus.OPEN);
        finding.setReviewedBy("accountant");
        finding.setReviewedAt(NOW);
        finding.setReviewNote("bad");
        finding.setResolvedBy("accountant");
        finding.setResolvedAt(NOW);

        service.submit(finding, "V2", "second try", operator(), NOW.plusSeconds(120), applier);

        assertThat(finding.getResolveStatus()).isEqualTo(ResolveStatus.ACKNOWLEDGED);
        assertThat(finding.getProposedValue()).isEqualTo("V2");
        assertThat(finding.getReviewedBy()).isNull();
        assertThat(finding.getReviewedAt()).isNull();
        assertThat(finding.getReviewNote()).isNull();
        assertThat(finding.getResolvedBy()).isNull();
        assertThat(finding.getResolvedAt()).isNull();
    }

    @Test
    void approve_applierThrows_statusUnchanged() {
        finding.setResolveStatus(ResolveStatus.ACKNOWLEDGED);
        applier.throwOnApply = new RuntimeException("apply failed");

        assertThatThrownBy(() -> service.approve(finding, accountant(), NOW, applier))
                .isInstanceOf(RuntimeException.class);

        assertThat(finding.getResolveStatus()).isEqualTo(ResolveStatus.ACKNOWLEDGED);
    }

    @Test
    void act_dispatchesAndSaves() {
        FakeFinding f = new FakeFinding();
        service.act(f, "ACKNOWLEDGE", "V", null, operator(), NOW, applier);
        assertThat(f.getResolveStatus()).isEqualTo(ResolveStatus.ACKNOWLEDGED);
    }

    @Test
    void act_unknownAction_400() {
        assertThatThrownBy(() -> service.act(finding, "DELETE", null, null, operator(), NOW, applier))
                .isInstanceOfSatisfying(FindingReviewException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(400);
                    assertThat(e.getMessage()).isEqualTo("Unknown action. Must be ACKNOWLEDGE, APPROVE, or REJECT.");
                });
    }

    @Test
    void noteOnlyApplier_blankNote_400() {
        NoteOnlyApplier<FakeFinding> noteOnly = new NoteOnlyApplier<>();

        assertThatThrownBy(() -> noteOnly.validate(finding, null, " "))
                .isInstanceOfSatisfying(FindingReviewException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(400);
                    assertThat(e.getMessage()).isEqualTo("A note is required.");
                });

        noteOnly.validate(finding, null, "some note");
        noteOnly.apply(finding, "accountant");
        assertThat(applier.applied).isEmpty();
    }
}
