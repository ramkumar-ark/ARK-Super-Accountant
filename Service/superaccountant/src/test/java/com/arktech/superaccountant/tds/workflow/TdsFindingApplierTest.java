package com.arktech.superaccountant.tds.workflow;

import com.arktech.superaccountant.masters.models.FindingSeverity;
import com.arktech.superaccountant.masters.models.ResolveStatus;
import com.arktech.superaccountant.review.FindingReviewException;
import com.arktech.superaccountant.tds.engine.TdsEngineFixtures;
import com.arktech.superaccountant.tds.models.TdsFinding;
import com.arktech.superaccountant.tds.models.TdsFindingType;
import com.arktech.superaccountant.tds.models.TdsPanOverride;
import com.arktech.superaccountant.tds.repository.TdsPanOverrideRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TdsFindingApplierTest {

    private TdsPanOverrideRepository repository;
    private TdsFindingApplier applier;

    @BeforeEach
    void setUp() {
        repository = mock(TdsPanOverrideRepository.class);
        applier = new TdsFindingApplier(repository, TdsEngineFixtures.ORG);
    }

    private TdsFinding finding(TdsFindingType type, String party) {
        TdsFinding f = new TdsFinding();
        f.setId(UUID.randomUUID());
        f.setFindingType(type);
        f.setSeverity(FindingSeverity.MEDIUM);
        f.setPartyLedgerName(party);
        f.setResolveStatus(ResolveStatus.OPEN);
        return f;
    }

    @Test
    void validate_panFinding_rejectsMalformedPan() {
        TdsFinding f = finding(TdsFindingType.PAN_MISSING, "ABC Ltd");

        FindingReviewException e = assertThrows(FindingReviewException.class,
                () -> applier.validate(f, "abc", "note"));

        assertEquals(400, e.getStatus());
    }

    @Test
    void validate_panFinding_rejectsMissingValue() {
        TdsFinding f = finding(TdsFindingType.PAN_INVALID, "ABC Ltd");

        FindingReviewException e = assertThrows(FindingReviewException.class,
                () -> applier.validate(f, "  ", "note"));

        assertEquals(400, e.getStatus());
    }

    @Test
    void apply_panFinding_upsertsOverrideNormalized() {
        TdsFinding f = finding(TdsFindingType.PAN_MISSING, "ABC Ltd");
        f.setProposedValue(" abcpe1234f ");
        when(repository.findByOrganizationIdAndPartyKey(TdsEngineFixtures.ORG, "abc ltd"))
                .thenReturn(Optional.empty());

        applier.apply(f, "acct");

        ArgumentCaptor<TdsPanOverride> captor = ArgumentCaptor.forClass(TdsPanOverride.class);
        verify(repository).save(captor.capture());
        TdsPanOverride saved = captor.getValue();
        assertEquals("ABCPE1234F", saved.getPan());
        assertEquals("abc ltd", saved.getPartyKey());
        assertEquals("ABC Ltd", saved.getPartyLedgerName());
        assertEquals(TdsEngineFixtures.ORG, saved.getOrganizationId());
        assertEquals(f.getId(), saved.getSourceFindingId());
        assertEquals("acct", saved.getApprovedBy());

        when(repository.findByOrganizationIdAndPartyKey(TdsEngineFixtures.ORG, "abc ltd"))
                .thenReturn(Optional.of(saved));
        applier.apply(f, "acct2");
        ArgumentCaptor<TdsPanOverride> captor2 = ArgumentCaptor.forClass(TdsPanOverride.class);
        verify(repository, org.mockito.Mockito.times(2)).save(captor2.capture());
        assertEquals(saved, captor2.getAllValues().get(1));
        assertEquals("acct2", captor2.getAllValues().get(1).getApprovedBy());
    }

    @Test
    void validate_nonPanFinding_requiresNoteAndNoValue() {
        TdsFinding f = finding(TdsFindingType.SHORT_DEDUCTED, "ABC Ltd");

        assertEquals(400, assertThrows(FindingReviewException.class,
                () -> applier.validate(f, null, null)).getStatus());
        assertEquals(400, assertThrows(FindingReviewException.class,
                () -> applier.validate(f, "x", "note")).getStatus());

        applier.validate(f, null, "explained"); // does not throw
        verify(repository, never()).save(any());
    }
}
