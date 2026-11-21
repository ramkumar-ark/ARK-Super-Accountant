package com.arktech.superaccountant.tds.services;

import com.arktech.superaccountant.masters.pan.PanChangeRecorder;
import com.arktech.superaccountant.masters.pan.PanFormat;
import com.arktech.superaccountant.masters.pan.PanOverrideGateway;
import com.arktech.superaccountant.masters.repository.OrganizationRepository;
import com.arktech.superaccountant.tds.engine.TdsEngine;
import com.arktech.superaccountant.tds.models.TdsPanOverride;
import com.arktech.superaccountant.tds.repository.TdsPanOverrideRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class TdsPanOverrideStore implements PanOverrideGateway {

    private final TdsPanOverrideRepository repository;
    private final OrganizationRepository organizationRepository;
    private final PanChangeRecorder panChangeRecorder;

    public TdsPanOverrideStore(TdsPanOverrideRepository repository,
                               OrganizationRepository organizationRepository,
                               PanChangeRecorder panChangeRecorder) {
        this.repository = repository;
        this.organizationRepository = organizationRepository;
        this.panChangeRecorder = panChangeRecorder;
    }

    @Override
    public Optional<String> findPan(UUID organizationId, String ledgerName) {
        return repository.findByOrganizationIdAndPartyKey(organizationId, TdsEngine.key(ledgerName))
                .map(TdsPanOverride::getPan);
    }

    @Override
    @Transactional
    public void savePan(UUID organizationId, String ledgerName, String pan, UUID sourceFindingId,
                        String approvedBy, Instant approvedAt) {
        // One stable row per org: serialize concurrent approvals before find-or-create and the change record.
        organizationRepository.findByIdForUpdate(organizationId)
                .orElseThrow(() -> new IllegalStateException("Organization not found: " + organizationId));
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
