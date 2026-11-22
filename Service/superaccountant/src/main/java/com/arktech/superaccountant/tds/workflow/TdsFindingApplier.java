package com.arktech.superaccountant.tds.workflow;

import com.arktech.superaccountant.masters.pan.PanFormat;
import com.arktech.superaccountant.masters.pan.PanOverrideGateway;
import com.arktech.superaccountant.masters.tan.DebtorTanGateway;
import com.arktech.superaccountant.review.FindingApplier;
import com.arktech.superaccountant.review.FindingReviewException;
import com.arktech.superaccountant.review.ReviewableFinding;
import com.arktech.superaccountant.tds.models.TdsFinding;
import com.arktech.superaccountant.tds.models.TdsFindingType;
import com.arktech.superaccountant.tds.tan.TanFormat;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** Applies accountant PAN proposals as company-level PAN overrides. */
public class TdsFindingApplier implements FindingApplier<ReviewableFinding> {

    private static final Set<TdsFindingType> PAN_TYPES =
            Set.of(TdsFindingType.PAN_MISSING, TdsFindingType.PAN_INVALID);

    private static final Set<TdsFindingType> DEBTOR_TAN_TYPES =
            Set.of(TdsFindingType.DEBTOR_TDS_RECEIVABLE_MISSING_TAN);

    private final PanOverrideGateway panOverrides;
    private final DebtorTanGateway debtorTans;
    private final UUID organizationId;

    public TdsFindingApplier(PanOverrideGateway panOverrides, DebtorTanGateway debtorTans,
                             UUID organizationId) {
        this.panOverrides = panOverrides;
        this.debtorTans = debtorTans;
        this.organizationId = organizationId;
    }

    @Override
    public void validate(ReviewableFinding finding, String proposedValue, String note) {
        TdsFindingType type = ((TdsFinding) finding).getFindingType();
        if (PAN_TYPES.contains(type)) {
            String pan = PanFormat.normalize(proposedValue);
            if (pan == null || !pan.matches(PanFormat.PATTERN)) {
                throw new FindingReviewException(400, PanFormat.ERROR_MESSAGE);
            }
            return;
        }
        if (DEBTOR_TAN_TYPES.contains(type)) {
            if (!TanFormat.isValid(proposedValue)) {
                throw new FindingReviewException(400, TanFormat.ERROR_MESSAGE);
            }
            return;
        }
        if (note == null || note.isBlank()) {
            throw new FindingReviewException(400, "A note is required.");
        }
        if (proposedValue != null && !proposedValue.isBlank()) {
            throw new FindingReviewException(400, "This finding does not take a value.");
        }
    }

    @Override
    public void apply(ReviewableFinding finding, String approver) {
        TdsFinding f = (TdsFinding) finding;
        if (DEBTOR_TAN_TYPES.contains(f.getFindingType())) {
            debtorTans.saveTan(organizationId, f.getPartyLedgerName(),
                    TanFormat.normalize(f.getProposedValue()), null, f.getId(), approver,
                    Instant.now());
            return;
        }
        if (!PAN_TYPES.contains(f.getFindingType())) {
            return;
        }
        panOverrides.savePan(organizationId, f.getPartyLedgerName(),
                PanFormat.normalize(f.getProposedValue()), f.getId(), approver, Instant.now());
    }
}
