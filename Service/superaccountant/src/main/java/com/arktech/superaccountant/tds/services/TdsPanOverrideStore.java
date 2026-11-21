package com.arktech.superaccountant.tds.services;

import com.arktech.superaccountant.masters.pan.PanChangeRecorder;
import com.arktech.superaccountant.masters.pan.PanFormat;
import com.arktech.superaccountant.masters.pan.PanOverrideGateway;
import com.arktech.superaccountant.tds.engine.TdsEngine;
import com.arktech.superaccountant.tds.models.TdsPanOverride;
import com.arktech.superaccountant.tds.repository.TdsPanOverrideRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class TdsPanOverrideStore implements PanOverrideGateway {

    private final TdsPanOverrideRepository repository;
    private final PanChangeRecorder panChangeRecorder;

    public TdsPanOverrideStore(TdsPanOverrideRepository repository, PanChangeRecorder panChangeRecorder) {
        this.repository = repository;
        this.panChangeRecorder = panChangeRecorder;
    }

    @Override
    public Optional<String> findPan(UUID organizationId, String ledgerName) {
        return repository.findByOrganizationIdAndPartyKey(organizationId, TdsEngine.key(ledgerName))
                .map(TdsPanOverride::getPan);
    }

    @Override
    public void savePan(UUID organizationId, String ledgerName, String pan, UUID sourceFindingId,
                        String approvedBy, Instant approvedAt) {
        String partyKey = TdsEngine.key(ledgerName);
        TdsPanOverride override = repository
                .findByOrganizationIdAndPartyKey(organizationId, partyKey)
                .orElseGet(TdsPanOverride::new);
        override.setOrganizationId(organizationId);
        override.setPartyLedgerName(ledgerName);
        override.setPartyKey(partyKey);
        override.setPan(PanFormat.normalize(pan));
        override.setSourceFindingId(sourceFindingId);
        override.setApprovedBy(approvedBy);
        override.setApprovedAt(approvedAt);
        repository.save(override);
        panChangeRecorder.onPanSaved(organizationId, override.getPartyLedgerName(), override.getPan(),
                override.getId(), sourceFindingId, approvedBy, approvedAt);
    }
}
