package com.arktech.superaccountant.review;

import com.arktech.superaccountant.masters.models.ResolveStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * One review state machine for findings, shared by every module. Rules carried over from masters:
 * ACKNOWLEDGE (OPERATOR or ACCOUNTANT, requires OPEN) submits; an ACCOUNTANT's ACKNOWLEDGE also
 * approves in the same step (running the applier). APPROVE and REJECT are ACCOUNTANT-only and
 * require ACKNOWLEDGED. REJECT needs a note and returns the finding to OPEN. AUDITOR_CA and OWNER
 * are read-only.
 */
@Service
public class FindingReviewService {

    /**
     * Dispatches one review action on a finding. Validation failures throw {@link FindingReviewException}
     * with today's 403/400 messages; an applier failure propagates so the caller's transaction rolls back.
     */
    public <F extends ReviewableFinding> F act(F finding, String action, String value, String note,
                                               ReviewActor actor, Instant now, FindingApplier<F> applier) {
        switch (action) {
            case "ACKNOWLEDGE" -> submit(finding, value, note, actor, now, applier);
            case "APPROVE" -> approve(finding, actor, now, applier);
            case "REJECT" -> reject(finding, actor, note, now);
            default -> throw new FindingReviewException(400,
                    "Unknown action. Must be ACKNOWLEDGE, APPROVE, or REJECT.");
        }
        return finding;
    }

    /**
     * Records an override proposal on an OPEN finding (a resubmission clears any earlier review). An
     * accountant's own submission is approved in the same step. Must run inside the caller's transaction.
     */
    public <F extends ReviewableFinding> void submit(F finding, String value, String note, ReviewActor actor,
                                                     Instant now, FindingApplier<F> applier) {
        if (!actor.canSubmit()) {
            throw new FindingReviewException(403, "Only OPERATOR or ACCOUNTANT can resolve findings.");
        }
        if (finding.getResolveStatus() != ResolveStatus.OPEN) {
            throw new FindingReviewException(400, "Only OPEN findings can be resolved.");
        }
        applier.validate(finding, value, note);
        finding.setResolveStatus(ResolveStatus.ACKNOWLEDGED);
        finding.setResolveNote(note);
        finding.setProposedValue(value);
        finding.setSubmittedBy(actor.username());
        finding.setSubmittedAt(now);
        finding.setReviewedBy(null);
        finding.setReviewedAt(null);
        finding.setReviewNote(null);
        finding.setResolvedBy(null);
        finding.setResolvedAt(null);
        if (actor.accountant()) {
            approve(finding, actor, now, applier);
        }
    }

    /**
     * Applies the proposal through the applier and marks the finding APPROVED. Must run inside the
     * caller's transaction, so an applier failure rolls back both the module write and this status.
     */
    public <F extends ReviewableFinding> void approve(F finding, ReviewActor approver, Instant now,
                                                      FindingApplier<F> applier) {
        if (!approver.accountant()) {
            throw new FindingReviewException(403, "Only ACCOUNTANT can approve findings.");
        }
        if (finding.getResolveStatus() != ResolveStatus.ACKNOWLEDGED) {
            throw new FindingReviewException(400, "Only findings pending review can be approved.");
        }
        applier.apply(finding, approver.username());
        finding.setResolveStatus(ResolveStatus.APPROVED);
        finding.setReviewedBy(approver.username());
        finding.setReviewedAt(now);
        finding.setResolvedBy(approver.username());
        finding.setResolvedAt(now);
    }

    /**
     * Returns an ACKNOWLEDGED finding to OPEN: the proposal and the submitter's note stay visible,
     * the module is untouched. Requires a non-blank note.
     */
    public <F extends ReviewableFinding> void reject(F finding, ReviewActor reviewer, String note, Instant now) {
        if (!reviewer.accountant()) {
            throw new FindingReviewException(403, "Only ACCOUNTANT can reject findings.");
        }
        if (finding.getResolveStatus() != ResolveStatus.ACKNOWLEDGED) {
            throw new FindingReviewException(400, "Only findings pending review can be rejected.");
        }
        if (note == null || note.isBlank()) {
            throw new FindingReviewException(400, "A note is required to reject a finding.");
        }
        finding.setResolveStatus(ResolveStatus.OPEN);
        finding.setReviewedBy(reviewer.username());
        finding.setReviewedAt(now);
        finding.setReviewNote(note);
        finding.setResolvedBy(null);
        finding.setResolvedAt(null);
    }
}
