package com.arktech.superaccountant.workflow.snapshot;

import com.arktech.superaccountant.daybook.models.ParsedVoucher;
import com.arktech.superaccountant.daybook.repository.ParsedVoucherRepository;
import com.arktech.superaccountant.review.FindingApplier;
import com.arktech.superaccountant.review.FindingReviewException;
import com.arktech.superaccountant.review.ReviewableFinding;
import com.arktech.superaccountant.workflow.models.WorkflowEvent;
import com.arktech.superaccountant.workflow.models.WorkflowEventKind;
import com.arktech.superaccountant.workflow.repository.WorkflowEventRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

/**
 * Accepts a tamper finding as a deliberate correction: rewrites or releases
 * the frozen voucher row, then records one amendment follow-up and one
 * AMENDMENT_RECORDED event per affected workflow.
 */
@Component
public class TamperAcceptApplier implements FindingApplier<ReviewableFinding> {

    @Autowired
    private ParsedVoucherRepository parsedVoucherRepository;
    @Autowired
    private FrozenVoucherRepository frozenVoucherRepository;
    @Autowired
    private AmendmentFollowUpRepository amendmentFollowUpRepository;
    @Autowired
    private WorkflowEventRepository workflowEventRepository;
    @Autowired
    private MasterTamperApplier masterTamperApplier;

    @Override
    public void validate(ReviewableFinding finding, String proposedValue, String note) {
        TamperFinding f = (TamperFinding) finding;
        if (isMastersKind(f.getChangeKind())) {
            masterTamperApplier.validate(finding, proposedValue, note);
            return;
        }
        if (note == null || note.isBlank()) {
            throw new FindingReviewException(400, "A note is required.");
        }
    }

    @Override
    public void apply(ReviewableFinding finding, String approver) {
        TamperFinding f = (TamperFinding) finding;
        if (isMastersKind(f.getChangeKind())) {
            masterTamperApplier.apply(finding, approver);
            return;
        }
        if (f.getChangeKind() != TamperChangeKind.RETRO_FLAGGED) {
            switch (f.getChangeKind()) {
                case MODIFIED, INSERTED -> rewriteOrInsert(f);
                case DELETED -> release(f);
                default -> {
                }
            }
        }
        recordFollowUps(f, approver);
    }

    private boolean isMastersKind(TamperChangeKind kind) {
        return kind == TamperChangeKind.MASTER_MODIFIED || kind == TamperChangeKind.MASTER_REMOVED;
    }

    private void rewriteOrInsert(TamperFinding f) {
        Map<String, ParsedVoucher> uploaded = VoucherKeys.keyAll(
                parsedVoucherRepository.findByUploadJobId(f.getDetectedInUploadJobId()));
        ParsedVoucher voucher = uploaded.get(f.getVoucherKey());
        if (voucher == null) {
            throw new FindingReviewException(409,
                    "The upload this change was found in no longer contains the voucher.");
        }
        FrozenVoucher live = frozenVoucherRepository
                .findByOrganizationIdAndVoucherKeyAndReleasedAtIsNull(f.getOrganizationId(), f.getVoucherKey())
                .orElse(null);
        if (live == null) {
            live = new FrozenVoucher();
            live.setOrganizationId(f.getOrganizationId());
            live.setVoucherKey(f.getVoucherKey());
        }
        live.setContentHash(f.getNewHash());
        live.setPayload(VoucherPayload.of(voucher).toJson());
        live.setVoucherDate(voucher.getVoucherDate());
        live.setVoucherTypeName(voucher.getVoucherTypeName());
        live.setVoucherNumber(voucher.getVoucherNumber());
        live.setFrozenByWorkflowId(firstAffectedWorkflowId(f));
        live.setFrozenAt(Instant.now());
        live.setReleasedAt(null);
        frozenVoucherRepository.save(live);
    }

    private void release(TamperFinding f) {
        frozenVoucherRepository
                .findByOrganizationIdAndVoucherKeyAndReleasedAtIsNull(f.getOrganizationId(), f.getVoucherKey())
                .ifPresent(row -> {
                    row.setReleasedAt(Instant.now());
                    frozenVoucherRepository.save(row);
                });
    }

    private void recordFollowUps(TamperFinding f, String approver) {
        if (f.getAffectedWorkflowIds() == null || f.getAffectedWorkflowIds().isBlank()) {
            return;
        }
        for (String id : f.getAffectedWorkflowIds().split(",")) {
            UUID workflowId = UUID.fromString(id.trim());
            AmendmentFollowUp followUp = new AmendmentFollowUp();
            followUp.setOrganizationId(f.getOrganizationId());
            followUp.setWorkflowId(workflowId);
            followUp.setTamperFindingId(f.getId());
            followUp.setVoucherKey(f.getVoucherKey());
            followUp.setSummary(f.getMessage());
            followUp.setStatus(AmendmentStatus.OPEN);
            followUp.setCreatedAt(Instant.now());
            amendmentFollowUpRepository.save(followUp);

            WorkflowEvent event = new WorkflowEvent();
            event.setWorkflowId(workflowId);
            event.setActor(approver);
            event.setKind(WorkflowEventKind.AMENDMENT_RECORDED);
            event.setReason(f.getMessage());
            workflowEventRepository.save(event);
        }
    }

    private UUID firstAffectedWorkflowId(TamperFinding f) {
        if (f.getAffectedWorkflowIds() == null || f.getAffectedWorkflowIds().isBlank()) {
            return null;
        }
        return Arrays.stream(f.getAffectedWorkflowIds().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .findFirst()
                .map(UUID::fromString)
                .orElse(null);
    }
}
