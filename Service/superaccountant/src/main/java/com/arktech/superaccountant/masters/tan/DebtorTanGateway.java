package com.arktech.superaccountant.masters.tan;

import com.arktech.superaccountant.masters.classifier.ParsedLedger;
import com.arktech.superaccountant.tds.models.DebtorTan;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** The single company debtor TAN store, as masters sees it. Implemented in tds. */
public interface DebtorTanGateway {
    Optional<String> findTan(UUID organizationId, String ledgerName);

    Optional<DebtorTan> findRecord(UUID organizationId, String ledgerName);

    List<DebtorTan> findAll(UUID organizationId);

    void saveTan(UUID organizationId, String ledgerName, String tan, String tallyGuid,
                 UUID sourceFindingId, String updatedBy, Instant updatedAt);

    void deleteTan(UUID organizationId, String ledgerName);

    void syncLedgerRenames(UUID organizationId, List<ParsedLedger> ledgers);
}
