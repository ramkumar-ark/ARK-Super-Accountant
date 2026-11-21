package com.arktech.superaccountant.masters.services;

import com.arktech.superaccountant.masters.models.ValidationFinding;
import com.arktech.superaccountant.masters.pan.PanFormat;
import com.arktech.superaccountant.masters.pan.PanOverrideGateway;
import com.arktech.superaccountant.review.FindingReviewException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class PanFormatOverrideService {

    private final PanOverrideGateway panOverrides;

    public PanFormatOverrideService(PanOverrideGateway panOverrides) {
        this.panOverrides = panOverrides;
    }

    public void validate(String value) {
        if (!PanFormat.isValid(PanFormat.normalize(value))) {
            throw new FindingReviewException(400, PanFormat.ERROR_MESSAGE);
        }
    }

    public void apply(ValidationFinding finding, UUID organizationId, String approver) {
        validate(finding.getProposedValue());
        panOverrides.savePan(organizationId, finding.getLedgerName(),
                PanFormat.normalize(finding.getProposedValue()), finding.getId(), approver, Instant.now());
    }
}
