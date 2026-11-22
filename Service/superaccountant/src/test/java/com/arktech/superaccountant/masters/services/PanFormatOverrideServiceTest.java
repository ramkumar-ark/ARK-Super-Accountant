package com.arktech.superaccountant.masters.services;

import com.arktech.superaccountant.masters.models.ValidationFinding;
import com.arktech.superaccountant.masters.pan.PanOverrideGateway;
import com.arktech.superaccountant.review.FindingReviewException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PanFormatOverrideServiceTest {

    private static final UUID ORG = UUID.randomUUID();
    private final PanOverrideGateway gateway = mock(PanOverrideGateway.class);
    private final PanFormatOverrideService service = new PanFormatOverrideService(gateway);

    @Test
    void rejectsOverLengthButAcceptsBlankNoPan() {
        assertThatThrownBy(() -> service.validate("AAPPK2262D1"))
                .isInstanceOf(FindingReviewException.class)
                .hasMessageContaining("format AAAAA9999A");
        // Blank is the stored "No PAN / not available" correction; the controller gates entry.
        service.validate("   ");
    }

    @Test
    void acceptsLowercaseWithWhitespace() {
        service.validate(" aappk2262d ");
    }

    @Test
    void applyStoresNormalizedPanWithFindingAndApprover() {
        ValidationFinding f = new ValidationFinding();
        UUID findingId = UUID.randomUUID();
        f.setId(findingId);
        f.setLedgerName("ANANDHA TIMBER DEPOT");
        f.setProposedValue(" aappk2262d ");

        service.apply(f, ORG, "acct");

        verify(gateway).savePan(eq(ORG), eq("ANANDHA TIMBER DEPOT"), eq("AAPPK2262D"),
                eq(findingId), eq("acct"), any(Instant.class));
    }
}
