package com.arktech.superaccountant.tds.mapping;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TdsLedgerMappingRepository extends JpaRepository<TdsLedgerMapping, UUID> {

    Optional<TdsLedgerMapping> findByOrganizationIdAndLedgerKey(UUID organizationId, String ledgerKey);

    List<TdsLedgerMapping> findByOrganizationId(UUID organizationId);
}
