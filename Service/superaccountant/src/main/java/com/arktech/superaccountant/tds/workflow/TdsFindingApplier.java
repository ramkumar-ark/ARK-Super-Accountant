package com.arktech.superaccountant.tds.workflow;

import com.arktech.superaccountant.review.FindingApplier;
import com.arktech.superaccountant.review.FindingReviewException;
import com.arktech.superaccountant.review.ReviewableFinding;
import com.arktech.superaccountant.tds.engine.TdsEngine;
import com.arktech.superaccountant.tds.models.TdsFinding;
import com.arktech.superaccountant.tds.models.TdsFindingType;
import com.arktech.superaccountant.tds.models.TdsPanOverride;
import com.arktech.superaccountant.tds.repository.TdsPanOverrideRepository;

import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Applies accountant PAN proposals as company-level PAN overrides. */
public class TdsFindingApplier implements FindingApplier<ReviewableFinding> {

    private static final Set<TdsFindingType> PAN_TYPES =
            Set.of(TdsFindingType.PAN_MISSING, TdsFindingType.PAN_INVALID);

    private final TdsPanOverrideRepository panOverrideRepository;
    private final UUID organizationId;

    public TdsFindingApplier(TdsPanOverrideRepository panOverrideRepository, UUID organizationId) {
        this.panOverrideRepository = panOverrideRepository;
        this.organizationId = organizationId;
    }

    @Override
    public void validate(ReviewableFinding finding, String proposedValue, String note) {
        TdsFindingType type = ((TdsFinding) finding).getFindingType();
        if (PAN_TYPES.contains(type)) {
            String pan = normalized(proposedValue);
            if (pan == null || !pan.matches(TdsEngine.PAN_PATTERN)) {
                throw new FindingReviewException(400, "Enter a valid PAN (format AAAAA9999A).");
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
        if (!PAN_TYPES.contains(f.getFindingType())) {
            return;
        }
        String partyKey = TdsEngine.key(f.getPartyLedgerName());
        TdsPanOverride override = panOverrideRepository
                .findByOrganizationIdAndPartyKey(organizationId, partyKey)
                .orElseGet(TdsPanOverride::new);
        override.setOrganizationId(organizationId);
        override.setPartyLedgerName(f.getPartyLedgerName());
        override.setPartyKey(partyKey);
        override.setPan(normalized(f.getProposedValue()));
        override.setSourceFindingId(f.getId());
        override.setApprovedBy(approver);
        override.setApprovedAt(Instant.now());
        panOverrideRepository.save(override);
    }

    private static String normalized(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }
}
