package com.arktech.superaccountant.workflow.snapshot;

import com.arktech.superaccountant.review.FindingApplier;
import com.arktech.superaccountant.review.FindingReviewException;
import com.arktech.superaccountant.review.ReviewableFinding;
import com.arktech.superaccountant.workflow.models.WorkflowEvent;
import com.arktech.superaccountant.workflow.models.WorkflowEventKind;
import com.arktech.superaccountant.workflow.models.WorkflowType;
import com.arktech.superaccountant.workflow.repository.WorkflowEventRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Accepts a masters tamper finding. A blank proposal is a retrospective correction: it
 * rewrites or releases the frozen master-attribute rows and records one amendment
 * follow-up per affected workflow. A {@code VALID_FROM:yyyy-MM-dd} proposal is
 * prospective: it inserts a new frozen row carrying the valid-from date and leaves
 * earlier rows untouched.
 */
@Component
public class MasterTamperApplier implements FindingApplier<ReviewableFinding> {

    public static final String VALID_FROM_PREFIX = "VALID_FROM:";

    @Autowired
    private FrozenMasterAttributeRepository frozenMasterAttributeRepository;
    @Autowired
    private AmendmentFollowUpRepository amendmentFollowUpRepository;
    @Autowired
    private WorkflowEventRepository workflowEventRepository;

    @Override
    public void validate(ReviewableFinding finding, String proposedValue, String note) {
        if (note == null || note.isBlank()) {
            throw new FindingReviewException(400, "A note is required.");
        }
        TamperFinding f = (TamperFinding) finding;
        if (proposedValue == null || proposedValue.isBlank()) {
            return;
        }
        if (proposedValue.startsWith(VALID_FROM_PREFIX)) {
            LocalDate validFrom = parseValidFrom(proposedValue.substring(VALID_FROM_PREFIX.length()));
            LocalDate frozenPeriodEnd = f.getFrozenPeriodEnd() == null ? LocalDate.MIN : f.getFrozenPeriodEnd();
            if (validFrom == null || !validFrom.isAfter(frozenPeriodEnd)) {
                throw new FindingReviewException(400, "Valid-from date must be after " + frozenPeriodEnd
                        + ", the last finalized period that used the old value.");
            }
            return;
        }
        throw new FindingReviewException(400, "Unknown proposal for a masters tamper finding.");
    }

    @Override
    public void apply(ReviewableFinding finding, String approver) {
        TamperFinding f = (TamperFinding) finding;
        String proposal = f.getProposedValue();
        if (proposal != null && proposal.startsWith(VALID_FROM_PREFIX)) {
            applyValidFrom(f, LocalDate.parse(proposal.substring(VALID_FROM_PREFIX.length())));
        } else {
            applyCorrection(f, approver);
        }
    }

    private void applyCorrection(TamperFinding f, String approver) {
        for (WorkflowType type : affectedTypes(f)) {
            List<FrozenMasterAttribute> rows = frozenMasterAttributeRepository
                    .findByOrganizationIdAndLedgerKeyAndAttributeAndWorkflowTypeAndReleasedAtIsNull(
                            f.getOrganizationId(), ledgerKey(f), attributeName(f), type);
            for (FrozenMasterAttribute row : rows) {
                if (f.getChangeKind() == TamperChangeKind.MASTER_REMOVED) {
                    row.setReleasedAt(Instant.now());
                } else {
                    row.setValue(f.getNewValue());
                }
                frozenMasterAttributeRepository.save(row);
            }
        }
        recordFollowUps(f, approver);
    }

    private void applyValidFrom(TamperFinding f, LocalDate validFrom) {
        for (WorkflowType type : affectedTypes(f)) {
            FrozenMasterAttribute row = new FrozenMasterAttribute();
            row.setOrganizationId(f.getOrganizationId());
            row.setLedgerName(f.getLedgerName());
            row.setLedgerKey(ledgerKey(f));
            row.setAttribute(attributeName(f));
            row.setValue(f.getChangeKind() == TamperChangeKind.MASTER_REMOVED ? null : f.getNewValue());
            row.setWorkflowType(type);
            row.setValidFrom(validFrom);
            row.setFrozenAt(Instant.now());
            frozenMasterAttributeRepository.save(row);
        }
    }

    private void recordFollowUps(TamperFinding f, String approver) {
        if (f.getAffectedWorkflowIds() == null || f.getAffectedWorkflowIds().isBlank()) {
            return;
        }
        String voucherKey = "MASTER:" + ledgerKey(f) + ":" + attributeName(f);
        for (String id : f.getAffectedWorkflowIds().split(",")) {
            UUID workflowId = UUID.fromString(id.trim());
            AmendmentFollowUp followUp = new AmendmentFollowUp();
            followUp.setOrganizationId(f.getOrganizationId());
            followUp.setWorkflowId(workflowId);
            followUp.setTamperFindingId(f.getId());
            followUp.setVoucherKey(voucherKey);
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

    private LocalDate parseValidFrom(String text) {
        try {
            return LocalDate.parse(text.trim());
        } catch (DateTimeParseException e) {
            throw new FindingReviewException(400, "Valid-from date must be yyyy-MM-dd.");
        }
    }

    private List<WorkflowType> affectedTypes(TamperFinding f) {
        List<WorkflowType> types = new ArrayList<>();
        if (f.getAffectedTypes() != null && !f.getAffectedTypes().isBlank()) {
            for (String name : f.getAffectedTypes().split(",")) {
                types.add(WorkflowType.valueOf(name.trim()));
            }
        }
        if (types.isEmpty()) {
            throw new FindingReviewException(409, "The finding lists no affected workflow types.");
        }
        return types;
    }

    /** findingKey is {@code kind|ledgerKey|attribute}; masters findings carry no ledgerKey column. */
    private String ledgerKey(TamperFinding f) {
        return middle(f, 0);
    }

    private String attributeName(TamperFinding f) {
        return middle(f, 1);
    }

    private String middle(TamperFinding f, int index) {
        String[] parts = f.getFindingKey().split("\\|");
        return parts[1 + index];
    }
}
