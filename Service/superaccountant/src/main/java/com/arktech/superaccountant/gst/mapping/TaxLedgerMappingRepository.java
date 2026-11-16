package com.arktech.superaccountant.gst.mapping;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaxLedgerMappingRepository extends JpaRepository<TaxLedgerMapping, UUID> {

    Optional<TaxLedgerMapping> findByOrganizationIdAndLedgerKey(UUID organizationId, String ledgerKey);

    List<TaxLedgerMapping> findByOrganizationId(UUID organizationId);
}
