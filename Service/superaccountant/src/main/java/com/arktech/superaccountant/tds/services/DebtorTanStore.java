package com.arktech.superaccountant.tds.services;

import com.arktech.superaccountant.masters.classifier.ParsedLedger;
import com.arktech.superaccountant.masters.tan.DebtorTanGateway;
import com.arktech.superaccountant.tds.engine.TdsEngine;
import com.arktech.superaccountant.tds.models.DebtorTan;
import com.arktech.superaccountant.tds.repository.DebtorTanRepository;
import com.arktech.superaccountant.tds.tan.TanFormat;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class DebtorTanStore implements DebtorTanGateway {

    private final DebtorTanRepository repository;

    public DebtorTanStore(DebtorTanRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<String> findTan(UUID organizationId, String ledgerName) {
        return findRecord(organizationId, ledgerName).map(DebtorTan::getTan);
    }

    @Override
    public Optional<DebtorTan> findRecord(UUID organizationId, String ledgerName) {
        return repository.findByOrganizationIdAndLedgerKey(organizationId, TdsEngine.key(ledgerName));
    }

    @Override
    public List<DebtorTan> findAll(UUID organizationId) {
        return repository.findByOrganizationIdOrderByLedgerNameAsc(organizationId);
    }

    @Override
    public void saveTan(UUID organizationId, String ledgerName, String tan, String tallyGuid,
                        UUID sourceFindingId, String updatedBy, Instant updatedAt) {
        if (!TanFormat.isValid(tan)) {
            throw new IllegalArgumentException(TanFormat.ERROR_MESSAGE);
        }
        String ledgerKey = TdsEngine.key(ledgerName);
        DebtorTan record = repository
                .findByOrganizationIdAndLedgerKey(organizationId, ledgerKey)
                .orElseGet(DebtorTan::new);
        record.setOrganizationId(organizationId);
        record.setLedgerName(ledgerName == null ? null : ledgerName.trim());
        record.setLedgerKey(ledgerKey);
        if (tallyGuid != null) {
            record.setTallyGuid(tallyGuid);
        }
        record.setTan(TanFormat.normalize(tan));
        record.setSourceFindingId(sourceFindingId);
        record.setUpdatedBy(updatedBy);
        record.setUpdatedAt(updatedAt);
        repository.save(record);
    }

    @Override
    public void deleteTan(UUID organizationId, String ledgerName) {
        findRecord(organizationId, ledgerName).ifPresent(repository::delete);
    }

    @Override
    public void syncLedgerRenames(UUID organizationId, List<ParsedLedger> ledgers) {
        if (ledgers == null) {
            return;
        }
        for (ParsedLedger ledger : ledgers) {
            if (!isDebtor(ledger)) {
                continue;
            }
            String ledgerName = ledger.getName();
            String ledgerKey = TdsEngine.key(ledgerName);
            String guid = ledger.getGuid();
            Optional<DebtorTan> byGuid = guid == null
                    ? Optional.empty()
                    : repository.findByOrganizationIdAndTallyGuid(organizationId, guid).stream().findFirst();
            if (byGuid.isPresent()) {
                DebtorTan record = byGuid.get();
                boolean keyTaken = !ledgerKey.equals(record.getLedgerKey())
                        && repository.findByOrganizationIdAndLedgerKey(organizationId, ledgerKey).isPresent();
                if (!keyTaken && (!ledgerKey.equals(record.getLedgerKey()) || !ledgerName.equals(record.getLedgerName()))) {
                    record.setLedgerName(ledgerName);
                    record.setLedgerKey(ledgerKey);
                    repository.save(record);
                }
                continue;
            }
            repository.findByOrganizationIdAndLedgerKey(organizationId, ledgerKey).ifPresent(record -> {
                if (record.getTallyGuid() == null && guid != null) {
                    record.setTallyGuid(guid);
                    repository.save(record);
                }
            });
        }
    }

    private static boolean isDebtor(ParsedLedger ledger) {
        String chain = ledger.getGroupChain();
        return chain != null && chain.toLowerCase(java.util.Locale.ROOT).contains("sundry debtors");
    }
}
